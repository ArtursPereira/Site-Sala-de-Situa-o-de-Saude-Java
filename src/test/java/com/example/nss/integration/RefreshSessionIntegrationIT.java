package com.example.nss.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.nss.user.application.RefreshSessionService;
import com.example.nss.user.domain.AccountType;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshSessionIntegrationIT {
  @Autowired MockMvc mockMvc;
  @Autowired RefreshSessionService refreshSessions;
  @Autowired StringRedisTemplate redis;
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

    assertNotNull(redis.opsForHash().get(key(rawToken), "revokedAt"));
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

    assertNotNull(redis.opsForHash().get(key(rawToken), "revokedAt"));
  }

  @Test
  void rejectsAnExpiredPersistentRefreshSession() throws Exception {
    User user = persistUser("refresh-expired@nss.test", true);
    String rawToken = refreshSessions.issue(user);
    String tokenKey = key(rawToken);
    redis.opsForHash().put(tokenKey, "expiresAt", Instant.now().minusSeconds(1).toString());

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .cookie(new jakarta.servlet.http.Cookie("nss_refresh", rawToken)))
        .andExpect(status().isUnauthorized());

    // The Redis TTL is recalculated from expiresAt and may remove the hash
    // immediately after the invalid refresh is detected.
  }

  private User persistUser(String email, boolean active) {
    User user = new User();
    user.setEmail(email);
    user.setPassword("encoded-password");
    user.setAccountType(AccountType.USER);
    user.setAtivo(active);
    return users.saveAndFlush(user);
  }

  private String key(String rawToken) {
    try {
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
      return "nss:auth:refresh:" + hash;
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
