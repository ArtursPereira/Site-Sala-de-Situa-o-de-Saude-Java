package com.example.nss.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.nss.shared.notification.MailService;
import com.example.nss.user.application.RefreshSessionService;
import com.example.nss.user.domain.AccountType;
import com.example.nss.user.domain.FirstAccessToken;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.FirstAccessTokenRepository;
import com.example.nss.user.infrastructure.repository.RefreshSessionRepository;
import com.example.nss.user.infrastructure.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FirstAccessIntegrationIT {
  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired FirstAccessTokenRepository tokens;
  @Autowired RefreshSessionRepository sessions;
  @Autowired RefreshSessionService refreshSessions;

  @MockitoBean MailService mail;

  @Test
  void completesFirstAccessAndPersistsTheConfiguredAccount() throws Exception {
    User user = persistUser("first-access-http@nss.test", false);
    createToken(user, "first-access-http-token", "FIRST_ACCESS");

    mockMvc
        .perform(
            post("/api/v1/auth/first-access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"first-access-http@nss.test\",\"name\":\"First User\","
                        + "\"password\":\"Password123!\",\"token\":\"first-access-http-token\"}"))
        .andExpect(status().isNoContent());

    User configured = users.findById(user.getId()).orElseThrow();
    assertEquals("First User", configured.getNome());
    assertNotNull(configured.getPassword());
    assertEquals(false, configured.isAtivo());
    assertEquals("COMPLETED", tokens.findByUserId(user.getId()).orElseThrow().getStatus());
  }

  @Test
  void completesPasswordResetAndRevokesThePersistedSessions() throws Exception {
    User user = persistUser("password-reset-http@nss.test", true);
    user.setPassword("old-password");
    users.saveAndFlush(user);
    refreshSessions.issue(user);
    createToken(user, "password-reset-http-token", "PASSWORD_RESET");

    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"password-reset-http@nss.test\","
                        + "\"token\":\"password-reset-http-token\","
                        + "\"password\":\"NewPassword123!\"}"))
        .andExpect(status().isNoContent());

    User reset = users.findById(user.getId()).orElseThrow();
    assertNotNull(reset.getPassword());
    assertEquals(true, reset.isAtivo());
    assertEquals("COMPLETED", tokens.findByUserId(user.getId()).orElseThrow().getStatus());
    assertEquals(
        1,
        sessions.findAll().stream()
            .filter(session -> user.getId().equals(session.getUser().getId()))
            .filter(session -> session.getRevokedAt() != null)
            .count());
  }

  @Test
  void requestsPasswordResetForAnActivePersistedAccount() throws Exception {
    User user = persistUser("password-request-http@nss.test", true);

    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"password-request-http@nss.test\"}"))
        .andExpect(status().isAccepted());

    FirstAccessToken token = tokens.findByUserId(user.getId()).orElseThrow();
    assertEquals("PASSWORD_RESET", token.getPurpose());
    assertEquals("PENDING", token.getStatus());
    org.mockito.Mockito.verify(mail)
        .sendTextAfterCommit(
            org.mockito.ArgumentMatchers.eq("password-request-http@nss.test"),
            org.mockito.ArgumentMatchers.contains("Redefinição"),
            org.mockito.ArgumentMatchers.any());
  }

  private User persistUser(String email, boolean active) {
    User user = new User();
    user.setEmail(email);
    user.setAccountType(AccountType.USER);
    user.setAtivo(active);
    return users.saveAndFlush(user);
  }

  private void createToken(User user, String rawToken, String purpose) {
    FirstAccessToken token = new FirstAccessToken();
    token.setUser(user);
    token.setTokenHash(hash(rawToken));
    token.setStatus("PENDING");
    token.setPurpose(purpose);
    token.setSentAt(Instant.now());
    token.setExpiresAt(Instant.now().plusSeconds(300));
    tokens.saveAndFlush(token);
  }

  private String hash(String rawToken) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
