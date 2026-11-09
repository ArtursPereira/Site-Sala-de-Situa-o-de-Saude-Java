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
      var service = new RefreshSessionService(connection.template(), users, encoder);
      ReflectionTestUtils.setField(service, "expirationMillis", 120_000L);
      String old = service.issue(user, "old-password");
      String next = service.rotateWithToken(old).rawToken();
      service.revoke(old);
      assertThrows(BadCredentialsException.class, () -> service.rotateWithToken(next));
      String current = service.issue(user, "old-password");
      user.setPassword(encoder.encode("new-password"));
      assertThrows(BadCredentialsException.class, () -> service.rotateWithToken(current));
      assertThrows(BadCredentialsException.class, () -> service.issue(user, "old-password"));
      String token = service.issue(user, "new-password");
      try (var executor = Executors.newFixedThreadPool(2)) {
        var first = executor.submit(() -> rotate(service, token));
        var second = executor.submit(() -> rotate(service, token));
        assertNotEquals(first.get(), second.get());
      }
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
