package com.example.nss.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.nss.shared.notification.MailService;
import com.example.nss.user.api.request.AccountInvitationRequest;
import com.example.nss.user.application.AdminUserService;
import com.example.nss.user.domain.AccountType;
import com.example.nss.user.infrastructure.repository.UserRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
class AdminUserEmailUniquenessPostgresIT {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @Autowired AdminUserService service;
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
  void databaseRejectsConcurrentInvitationsForTheSameNormalizedEmail() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> first = submitInvitation(executor, start, "Concurrent@NSS.TEST");
      Future<Boolean> second = submitInvitation(executor, start, " concurrent@nss.test ");
      start.countDown();

      List<Boolean> results = List.of(first.get(), second.get());

      assertEquals(1, results.stream().filter(Boolean.TRUE::equals).count());
      assertEquals(1, results.stream().filter(Boolean.FALSE::equals).count());
      assertEquals(
          1,
          users.findAll().stream().filter(u -> "concurrent@nss.test".equals(u.getEmail())).count());
    } finally {
      executor.shutdownNow();
    }
  }

  private Future<Boolean> submitInvitation(
      ExecutorService executor, CountDownLatch start, String email) {
    return executor.submit(
        () -> {
          start.await();
          try {
            service.invite(new AccountInvitationRequest(email, AccountType.USER));
            return true;
          } catch (IllegalArgumentException exception) {
            return false;
          }
        });
  }
}
