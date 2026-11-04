package com.example.nss.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.nss.user.application.RefreshSessionService;
import com.example.nss.user.domain.AccountType;
import com.example.nss.user.domain.RefreshSession;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.RefreshSessionRepository;
import com.example.nss.user.infrastructure.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshSessionIntegrationIT {
  @Autowired MockMvc mockMvc;
  @Autowired RefreshSessionService refreshSessions;
  @Autowired RefreshSessionRepository sessions;
  @Autowired UserRepository users;

  @Test
  void rotatesAPersistentRefreshSessionThroughTheHttpEndpoint() throws Exception {
    User user = persistUser("refresh-rotate@nss.test", true);
    String rawToken = refreshSessions.issue(user);

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .cookie(new jakarta.servlet.http.Cookie("nss_refresh", rawToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(
            header().string("Set-Cookie", org.hamcrest.Matchers.containsString("nss_refresh=")));

    List<RefreshSession> stored =
        sessions.findAll().stream()
            .filter(session -> user.getId().equals(session.getUser().getId()))
            .toList();
    assertEquals(2, stored.size());
    assertEquals(1, stored.stream().filter(session -> session.getRevokedAt() != null).count());
    assertEquals(1, stored.stream().filter(session -> session.getRevokedAt() == null).count());
  }

  @Test
  void logoutRevokesThePersistentRefreshSession() throws Exception {
    User user = persistUser("refresh-logout@nss.test", true);
    String rawToken = refreshSessions.issue(user);

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .cookie(new jakarta.servlet.http.Cookie("nss_refresh", rawToken)))
        .andExpect(status().isNoContent())
        .andExpect(
            header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

    RefreshSession session =
        sessions.findAll().stream()
            .filter(candidate -> user.getId().equals(candidate.getUser().getId()))
            .findFirst()
            .orElseThrow();
    assertNotNull(session.getRevokedAt());
  }

  @Test
  void rejectsAnExpiredPersistentRefreshSession() throws Exception {
    User user = persistUser("refresh-expired@nss.test", true);
    String rawToken = refreshSessions.issue(user);
    RefreshSession session =
        sessions.findAll().stream()
            .filter(candidate -> user.getId().equals(candidate.getUser().getId()))
            .findFirst()
            .orElseThrow();
    session.setExpiresAt(Instant.now().minusSeconds(1));
    sessions.saveAndFlush(session);

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .cookie(new jakarta.servlet.http.Cookie("nss_refresh", rawToken)))
        .andExpect(status().isUnauthorized());

    RefreshSession revoked =
        sessions.findAll().stream()
            .filter(candidate -> user.getId().equals(candidate.getUser().getId()))
            .findFirst()
            .orElseThrow();
    assertNotNull(revoked.getRevokedAt());
  }

  private User persistUser(String email, boolean active) {
    User user = new User();
    user.setEmail(email);
    user.setPassword("encoded-password");
    user.setAccountType(AccountType.USER);
    user.setAtivo(active);
    return users.saveAndFlush(user);
  }
}
