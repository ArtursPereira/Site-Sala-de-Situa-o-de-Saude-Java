package com.example.nss.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowPostgresIT {
  private static final String JWT_SECRET =
      "ZmFrZS1wcm9kLXNlY3JldC1sb25nLWVub3VnaC1mb3ItaHMyNTYtMzI=";

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JwtService jwtService;

  @MockitoBean com.example.nss.shared.notification.MailService mail;

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
    registry.add("jwt.secret", () -> JWT_SECRET);
  }

  @Test
  void logsInWithThePersistedPasswordAndUsesTheReturnedJwtOnMe() throws Exception {
    persistUser("admin-login@nss.test", "Admin Login", AccountType.ADMIN, true, "Password123!");

    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"ADMIN-LOGIN@NSS.TEST\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("admin-login@nss.test"))
            .andExpect(jsonPath("$.permissions[0]").value("USER_MANAGEMENT"))
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn();

    String accessToken =
        com.jayway.jsonpath.JsonPath.read(
            login.getResponse().getContentAsString(), "$.accessToken");

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("admin-login@nss.test"))
        .andExpect(jsonPath("$.name").value("Admin Login"))
        .andExpect(jsonPath("$.permissions[0]").value("USER_MANAGEMENT"));
  }

  @Test
  void authenticatesAUserButDeniesTheAdministrativeEndpoint() throws Exception {
    persistUser("regular-login@nss.test", "Regular Login", AccountType.USER, true, "Password123!");

    String token = login("regular-login@nss.test", "Password123!");

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions").isEmpty());
    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsAnIncorrectPasswordBeforeIssuingTokens() throws Exception {
    persistUser(
        "wrong-password@nss.test", "Wrong Password", AccountType.USER, true, "Password123!");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"wrong-password@nss.test\",\"password\":\"Wrong123!\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsLoginForADisabledPersistedAccount() throws Exception {
    persistUser(
        "disabled-login@nss.test", "Disabled Login", AccountType.USER, false, "Password123!");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"disabled-login@nss.test\",\"password\":\"Password123!\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsExpiredAndTamperedTokensOnProtectedEndpoints() throws Exception {
    User expiredUser = new User();
    expiredUser.setId(999L);
    expiredUser.setEmail("expired@nss.test");
    String expired = new JwtService(JWT_SECRET, -1).generateToken(expiredUser);

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + expired.substring(0, expired.length() - 2)))
        .andExpect(status().isUnauthorized());
  }

  private String login(String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String token =
        com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(), "$.accessToken");
    assertNotNull(token);
    return token;
  }

  private User persistUser(
      String email, String name, AccountType accountType, boolean active, String password) {
    User user = new User();
    user.setEmail(email);
    user.setNome(name);
    user.setPassword(passwordEncoder.encode(password));
    user.setPasswordCreatedAt(java.time.Instant.now());
    user.setAccountType(accountType);
    user.setAtivo(active);
    User saved = users.saveAndFlush(user);
    assertNotNull(saved.getId());
    return saved;
  }
}
