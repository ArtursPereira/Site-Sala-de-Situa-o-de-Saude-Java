package com.example.nss.user.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.nss.shared.redis.DedicatedRedis;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshSessionRedisIT {
  @Test
  void logoutInvalidatesRotatedSessionAndPasswordChangeInvalidatesSession() throws Exception {
    String url = System.getenv().getOrDefault("SPRING_DATA_REDIS_URL", "redis://localhost:6379");
    try (var connection = new DedicatedRedis(url, Duration.ofSeconds(2))) {
      var users = mock(UserRepository.class);
      var encoder = new BCryptPasswordEncoder();
      var user = new User();
      user.setId(983412L);
      user.setPassword(encoder.encode("old-password"));
      when(users.findById(user.getId())).thenReturn(Optional.of(user));
      var service = new RefreshSessionService(connection.template(), users);
      ReflectionTestUtils.setField(service, "expirationMillis", 120_000L);
      String old = service.issue(user);
      String next = service.rotateWithToken(old).rawToken();
      service.revoke(old);
      assertThrows(BadCredentialsException.class, () -> service.rotateWithToken(next));
      String current = service.issue(user);
      user.setPassword(encoder.encode("new-password"));
      assertThrows(BadCredentialsException.class, () -> service.rotateWithToken(current));
      String token = service.issue(user);
      try (var executor = Executors.newFixedThreadPool(2)) {
        var first = executor.submit(() -> rotate(service, token));
        var second = executor.submit(() -> rotate(service, token));
        assertNotEquals(first.get(), second.get());
      }
    }
  }

  @Test
  void rejectsChangedAccountVersionWithoutChangingPassword() {
    String url = System.getenv().getOrDefault("SPRING_DATA_REDIS_URL", "redis://localhost:6379");
    try (var connection = new DedicatedRedis(url, Duration.ofSeconds(2))) {
      var users = mock(UserRepository.class);
      var oldAccount = new User();
      oldAccount.setId(983413L);
      oldAccount.setPassword("same-hash");
      oldAccount.setAccountVersion(1L);
      var currentAccount = new User();
      currentAccount.setId(983413L);
      currentAccount.setPassword("same-hash");
      currentAccount.setAccountVersion(2L);
      var service = new RefreshSessionService(connection.template(), users);
      ReflectionTestUtils.setField(service, "expirationMillis", 120_000L);
      when(users.findById(983413L)).thenReturn(Optional.of(oldAccount));
      String token = service.issue(oldAccount);
      when(users.findById(983413L)).thenReturn(Optional.of(currentAccount));
      assertThrows(BadCredentialsException.class, () -> service.issue(oldAccount));
      assertThrows(BadCredentialsException.class, () -> service.rotateWithToken(token));
    }
  }

  private boolean rotate(RefreshSessionService service, String token) {
    try {
      service.rotateWithToken(token);
      return true;
    } catch (BadCredentialsException exception) {
      return false;
    }
  }
}
