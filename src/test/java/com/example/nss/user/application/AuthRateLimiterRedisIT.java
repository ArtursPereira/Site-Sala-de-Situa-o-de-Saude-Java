package com.example.nss.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.nss.shared.redis.DedicatedRedis;
import com.example.nss.user.exception.RateLimitExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AuthRateLimiterRedisIT {
  private AuthRateLimiter limiter(String key) {
    return new AuthRateLimiter(
        System.getenv().getOrDefault("SPRING_DATA_REDIS_URL", "redis://localhost:6379"),
        key,
        5,
        3,
        2,
        100_000,
        100_000,
        1000,
        new SimpleMeterRegistry());
  }

  @Test
  void retainsMoreThanTenThousandDistinctAccountWindows() throws Exception {
    URI base =
        URI.create(System.getenv().getOrDefault("SPRING_DATA_REDIS_URL", "redis://localhost:6379"));
    String databaseUrl = base.resolve("/15").toString();
    String secret = UUID.randomUUID().toString();
    try (var connection = new DedicatedRedis(databaseUrl, Duration.ofSeconds(3));
        var limiter =
            new AuthRateLimiter(
                databaseUrl,
                secret,
                2,
                2,
                900,
                100_000,
                100_000,
                3000,
                new SimpleMeterRegistry())) {
      String run = UUID.randomUUID().toString();
      var expected = new HashSet<String>();
      var hmac = Mac.getInstance("HmacSHA256");
      hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      try {
        for (int i = 0; i < 10_001; i++) {
          String email = run + "-" + i + "@nss.test";
          limiter.registerLoginAttempt(email);
          String digest =
              HexFormat.of()
                  .formatHex(hmac.doFinal(("account:" + email).getBytes(StandardCharsets.UTF_8)));
          expected.add("nss:auth:limit:v2:account:login:" + digest);
        }
        var keys = connection.template().keys("nss:auth:limit:v2:account:login:*");
        keys.retainAll(expected);
        assertEquals(10_001, keys.size());
        String firstAccount = run + "-0@nss.test";
        limiter.registerLoginAttempt(firstAccount);
        assertThrows(
            RateLimitExceededException.class, () -> limiter.registerLoginAttempt(firstAccount));
      } finally {
        connection.template().delete(expected);
      }
    }
  }

  @Test
  void enforcesOneAccountWindowAcrossTwoInstancesConcurrently() throws Exception {
    String secret = UUID.randomUUID().toString();
    String email = UUID.randomUUID() + "@nss.test";
    try (var first = limiter(secret);
        var second = limiter(secret);
        var executor = Executors.newFixedThreadPool(12)) {
      // Warm connections before the concurrent admissions.
      first.registerLoginAttempt("warm-first@nss.test");
      second.registerLoginAttempt("warm-second@nss.test");
      var futures =
          IntStream.range(0, 32)
              .mapToObj(
                  i ->
                      executor.submit(
                          () -> {
                            try {
                              (i % 2 == 0 ? first : second).registerLoginAttempt(email);
                              return true;
                            } catch (RateLimitExceededException ignored) {
                              return false;
                            }
                          }))
              .toList();
      int accepted = 0;
      for (var future : futures) if (future.get(5, TimeUnit.SECONDS)) accepted++;
      assertEquals(5, accepted);
      second.registerPasswordResetRequest(email);
      first.registerPasswordResetRequest(email);
      second.registerPasswordResetRequest(email);
      assertThrows(
          RateLimitExceededException.class, () -> first.registerPasswordResetRequest(email));
      Thread.sleep(2100);
      second.registerLoginAttempt(email);
    }
  }
}
