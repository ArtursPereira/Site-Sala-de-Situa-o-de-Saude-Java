package com.example.nss.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.nss.user.domain.RefreshSession;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.RefreshSessionRepository;
import com.example.nss.user.infrastructure.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;

class RefreshSessionServiceTest {
  @Test
  void issuesARefreshTokenForAnActiveManagedUser() {
    RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    UserRepository users = mock(UserRepository.class);
    RefreshSessionService service = new RefreshSessionService(sessions, users);
    setExpiration(service, 60_000L);
    User user = new User();
    user.setId(7L);
    user.setAtivo(true);
    when(users.findById(7L)).thenReturn(java.util.Optional.of(user));

    String token = service.issue(user);

    assertNotNull(token);
    ArgumentCaptor<RefreshSession> captor = ArgumentCaptor.forClass(RefreshSession.class);
    verify(sessions).save(captor.capture());
    RefreshSession created = captor.getValue();
    assertEquals(user, created.getUser());
    assertNotNull(created.getId());
    assertNotNull(created.getCreatedAt());
    assertNotNull(created.getExpiresAt());
    assertNotNull(created.getTokenHash());
    assertEquals(false, created.getTokenHash().equals(token));
    assertFalse(token.isBlank());
  }

  @Test
  void refusesToIssueRefreshTokenForAnInactiveManagedUser() {
    RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    UserRepository users = mock(UserRepository.class);
    RefreshSessionService service = new RefreshSessionService(sessions, users);
    User user = new User();
    user.setId(8L);
    user.setAtivo(false);
    when(users.findById(8L)).thenReturn(Optional.of(user));

    assertThrows(BadCredentialsException.class, () -> service.issue(user));
    verify(sessions, org.mockito.Mockito.never()).save(any(RefreshSession.class));
  }

  @Test
  void refusesToIssueRefreshTokenWhenTheManagedUserDoesNotExist() {
    RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    UserRepository users = mock(UserRepository.class);
    RefreshSessionService service = new RefreshSessionService(sessions, users);
    User user = new User();
    user.setId(404L);
    when(users.findById(404L)).thenReturn(Optional.empty());

    assertThrows(BadCredentialsException.class, () -> service.issue(user));
    verify(sessions, org.mockito.Mockito.never()).save(any(RefreshSession.class));
  }

  @Test
  void rotatesAnActiveRefreshTokenAndRevokesThePreviousSession() throws Exception {
    RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    UserRepository users = mock(UserRepository.class);
    RefreshSessionService service = new RefreshSessionService(sessions, users);
    setExpiration(service, 60_000L);
    User user = new User();
    user.setAtivo(true);
    RefreshSession current = new RefreshSession();
    current.setId(UUID.randomUUID());
    current.setUser(user);
    current.setExpiresAt(Instant.now().plusSeconds(60));
    when(sessions.findByTokenHashForUpdate(any())).thenReturn(java.util.Optional.of(current));

    RefreshSessionService.Rotation rotation = service.rotateWithToken("opaque-refresh");

    assertEquals(user, rotation.user());
    assertNotNull(rotation.rawToken());
    assertNotNull(current.getRevokedAt());
    assertNotNull(current.getReplacedBySessionId());
    verify(sessions, times(2)).save(any(RefreshSession.class));
    ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
    verify(sessions).findByTokenHashForUpdate(hashCaptor.capture());
    assertEquals(
        java.util.HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest("opaque-refresh".getBytes(StandardCharsets.UTF_8))),
        hashCaptor.getValue());
  }

  @Test
  void rejectsBlankOrUnknownRefreshTokens() {
    RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    UserRepository users = mock(UserRepository.class);
    RefreshSessionService service = new RefreshSessionService(sessions, users);
    when(sessions.findByTokenHashForUpdate(any())).thenReturn(java.util.Optional.empty());

    assertThrows(BadCredentialsException.class, () -> service.rotateWithToken(" "));
    assertThrows(BadCredentialsException.class, () -> service.rotateWithToken("unknown"));
  }

  @Test
  void rejectsRefreshForAnInactiveUserAndRevokesTheStoredSession() {
    RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    UserRepository users = mock(UserRepository.class);
    RefreshSessionService service = new RefreshSessionService(sessions, users);
    setExpiration(service, 60_000L);
    User user = new User();
    user.setAtivo(false);
    RefreshSession session = new RefreshSession();
    session.setUser(user);
    session.setExpiresAt(Instant.now().plusSeconds(60));
    when(sessions.findByTokenHashForUpdate(any())).thenReturn(Optional.of(session));

    assertThrows(BadCredentialsException.class, () -> service.rotateWithToken("opaque-refresh"));
    assertNotNull(session.getRevokedAt());
    verify(sessions).save(session);
  }

  @Test
  void revokesOnlyAnActiveStoredSessionAndIgnoresBlankTokens() {
    RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    UserRepository users = mock(UserRepository.class);
    RefreshSessionService service = new RefreshSessionService(sessions, users);
    RefreshSession session = new RefreshSession();
    when(sessions.findByTokenHashForUpdate(any())).thenReturn(Optional.of(session));

    service.revoke("opaque-refresh");
    service.revoke(null);
    service.revoke(" ");

    assertNotNull(session.getRevokedAt());
    verify(sessions).save(session);
  }

  @Test
  void doesNotSaveARefreshSessionThatWasAlreadyRevoked() {
    RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    UserRepository users = mock(UserRepository.class);
    RefreshSessionService service = new RefreshSessionService(sessions, users);
    RefreshSession session = new RefreshSession();
    session.setRevokedAt(Instant.now());
    when(sessions.findByTokenHashForUpdate(any())).thenReturn(Optional.of(session));

    service.revoke("opaque-refresh");

    verify(sessions, org.mockito.Mockito.never()).save(any(RefreshSession.class));
  }

  @Test
  void revokesAllSessionsForTheRequestedUser() {
    RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    UserRepository users = mock(UserRepository.class);
    RefreshSessionService service = new RefreshSessionService(sessions, users);

    service.revokeAllForUser(42L);

    verify(sessions).revokeActiveByUserId(org.mockito.ArgumentMatchers.eq(42L), any());
  }

  private void setExpiration(RefreshSessionService service, long value) {
    try {
      var field = RefreshSessionService.class.getDeclaredField("expirationMillis");
      field.setAccessible(true);
      field.set(service, value);
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError(exception);
    }
  }
}
