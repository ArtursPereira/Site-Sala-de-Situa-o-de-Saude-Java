package com.example.nss.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.nss.shared.notification.MailService;
import com.example.nss.user.infrastructure.repository.FirstAccessTokenRepository;
import com.example.nss.user.infrastructure.repository.UserRepository;
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
class AdminUserControllerPersistenceIT {
  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired FirstAccessTokenRepository tokens;

  @MockitoBean MailService mail;

  @Test
  void managesAnInvitedUserThroughTheAuthenticatedAdminHttpFlow() throws Exception {
    String email = "http-admin-flow@nss.test";

    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .with(user("admin@nss.test").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"accountType\":\"USER\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.firstAccessPending").value(true));

    Long id = users.findByEmail(email).orElseThrow().getId();
    assertNotNull(id);

    mockMvc
        .perform(get("/api/v1/admin/users/invitations").with(user("admin@nss.test").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.email=='" + email + "')]").isNotEmpty());

    mockMvc
        .perform(
            post(
                    "/api/v1/admin/users/invitations/{id}/renew",
                    tokens.findByUserId(id).orElseThrow().getId())
                .with(user("admin@nss.test").roles("ADMIN"))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));

    Long renewedInvitationId = tokens.findByUserId(id).orElseThrow().getId();
    mockMvc
        .perform(
            post("/api/v1/admin/users/invitations/{id}/cancel", renewedInvitationId)
                .with(user("admin@nss.test").roles("ADMIN"))
                .with(csrf()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/admin/users").with(user("admin@nss.test").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.email=='" + email + "')]").isNotEmpty());

    mockMvc
        .perform(
            patch("/api/v1/admin/users/{id}/activation", id)
                .with(user("admin@nss.test").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mockMvc
        .perform(
            post("/api/v1/admin/users/{id}/password-reset", id)
                .with(user("admin@nss.test").roles("ADMIN"))
                .with(csrf()))
        .andExpect(status().isNoContent());

    assertFalse(users.findById(id).orElseThrow().isAtivo());
  }

  @Test
  void rejectsInvalidAdminPaginationThroughTheHttpBoundary() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/users")
                .param("page", "-1")
                .with(user("admin@nss.test").roles("ADMIN")))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            get("/api/v1/admin/users")
                .param("size", "61")
                .with(user("admin@nss.test").roles("ADMIN")))
        .andExpect(status().isBadRequest());
  }
}
