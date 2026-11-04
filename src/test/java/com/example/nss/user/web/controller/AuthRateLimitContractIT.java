package com.example.nss.user.web.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.nss.user.application.FirstAccessService;
import com.example.nss.user.application.RefreshSessionService;
import com.example.nss.user.application.UserService;
import com.example.nss.user.security.CustomUserDetailsService;
import com.example.nss.user.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {"api.auth.rate-limit.reset-maximum=2", "api.auth.rate-limit.window-seconds=900"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitContractIT {
  @Autowired MockMvc mockMvc;

  @MockitoBean UserService userService;
  @MockitoBean AuthenticationManager authenticationManager;
  @MockitoBean CustomUserDetailsService userDetailsService;
  @MockitoBean JwtService jwtService;
  @MockitoBean FirstAccessService firstAccessService;
  @MockitoBean RefreshSessionService refreshSessions;

  @Test
  void rateLimitsPasswordResetRequestsThroughTheHttpBoundary() throws Exception {
    String body = "{\"email\":\"limited@nss.test\"}";

    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isAccepted());
    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isAccepted());
    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isTooManyRequests());

    verify(userService, org.mockito.Mockito.times(2)).requestPasswordReset("limited@nss.test");
  }
}
