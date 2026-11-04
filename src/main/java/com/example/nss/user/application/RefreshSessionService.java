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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshSessionService {
  private final RefreshSessionRepository sessions;
  private final UserRepository users;
  private final SecureRandom random = new SecureRandom();

  @Value("${api.auth.refresh-expiration:604800000}")
  private long expirationMillis;

  @Transactional
  public String issue(User user) {
    User managedUser =
        users
            .findById(user.getId())
            .orElseThrow(() -> new BadCredentialsException("Conta não encontrada"));
    if (!managedUser.isAtivo()) {
      throw new BadCredentialsException("Conta desativada");
    }
    String raw = randomToken();
    sessions.save(newSession(managedUser, raw));
    return raw;
  }

  @Transactional(noRollbackFor = BadCredentialsException.class)
  public Rotation rotateWithToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new BadCredentialsException("Refresh inválido");
    }
    RefreshSession current =
        sessions
            .findByTokenHashForUpdate(hash(rawToken))
            .orElseThrow(() -> new BadCredentialsException("Refresh inválido"));
    Instant now = Instant.now();
    if (current.getRevokedAt() != null
        || !current.getExpiresAt().isAfter(now)
        || !current.getUser().isAtivo()) {
      if (current.getRevokedAt() == null) {
        current.setRevokedAt(now);
        sessions.save(current);
      }
      throw new BadCredentialsException("Refresh inválido");
    }
    String replacementRaw = randomToken();
    RefreshSession replacement = newSession(current.getUser(), replacementRaw);
    sessions.save(replacement);
    current.setRevokedAt(now);
    current.setReplacedBySessionId(replacement.getId());
    sessions.save(current);
    return new Rotation(current.getUser(), replacementRaw);
  }

  @Transactional
  public void revoke(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }
    sessions
        .findByTokenHashForUpdate(hash(rawToken))
        .ifPresent(
            session -> {
              if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                sessions.save(session);
              }
            });
  }

  @Transactional
  public void revokeAllForUser(Long userId) {
    sessions.revokeActiveByUserId(userId, Instant.now());
  }

  private RefreshSession newSession(User user, String rawToken) {
    RefreshSession session = new RefreshSession();
    session.setId(UUID.randomUUID());
    session.setUser(user);
    session.setTokenHash(hash(rawToken));
    session.setCreatedAt(Instant.now());
    session.setExpiresAt(Instant.now().plus(Duration.ofMillis(expirationMillis)));
    return session;
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
