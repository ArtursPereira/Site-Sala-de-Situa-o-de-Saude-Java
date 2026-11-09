package com.example.nss.user.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.nss.user.api.request.LoginRequest;
import com.example.nss.user.api.response.UserResponse;
import com.example.nss.user.application.AuthRateLimiter;
import com.example.nss.user.application.FirstAccessService;
import com.example.nss.user.application.RefreshSessionService;
import com.example.nss.user.application.UserService;
import com.example.nss.user.domain.User;
import com.example.nss.user.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
  @Mock UserService userService;
  @Mock AuthenticationManager authenticationManager;
  @Mock UserDetailsService userDetailsService;
  @Mock JwtService jwtService;
  @Mock FirstAccessService firstAccessService;
  @Mock RefreshSessionService refreshSessions;
  @Mock AuthRateLimiter rateLimiter;
  @Mock Authentication authentication;
  @Mock HttpServletResponse response;
  @Mock HttpServletRequest request;
  @InjectMocks AuthController controller;

  @Test
  void normalizesEmailAndReusesAuthenticatedPrincipal() {
    User user = new User();
    user.setId(7L);
    user.setEmail("ana@example.com");
    user.setPassword("encoded");
    user.setAccountVersion(0L);
    UserDetails principal = new com.example.nss.user.security.VersionedUserDetails(user);
    UserResponse userResponse = new UserResponse(7L, "Ana", "ana@example.com", true);

    when(authentication.getPrincipal()).thenReturn(principal);
    when(authenticationManager.authenticate(any())).thenReturn(authentication);
    when(userService.entityById(7L)).thenReturn(user);
    when(jwtService.generateToken(user)).thenReturn("access-token");
    when(refreshSessions.issue(user)).thenReturn("refresh-token");
    when(userService.response(user)).thenReturn(userResponse);
    ReflectionTestUtils.setField(controller, "refreshCookieName", "nss_refresh");
    ReflectionTestUtils.setField(controller, "refreshExpiration", 604_800_000L);

    controller.login(new LoginRequest("  Ana@Example.COM ", "secret"), response);

    ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
        ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
    verify(authenticationManager).authenticate(captor.capture());
    assertEquals("ana@example.com", captor.getValue().getPrincipal());
    verify(jwtService).generateToken(user);
    verify(userDetailsService, never()).loadUserByUsername(any());
  }

  @Test
  void rejectsLoginWhenAccountChangedAfterAuthentication() {
    User authenticated = new User();
    authenticated.setId(7L);
    authenticated.setEmail("ana@example.com");
    authenticated.setPassword("encoded");
    authenticated.setAccountVersion(1L);
    User current = new User();
    current.setId(7L);
    current.setEmail("ana@example.com");
    current.setPassword("encoded");
    current.setAccountVersion(2L);
    when(authentication.getPrincipal())
        .thenReturn(new com.example.nss.user.security.VersionedUserDetails(authenticated));
    when(authenticationManager.authenticate(any())).thenReturn(authentication);
    when(userService.entityById(7L)).thenReturn(current);

    assertThrows(
        BadCredentialsException.class,
        () -> controller.login(new LoginRequest("ana@example.com", "secret"), response));
    verify(refreshSessions, never()).issue(any());
  }

  @Test
  void recordsFailedLoginAttemptsWhenAuthenticationFails() {
    BadCredentialsException failure = new BadCredentialsException("invalid");
    when(authenticationManager.authenticate(any())).thenThrow(failure);

    assertThrows(
        BadCredentialsException.class,
        () -> controller.login(new LoginRequest("user@example.com", "wrong"), response));

    verify(rateLimiter).registerLoginAttempt("user@example.com");
  }

  @Test
  void rejectsAuthenticationWithoutAUserDetailsPrincipal() {
    when(authentication.getPrincipal()).thenReturn("unexpected-principal");
    when(authenticationManager.authenticate(any())).thenReturn(authentication);

    assertThrows(
        AuthenticationServiceException.class,
        () -> controller.login(new LoginRequest("user@example.com", "secret"), response));

    verify(userService, never()).entityByEmail(any());
    verify(refreshSessions, never()).issue(any());
  }

  @Test
  void refreshUsesTheRotatedPrincipalAndCookie() {
    User user = new User();
    user.setId(7L);
    user.setEmail("ana@example.com");
    user.setPassword("encoded");
    user.setAccountVersion(0L);
    when(request.getCookies()).thenReturn(new Cookie[] {new Cookie("nss_refresh", "old-token")});
    when(refreshSessions.rotateWithToken("old-token"))
        .thenReturn(new RefreshSessionService.Rotation(user, "new-token"));
    when(jwtService.generateToken(user)).thenReturn("new-access");
    when(userService.response(user))
        .thenReturn(new UserResponse(null, "Ana", "ana@example.com", true));
    ReflectionTestUtils.setField(controller, "refreshCookieName", "nss_refresh");
    ReflectionTestUtils.setField(controller, "refreshExpiration", 604_800_000L);

    controller.refresh(request, response);

    verify(refreshSessions).rotateWithToken("old-token");
    verify(userService).response(user);
    verify(jwtService).generateToken(user);
    verify(response).addHeader(eq("Set-Cookie"), any());
  }

  @Test
  void logoutRevokesCookieAndClearsIt() {
    MockHttpServletRequest logoutRequest = new MockHttpServletRequest();
    logoutRequest.setCookies(new Cookie("nss_refresh", "refresh-token"));
    ReflectionTestUtils.setField(controller, "refreshCookieName", "nss_refresh");

    controller.logout(logoutRequest, response);

    verify(refreshSessions).revoke("refresh-token");
    verify(response).addHeader(eq("Set-Cookie"), any());
  }
}
