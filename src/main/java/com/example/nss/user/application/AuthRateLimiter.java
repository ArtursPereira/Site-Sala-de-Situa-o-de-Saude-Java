package com.example.nss.user.application;

import com.example.nss.user.exception.RateLimitExceededException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Single-node protection for public authentication endpoints. The key is the normalized e-mail so
 * the response never reveals whether the account exists.
 */
@Service
public class AuthRateLimiter {
  private final Map<String, AttemptWindow> attempts = new HashMap<>();
  private final int loginMaximum;
  private final int resetMaximum;
  private final Duration window;
  private final int maximumEntries;

  @Autowired
  public AuthRateLimiter(
      @Value("${api.auth.rate-limit.login-maximum:5}") int loginMaximum,
      @Value("${api.auth.rate-limit.reset-maximum:3}") int resetMaximum,
      @Value("${api.auth.rate-limit.window-seconds:900}") long windowSeconds,
      @Value("${api.auth.rate-limit.maximum-entries:10000}") int maximumEntries) {
    this.loginMaximum = loginMaximum;
    this.resetMaximum = resetMaximum;
    this.window = Duration.ofSeconds(windowSeconds);
    this.maximumEntries = maximumEntries;
  }

  public AuthRateLimiter(int loginMaximum, int resetMaximum, long windowSeconds) {
    this(loginMaximum, resetMaximum, windowSeconds, 10_000);
  }

  public synchronized void registerLoginAttempt(String email) {
    reserve("login", email, loginMaximum);
  }

  public synchronized void checkLogin(String email) {
    check("login", email, loginMaximum);
  }

  public synchronized void loginSucceeded(String email) {
    attempts.remove(key("login", email));
  }

  public synchronized void registerPasswordResetRequest(String email) {
    reserve("password-reset", email, resetMaximum);
  }

  private void check(String operation, String email, int maximum) {
    AttemptWindow current = attempts.get(key(operation, email));
    if (current == null || expired(current)) {
      if (current != null) {
        attempts.remove(key(operation, email), current);
      }
      return;
    }
    if (current.count >= maximum) {
      throw new RateLimitExceededException();
    }
  }

  private void reserve(String operation, String email, int maximum) {
    String key = key(operation, email);
    removeExpiredAttempts();
    AttemptWindow current = attempts.get(key);
    if (current != null && current.count >= maximum) {
      throw new RateLimitExceededException();
    }
    if (current == null) {
      evictOldestIfNecessary();
      attempts.put(key, new AttemptWindow(1, Instant.now()));
    } else {
      attempts.put(key, new AttemptWindow(current.count + 1, current.startedAt));
    }
  }

  private void removeExpiredAttempts() {
    Instant now = Instant.now();
    Iterator<Map.Entry<String, AttemptWindow>> iterator = attempts.entrySet().iterator();
    while (iterator.hasNext()) {
      if (expired(iterator.next().getValue(), now)) iterator.remove();
    }
  }

  private void evictOldestIfNecessary() {
    if (maximumEntries <= 0 || attempts.size() < maximumEntries) return;
    String oldestKey = null;
    Instant oldest = Instant.MAX;
    for (Map.Entry<String, AttemptWindow> entry : attempts.entrySet()) {
      if (entry.getValue().startedAt.isBefore(oldest)) {
        oldest = entry.getValue().startedAt;
        oldestKey = entry.getKey();
      }
    }
    if (oldestKey != null) attempts.remove(oldestKey);
  }

  private boolean expired(AttemptWindow attempt) {
    return expired(attempt, Instant.now());
  }

  private boolean expired(AttemptWindow attempt, Instant now) {
    return !attempt.startedAt.plus(window).isAfter(now);
  }

  private String key(String operation, String email) {
    return operation + ":" + email.trim().toLowerCase(Locale.ROOT);
  }

  private record AttemptWindow(int count, Instant startedAt) {}
}
