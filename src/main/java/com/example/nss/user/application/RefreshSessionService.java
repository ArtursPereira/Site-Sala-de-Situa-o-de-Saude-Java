package com.example.nss.user.application;

import com.example.nss.user.domain.RefreshSession;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.RefreshSessionRepository;
import com.example.nss.user.infrastructure.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class RefreshSessionService {
  private static final String PREFIX = "nss:auth:refresh:";
  private static final String USER_PREFIX = PREFIX + "user:";
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
    User managedUser =
        users
            .findById(user.getId())
            .orElseThrow(() -> new BadCredentialsException("Conta não encontrada"));
    if (!managedUser.isAtivo()) throw new BadCredentialsException("Conta desativada");
    String raw = randomToken();
    RefreshSession session = newSession(managedUser, raw);
    if (legacySessions != null) {
      legacySessions.save(session);
    } else {
      saveRedis(session);
    }
    return raw;
  }

  public Rotation rotateWithToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) throw new BadCredentialsException(INVALID_REFRESH);
    String hash = hash(rawToken);
    if (legacySessions != null) return rotateLegacy(hash);
    String lockKey = key("lock:" + hash);
    String lockValue = UUID.randomUUID().toString();
    Boolean locked = redis.opsForValue().setIfAbsent(lockKey, lockValue, 5, TimeUnit.SECONDS);
    if (!Boolean.TRUE.equals(locked)) throw new BadCredentialsException("Refresh em processamento");
    try {
      RefreshSession current = readRedis(hash);
      if (current == null) throw new BadCredentialsException(INVALID_REFRESH);
      User managedUser =
          users
              .findById(current.getUser().getId())
              .orElseThrow(() -> new BadCredentialsException("Conta não encontrada"));
      current.setUser(managedUser);
      Instant now = Instant.now();
      if (current.getRevokedAt() != null
          || !current.getExpiresAt().isAfter(now)
          || !managedUser.isAtivo()) {
        if (current.getRevokedAt() == null) {
          current.setRevokedAt(now);
          saveRedis(current);
        }
        throw new BadCredentialsException(INVALID_REFRESH);
      }
      String replacementRaw = randomToken();
      RefreshSession replacement = newSession(managedUser, replacementRaw);
      current.setRevokedAt(now);
      current.setReplacedBySessionId(replacement.getId());
      saveRedis(current);
      saveRedis(replacement);
      return new Rotation(managedUser, replacementRaw);
    } finally {
      redis.delete(lockKey);
    }
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
    RefreshSession session = readRedis(hash);
    if (session != null && session.getRevokedAt() == null) {
      session.setRevokedAt(Instant.now());
      saveRedis(session);
    }
  }

  public void revokeAllForUser(Long userId) {
    if (legacySessions != null) {
      legacySessions.revokeActiveByUserId(userId, Instant.now());
      return;
    }
    Set<String> hashes = redis.opsForSet().members(USER_PREFIX + userId);
    if (hashes == null) return;
    hashes.forEach(
        hash -> {
          RefreshSession session = readRedis(hash);
          if (session != null && session.getRevokedAt() == null) {
            session.setRevokedAt(Instant.now());
            saveRedis(session);
          }
        });
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

  private void saveRedis(RefreshSession session) {
    String hash = session.getTokenHash();
    Map<String, String> values =
        Map.of(
            "id", session.getId().toString(),
            "userId", session.getUser().getId().toString(),
            "createdAt", session.getCreatedAt().toString(),
            "expiresAt", session.getExpiresAt().toString(),
            "revokedAt", session.getRevokedAt() == null ? "" : session.getRevokedAt().toString(),
            "replacedBy",
                session.getReplacedBySessionId() == null
                    ? ""
                    : session.getReplacedBySessionId().toString());
    redis.opsForHash().putAll(key(hash), values);
    redis.expire(
        key(hash),
        Duration.ofMillis(
            Math.max(1, session.getExpiresAt().toEpochMilli() - System.currentTimeMillis())));
    redis.opsForSet().add(USER_PREFIX + session.getUser().getId(), hash);
    redis.expire(USER_PREFIX + session.getUser().getId(), Duration.ofMillis(expirationMillis));
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
    s.setCreatedAt(Instant.now());
    s.setExpiresAt(Instant.now().plus(Duration.ofMillis(expirationMillis)));
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
