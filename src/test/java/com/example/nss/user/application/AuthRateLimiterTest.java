package com.example.nss.user.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.nss.user.exception.RateLimitExceededException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class AuthRateLimiterTest {
  @Test
  void blocksTheSixthFailedLoginWithinTheWindowAndClearsOnSuccess() {
    AuthRateLimiter limiter = new AuthRateLimiter(5, 3, 900);

    for (int attempt = 0; attempt < 5; attempt++) {
      limiter.registerLoginAttempt("user@nss.test");
    }
    assertThrows(RateLimitExceededException.class, () -> limiter.checkLogin("USER@nss.test"));

    limiter.loginSucceeded("user@nss.test");
    assertDoesNotThrow(() -> limiter.checkLogin("user@nss.test"));
  }

  @Test
  void limitsPasswordResetRequestsWithoutDependingOnAccountExistence() {
    AuthRateLimiter limiter = new AuthRateLimiter(5, 3, 900);

    for (int attempt = 0; attempt < 3; attempt++) {
      limiter.registerPasswordResetRequest("unknown@nss.test");
    }
    assertThrows(
        RateLimitExceededException.class,
        () -> limiter.registerPasswordResetRequest("unknown@nss.test"));
  }

  @Test
  void expiresAWindowAndKeepsLoginAndPasswordResetOperationsIndependent() {
    AuthRateLimiter expired = new AuthRateLimiter(1, 1, -1);
    expired.registerLoginAttempt("USER@nss.test");
    assertDoesNotThrow(() -> expired.checkLogin("user@nss.test"));

    AuthRateLimiter limiter = new AuthRateLimiter(1, 1, 900);
    limiter.registerLoginAttempt("user@nss.test");
    assertDoesNotThrow(() -> limiter.registerPasswordResetRequest("user@nss.test"));
  }

  @Test
  void reservesAtMostTheConfiguredAttemptsUnderConcurrency() throws Exception {
    AuthRateLimiter limiter = new AuthRateLimiter(5, 3, 900);
    ExecutorService executor = Executors.newFixedThreadPool(16);
    CountDownLatch start = new CountDownLatch(1);
    List<java.util.concurrent.Future<Boolean>> results = new ArrayList<>();

    try {
      for (int i = 0; i < 32; i++) {
        results.add(
            executor.submit(
                () -> {
                  start.await();
                  try {
                    limiter.registerLoginAttempt("concurrent@nss.test");
                    return true;
                  } catch (RateLimitExceededException exception) {
                    return false;
                  }
                }));
      }
      start.countDown();
      long accepted = 0;
      for (var result : results) accepted += result.get() ? 1 : 0;
      org.junit.jupiter.api.Assertions.assertEquals(5, accepted);
    } finally {
      executor.shutdownNow();
    }
  }
}
