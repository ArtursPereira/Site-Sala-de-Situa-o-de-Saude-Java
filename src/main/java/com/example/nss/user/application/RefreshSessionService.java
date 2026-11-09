package com.example.nss.user.application;

import com.example.nss.user.domain.RefreshSession;
import com.example.nss.user.domain.User;
import com.example.nss.user.exception.AuthStoreUnavailableException;
import com.example.nss.user.infrastructure.repository.RefreshSessionRepository;
import com.example.nss.user.infrastructure.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class RefreshSessionService {
  private static final String PREFIX = "nss:auth:refresh:";
  private static final DefaultRedisScript<Long> ISSUE_SCRIPT = script("refresh-issue.lua");
  private static final DefaultRedisScript<Long> ROTATE = script("refresh-rotate.lua");
  private static final DefaultRedisScript<Long> REVOKE_SCRIPT = script("refresh-revoke.lua");
  private static final DefaultRedisScript<Long> REVOKE_ALL = script("refresh-revoke-all.lua");
  private static final String INVALID_REFRESH = "Refresh inválido";
  private final StringRedisTemplate redis;
  private final UserRepository users;
  private final RefreshSessionRepository legacySessions;
  private final SecureRandom random = new SecureRandom();

  @Value("${api.auth.refresh-expiration:604800000}")
  private long expirationMillis;

  @Autowired
  public RefreshSessionService(StringRedisTemplate redis, UserRepository users) {
    this.redis = redis;
    this.users = users;
    this.legacySessions = null;
  }

  /** Constructor kept only for isolated unit tests that exercise the old seam. */
  public RefreshSessionService(RefreshSessionRepository legacySessions, UserRepository users) {
    this.redis = null;
    this.users = users;
    this.legacySessions = legacySessions;
  }

  public String issue(User user) {
    String generation =
        legacySessions == null
            ? redisOperation(() -> redis.opsForValue().get(generationKey(user.getId())))
            : null;
    User managedUser =
        users
            .findById(user.getId())
            .orElseThrow(() -> new BadCredentialsException("Conta não encontrada"));
    if (!managedUser.isAtivo()) throw new BadCredentialsException("Conta desativada");
    if (legacySessions == null
        && (!java.util.Objects.equals(user.getPassword(), managedUser.getPassword())
            || !java.util.Objects.equals(user.getAccountVersion(), managedUser.getAccountVersion())
            || managedUser.getPassword() == null)) {
      throw new BadCredentialsException(INVALID_REFRESH);
    }
    String raw = randomToken();
    RefreshSession session = newSession(managedUser, raw);
    if (legacySessions != null) {
      legacySessions.save(session);
    } else {
      Long issued =
          redisOperation(
              () ->
                  redis.execute(
                      ISSUE_SCRIPT,
                      List.of(key(session.getTokenHash()), generationKey(managedUser.getId())),
                      session.getId().toString(),
                      managedUser.getId().toString(),
                      session.getCreatedAt().toString(),
                      session.getExpiresAt().toString(),
                      Long.toString(expirationMillis),
                      generation == null ? "0" : generation,
                      session.getTokenHash(),
                      hash(managedUser.getPassword() + ":" + managedUser.getAccountVersion())));
      if (!Long.valueOf(1).equals(issued)) throw new BadCredentialsException(INVALID_REFRESH);
    }
    return raw;
  }

  public Rotation rotateWithToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) throw new BadCredentialsException(INVALID_REFRESH);
    String hash = hash(rawToken);
    if (legacySessions != null) return rotateLegacy(hash);
    RefreshSession current = redisOperation(() -> readRedis(hash));
    if (current == null
        || current.getRevokedAt() != null
        || !current.getExpiresAt().isAfter(Instant.now())) {
      throw new BadCredentialsException(INVALID_REFRESH);
    }
    User managedUser =
        users
            .findById(current.getUser().getId())
            .orElseThrow(() -> new BadCredentialsException(INVALID_REFRESH));
    if (!managedUser.isAtivo()) throw new BadCredentialsException(INVALID_REFRESH);
    String credential =
        redisOperation(() -> (String) redis.opsForHash().get(key(hash), "credential"));
    if (credential == null
        || managedUser.getPassword() == null
        || !credential.equals(
            hash(managedUser.getPassword() + ":" + managedUser.getAccountVersion()))) {
      throw new BadCredentialsException(INVALID_REFRESH);
    }
    String replacementRaw = randomToken();
    RefreshSession replacement = newSession(managedUser, replacementRaw);
    Long rotated =
        redisOperation(
            () ->
                redis.execute(
                    ROTATE,
                    List.of(
                        key(hash),
                        key(replacement.getTokenHash()),
                        generationKey(managedUser.getId())),
                    replacement.getId().toString(),
                    managedUser.getId().toString(),
                    replacement.getCreatedAt().toString(),
                    replacement.getExpiresAt().toString(),
                    Long.toString(expirationMillis),
                    current.getExpiresAt().toString(),
                    PREFIX + "family:",
                    credential));
    if (!Long.valueOf(1).equals(rotated)) throw new BadCredentialsException(INVALID_REFRESH);
    return new Rotation(managedUser, replacementRaw);
  }

  public void revoke(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) return;
    String hash = hash(rawToken);
    if (legacySessions != null) {
      legacySessions
          .findByTokenHashForUpdate(hash)
          .ifPresent(
              session -> {
                if (session.getRevokedAt() == null) {
                  session.setRevokedAt(Instant.now());
                  legacySessions.save(session);
                }
              });
      return;
    }
    redisOperation(
        () ->
            redis.execute(
                REVOKE_SCRIPT,
                List.of(key(hash)),
                Instant.now().toString(),
                PREFIX + "family:",
                Long.toString(expirationMillis)));
  }

  public void revokeAllForUser(Long userId) {
    if (legacySessions != null) {
      legacySessions.revokeActiveByUserId(userId, Instant.now());
      return;
    }
    redisOperation(
        () ->
            redis.execute(
                REVOKE_ALL, List.of(generationKey(userId)), Long.toString(expirationMillis)));
  }

  private static String generationKey(Long userId) {
    return PREFIX + "generation:" + userId;
  }

  private static <T> T redisOperation(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (DataAccessException exception) {
      throw new AuthStoreUnavailableException(exception);
    }
  }

  private static DefaultRedisScript<Long> script(String name) {
    var script = new DefaultRedisScript<Long>();
    script.setLocation(new ClassPathResource("redis/" + name));
    script.setResultType(Long.class);
    return script;
  }

  private Rotation rotateLegacy(String hash) {
    RefreshSession current =
        legacySessions
            .findByTokenHashForUpdate(hash)
            .orElseThrow(() -> new BadCredentialsException(INVALID_REFRESH));
    Instant now = Instant.now();
    if (current.getRevokedAt() != null
        || !current.getExpiresAt().isAfter(now)
        || !current.getUser().isAtivo()) {
      if (current.getRevokedAt() == null) {
        current.setRevokedAt(now);
        legacySessions.save(current);
      }
      throw new BadCredentialsException(INVALID_REFRESH);
    }
    String replacementRaw = randomToken();
    RefreshSession replacement = newSession(current.getUser(), replacementRaw);
    current.setRevokedAt(now);
    current.setReplacedBySessionId(replacement.getId());
    legacySessions.save(replacement);
    legacySessions.save(current);
    return new Rotation(current.getUser(), replacementRaw);
  }

  private RefreshSession readRedis(String hash) {
    Map<Object, Object> values = redis.opsForHash().entries(key(hash));
    if (values.isEmpty()) return null;
    RefreshSession session = new RefreshSession();
    session.setId(UUID.fromString((String) values.get("id")));
    User user = new User();
    user.setId(Long.valueOf((String) values.get("userId")));
    session.setUser(user);
    session.setTokenHash(hash);
    session.setCreatedAt(Instant.parse((String) values.get("createdAt")));
    session.setExpiresAt(Instant.parse((String) values.get("expiresAt")));
    String revoked = (String) values.get("revokedAt");
    if (revoked != null && !revoked.isBlank()) session.setRevokedAt(Instant.parse(revoked));
    String replaced = (String) values.get("replacedBy");
    if (replaced != null && !replaced.isBlank())
      session.setReplacedBySessionId(UUID.fromString(replaced));
    return session;
  }

  private String key(String value) {
    return PREFIX + value;
  }

  private RefreshSession newSession(User user, String rawToken) {
    RefreshSession s = new RefreshSession();
    s.setId(UUID.randomUUID());
    s.setUser(user);
    s.setTokenHash(hash(rawToken));
    Instant now = Instant.now();
    s.setCreatedAt(now);
    s.setExpiresAt(now.plusMillis(expirationMillis));
    return s;
  }

  private String randomToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Não foi possível proteger o refresh token", exception);
    }
  }

  public record Rotation(User user, String rawToken) {}
}
