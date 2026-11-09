package com.example.nss.user.application;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AuthRateLimiterTest {
  @Test
  void rejectsNonPositiveWindowsBeforeConnecting() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AuthRateLimiter(
                "redis://localhost:6379",
                "test",
                5,
                3,
                0,
                6000,
                60,
                200,
                new SimpleMeterRegistry()));
  }
}
