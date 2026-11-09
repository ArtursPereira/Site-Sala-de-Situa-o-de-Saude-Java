package com.example.nss.user.application;

import com.example.nss.shared.redis.ClientAddress;
import com.example.nss.shared.redis.DedicatedRedis;
import com.example.nss.user.exception.AuthStoreUnavailableException;
import com.example.nss.user.exception.RateLimitExceededException;
import com.example.nss.user.security.EmailNormalizer;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class AuthRateLimiter implements AutoCloseable {
  private static final String ROUTE_TAG = "route";
  private static final String PREFIX = "nss:auth:limit:v2:";
  private static final long MINUTE_MS = 60_000;
  private static final DefaultRedisScript<Long> ACCOUNT = script("auth-fixed-window.lua");
  private static final DefaultRedisScript<Long> BUCKET = script("auth-token-bucket.lua");

  private final DedicatedRedis connection;
  private final StringRedisTemplate redis;
  private final MeterRegistry metrics;
  private final byte[] keySecret;
  private final int loginMaximum;
  private final int resetMaximum;
  private final long windowMillis;
  private final int globalPerMinute;
  private final int ipPerMinute;

  public AuthRateLimiter(
      @Value("${api.auth.rate-limit.redis-url}") String url,
      @Value("${api.auth.rate-limit.key-secret}") String secret,
      @Value("${api.auth.rate-limit.login-maximum:5}") int loginMaximum,
      @Value("${api.auth.rate-limit.reset-maximum:3}") int resetMaximum,
      @Value("${api.auth.rate-limit.window-seconds:900}") long windowSeconds,
      @Value("${api.auth.rate-limit.global-per-minute:6000}") int globalPerMinute,
      @Value("${api.auth.rate-limit.ip-per-minute:60}") int ipPerMinute,
      @Value("${api.auth.rate-limit.redis-timeout-ms:200}") long timeoutMillis,
      MeterRegistry metrics) {
    if (loginMaximum < 1
        || resetMaximum < 1
        || windowSeconds < 1
        || globalPerMinute < 1
        || ipPerMinute < 1
        || timeoutMillis < 1
        || secret.isBlank()) {
      throw new IllegalArgumentException("Parâmetros inválidos para o rate limiter");
    }
    this.connection = new DedicatedRedis(url, Duration.ofMillis(timeoutMillis));
    this.redis = connection.template();
    this.keySecret = secret.getBytes(StandardCharsets.UTF_8);
    this.loginMaximum = loginMaximum;
    this.resetMaximum = resetMaximum;
    this.windowMillis = Math.multiplyExact(windowSeconds, 1000);
    this.globalPerMinute = globalPerMinute;
    this.ipPerMinute = ipPerMinute;
    this.metrics = metrics;
  }

  public void registerLoginAttempt(String email) {
    admit("login", email, loginMaximum);
  }

  public void registerPasswordResetRequest(String email) {
    admit("password-reset", email, resetMaximum);
  }

  public void registerRefreshAttempt() {
    admit("refresh", null, 0);
  }

  public void registerPasswordResetCompletion() {
    admit("password-reset-complete", null, 0);
  }

  public void registerFirstAccessAttempt() {
    admit("first-access", null, 0);
  }

  private void admit(String route, String account, int maximum) {
    long started = System.nanoTime();
    try {
      check(
          BUCKET,
          PREFIX + "ip:" + route + ":" + digest("ip:" + ClientAddress.current()),
          ipPerMinute,
          MINUTE_MS,
          route,
          "ip");
      check(BUCKET, PREFIX + "global", globalPerMinute, MINUTE_MS, route, "global");
      if (account != null) {
        check(
            ACCOUNT,
            PREFIX
                + "account:"
                + route
                + ":"
                + digest("account:" + EmailNormalizer.normalize(account)),
            maximum,
            windowMillis,
            route,
            "account");
      }
      metrics.counter("nss.auth.admission", ROUTE_TAG, route, "result", "accepted").increment();
    } catch (DataAccessException exception) {
      metrics
          .counter("nss.auth.admission", ROUTE_TAG, route, "result", "redis_unavailable")
          .increment();
      throw new AuthStoreUnavailableException(exception);
    } finally {
      metrics
          .timer("nss.auth.admission.duration", ROUTE_TAG, route)
          .record(Duration.ofNanos(System.nanoTime() - started));
    }
  }

  private void check(
      DefaultRedisScript<Long> rule,
      String key,
      int max,
      long window,
      String route,
      String bucket) {
    Long retryMs = redis.execute(rule, List.of(key), Integer.toString(max), Long.toString(window));
    if (retryMs == null) throw new AuthStoreUnavailableException(null);
    if (retryMs > 0) {
      metrics
          .counter("nss.auth.admission", ROUTE_TAG, route, "result", bucket + "_limited")
          .increment();
      throw new RateLimitExceededException((retryMs + 999) / 1000);
    }
  }

  private String digest(String value) {
    try {
      Mac hmac = Mac.getInstance("HmacSHA256");
      hmac.init(new SecretKeySpec(keySecret, "HmacSHA256"));
      return HexFormat.of().formatHex(hmac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC de autenticação indisponível", exception);
    }
  }

  private static DefaultRedisScript<Long> script(String name) {
    var script = new DefaultRedisScript<Long>();
    script.setLocation(new ClassPathResource("redis/" + name));
    script.setResultType(Long.class);
    return script;
  }

  @Override
  public void close() {
    connection.close();
  }
}
