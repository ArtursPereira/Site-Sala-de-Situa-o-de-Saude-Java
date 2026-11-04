package com.example.nss.user.web.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.nss.shared.web.GlobalExceptionHandler;
import com.example.nss.user.api.response.ManagedUserResponse;
import com.example.nss.user.api.response.UserPageResponse;
import com.example.nss.user.application.AdminUserService;
import com.example.nss.user.domain.AccountType;
import com.example.nss.user.security.CustomUserDetailsService;
import com.example.nss.user.security.JwtService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminUserControllerWebMvcTest {
  @Autowired MockMvc mockMvc;

  @MockitoBean AdminUserService service;
  @MockitoBean JwtService jwtService;
  @MockitoBean CustomUserDetailsService userDetailsService;

  @Test
  void returnsUserPageWithStableJsonContract() throws Exception {
    ManagedUserResponse user =
        new ManagedUserResponse(7L, "ana@example.com", "Ana", AccountType.ADMIN, true, false, null);
    when(service.search(null, 0, 60))
        .thenReturn(new UserPageResponse(List.of(user), 0, 60, false, 1));

    mockMvc
        .perform(get("/api/v1/admin/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id").value(7))
        .andExpect(jsonPath("$.items[0].email").value("ana@example.com"))
        .andExpect(jsonPath("$.items[0].accountType").value("ADMIN"))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(60));
  }

  @Test
  void rejectsInvalidPaginationAsStructuredError() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/users").queryParam("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(
            jsonPath("$.message").value("page deve ser >= 0 e size deve estar entre 1 e 60"));
  }

  @Test
  void validatesInvitationPayloadBeforeCallingService() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    verify(service, org.mockito.Mockito.never()).invite(any());
  }
}
