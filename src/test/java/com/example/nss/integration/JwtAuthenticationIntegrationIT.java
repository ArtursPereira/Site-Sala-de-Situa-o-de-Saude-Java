package com.example.nss.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.nss.user.domain.AccountType;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.UserRepository;
import com.example.nss.user.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthenticationIntegrationIT {
  @Autowired MockMvc mockMvc;
  @Autowired JwtService jwtService;
  @Autowired UserRepository users;

  @Test
  void exposesLivenessAndReadinessProbesWithoutAuthentication() throws Exception {
    mockMvc
        .perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));

    mockMvc
        .perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void acceptsAValidJwtForAnAdminEndpoint() throws Exception {
    String email = "jwt-admin@nss.test";
    User user = persistUser(email, AccountType.ADMIN, true);
    String token = jwtService.generateToken(user);

    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsAnInvalidJwtAsAnonymous() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatesAUserButStillDeniesTheAdminEndpoint() throws Exception {
    String email = "jwt-user@nss.test";
    User user = persistUser(email, AccountType.USER, true);
    String token = jwtService.generateToken(user);

    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsAValidJwtForADisabledUser() throws Exception {
    String email = "jwt-disabled@nss.test";
    User user = persistUser(email, AccountType.USER, false);
    String token = jwtService.generateToken(user);

    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void doesNotReuseAnOldTokenWhenTheEmailIsAssignedToANewUser() throws Exception {
    String email = "reused-email@nss.test";
    User previousUser = persistUser(email, AccountType.USER, true);
    String token = jwtService.generateToken(previousUser);

    users.delete(previousUser);
    users.flush();
    User replacementUser = persistUser(email, AccountType.USER, true);

    org.junit.jupiter.api.Assertions.assertNotEquals(previousUser.getId(), replacementUser.getId());
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  private User persistUser(String email, AccountType accountType, boolean active) {
    User user = new User();
    user.setEmail(email);
    user.setPassword("encoded-password");
    user.setAccountType(accountType);
    user.setAtivo(active);
    User saved = users.saveAndFlush(user);
    assertNotNull(saved.getId());
    return saved;
  }
}
