package com.example.nss.user.application;

import com.example.nss.user.exception.RateLimitExceededException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Distributed authentication rate limiter backed by Redis. */
@Service
public class AuthRateLimiter {
  private static final String PREFIX = "nss:auth:rate-limit:";
  private static final DefaultRedisScript<Long> RESERVE_SCRIPT =
      new DefaultRedisScript<>(
          "local started=redis.call('HGET',KEYS[1],'startedAt') "
              + "local now=tonumber(ARGV[1]) "
              + "local window=tonumber(ARGV[2]) "
              + "if started and now >= tonumber(started)+window then redis.call('DEL',KEYS[1]); started=nil end "
              + "local count=tonumber(redis.call('HGET',KEYS[1],'count') or '0') "
              + "local maximum=tonumber(ARGV[3]) "
              + "if count >= maximum then return 0 end "
              + "if not started then redis.call('HSET',KEYS[1],'startedAt',now) end "
              + "redis.call('HINCRBY',KEYS[1],'count',1) "
              + "redis.call('EXPIRE',KEYS[1],window) "
              + "return 1",
          Long.class);
  private static final DefaultRedisScript<Long> CHECK_SCRIPT =
      new DefaultRedisScript<>(
          "local started=redis.call('HGET',KEYS[1],'startedAt') "
              + "if not started then return 1 end "
              + "if tonumber(ARGV[1]) >= tonumber(started)+tonumber(ARGV[2]) then redis.call('DEL',KEYS[1]); return 1 end "
              + "return tonumber(redis.call('HGET',KEYS[1],'count') or '0') < tonumber(ARGV[3]) and 1 or 0",
          Long.class);

  private final StringRedisTemplate redis;
  private final int loginMaximum;
  private final int resetMaximum;
  private final Duration window;
  private final Map<String, Attempt> compatibilityAttempts;
  private final boolean useRedis;

  @Autowired
  public AuthRateLimiter(
      StringRedisTemplate redis,
      @Value("${api.auth.rate-limit.login-maximum:5}") int loginMaximum,
      @Value("${api.auth.rate-limit.reset-maximum:3}") int resetMaximum,
      @Value("${api.auth.rate-limit.window-seconds:900}") long windowSeconds,
      @Value("${api.auth.rate-limit.backend:redis}") String backend) {
    this.redis = redis;
    this.loginMaximum = loginMaximum;
    this.resetMaximum = resetMaximum;
    this.window = Duration.ofSeconds(windowSeconds);
    this.useRedis = !"memory".equalsIgnoreCase(backend);
    this.compatibilityAttempts = useRedis ? null : new ConcurrentHashMap<>();
  }

  /** Compatibility constructor for isolated unit tests; application wiring always uses Redis. */
  public AuthRateLimiter(int loginMaximum, int resetMaximum, long windowSeconds) {
    this.redis = null;
    this.loginMaximum = loginMaximum;
    this.resetMaximum = resetMaximum;
    this.window = Duration.ofSeconds(windowSeconds);
    this.compatibilityAttempts = new ConcurrentHashMap<>();
    this.useRedis = false;
  }

  public synchronized void registerLoginAttempt(String email) {
    reserve("login", email, loginMaximum);
  }

  public synchronized void checkLogin(String email) {
    check("login", email, loginMaximum);
  }

  public void loginSucceeded(String email) {
    if (useRedis) redis.delete(key("login", email)); else compatibilityAttempts.remove(key("login", email));
  }

  public synchronized void registerPasswordResetRequest(String email) {
    reserve("password-reset", email, resetMaximum);
  }

  private void check(String operation, String email, int maximum) {
    if (!useRedis) {
      Attempt attempt = compatibilityAttempts.get(key(operation, email));
      if (attempt == null || attempt.startedAt.plus(window).isBefore(java.time.Instant.now())) { compatibilityAttempts.remove(key(operation, email)); return; }
      if (attempt.count >= maximum) throw new RateLimitExceededException();
      return;
    }
    Long allowed = redis.execute(
        CHECK_SCRIPT,
        List.of(key(operation, email)),
        Long.toString(System.currentTimeMillis() / 1000),
        Long.toString(window.toSeconds()),
        Integer.toString(maximum));
    if (!Long.valueOf(1L).equals(allowed)) throw new RateLimitExceededException();
  }

  private void reserve(String operation, String email, int maximum) {
    if (!useRedis) {
      String key = key(operation, email);
      Attempt attempt = compatibilityAttempts.get(key);
      if (attempt == null || !attempt.startedAt.plus(window).isAfter(java.time.Instant.now())) attempt = new Attempt(0, java.time.Instant.now());
      if (attempt.count >= maximum) throw new RateLimitExceededException();
      compatibilityAttempts.put(key, new Attempt(attempt.count + 1, attempt.startedAt));
      return;
    }
    Long allowed = redis.execute(
        RESERVE_SCRIPT,
        List.of(key(operation, email)),
        Long.toString(System.currentTimeMillis() / 1000),
        Long.toString(window.toSeconds()),
        Integer.toString(maximum));
    if (!Long.valueOf(1L).equals(allowed)) throw new RateLimitExceededException();
  }

  private String key(String operation, String email) {
    return PREFIX + operation + ":" + email.trim().toLowerCase(Locale.ROOT);
  }

  private record Attempt(int count, java.time.Instant startedAt) {}
}
