package com.example.nss.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.nss.shared.notification.MailService;
import com.example.nss.user.api.request.FirstAccessRequest;
import com.example.nss.user.application.FirstAccessService;
import com.example.nss.user.domain.AccountType;
import com.example.nss.user.domain.FirstAccessToken;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.FirstAccessTokenRepository;
import com.example.nss.user.infrastructure.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class FirstAccessConcurrencyPostgresIT {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @Autowired FirstAccessService firstAccess;
  @Autowired FirstAccessTokenRepository tokens;
  @Autowired UserRepository users;

  @MockitoBean MailService mail;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);
    registry.add("spring.docker.compose.enabled", () -> false);
    registry.add("analytics.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("analytics.datasource.username", POSTGRES::getUsername);
    registry.add("analytics.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void consumesTheSameFirstAccessTokenOnlyOnceUnderConcurrency() throws Exception {
    User user = new User();
    user.setEmail("concurrent-first-access@nss.test");
    user.setAccountType(AccountType.USER);
    user.setAtivo(true);
    user = users.saveAndFlush(user);

    String rawToken = "concurrent-first-access-token";
    FirstAccessToken token = new FirstAccessToken();
    token.setUser(user);
    token.setTokenHash(hash(rawToken));
    token.setStatus("PENDING");
    token.setPurpose("FIRST_ACCESS");
    token.setSentAt(Instant.now());
    token.setExpiresAt(Instant.now().plusSeconds(300));
    tokens.saveAndFlush(token);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<Void> first = submit(executor, ready, start, rawToken, "First");
    Future<Void> second = submit(executor, ready, start, rawToken, "Second");

    ready.await();
    start.countDown();

    int successes = 0;
    int failures = 0;
    for (Future<Void> result : java.util.List.of(first, second)) {
      try {
        result.get();
        successes++;
      } catch (ExecutionException exception) {
        assertNotNull(exception.getCause());
        failures++;
      }
    }
    executor.shutdownNow();

    assertEquals(1, successes);
    assertEquals(1, failures);
    assertEquals("COMPLETED", tokens.findById(token.getId()).orElseThrow().getStatus());
    assertTrue(
        java.util.Set.of("First", "Second")
            .contains(users.findById(user.getId()).orElseThrow().getNome()));
  }

  private Future<Void> submit(
      ExecutorService executor,
      CountDownLatch ready,
      CountDownLatch start,
      String rawToken,
      String name) {
    return executor.submit(
        () -> {
          ready.countDown();
          start.await();
          firstAccess.complete(
              new FirstAccessRequest(
                  "concurrent-first-access@nss.test", name, "Password123!", rawToken));
          return null;
        });
  }

  private String hash(String raw) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
  }
}
