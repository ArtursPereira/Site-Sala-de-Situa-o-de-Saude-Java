package com.example.nss.user.web.controller;

import com.example.nss.user.api.request.FirstAccessRequest;
import com.example.nss.user.api.request.LoginRequest;
import com.example.nss.user.api.request.PasswordResetCompleteRequest;
import com.example.nss.user.api.request.PasswordResetRequest;
import com.example.nss.user.api.response.LoginResponse;
import com.example.nss.user.api.response.MeResponse;
import com.example.nss.user.api.response.UserResponse;
import com.example.nss.user.application.AuthRateLimiter;
import com.example.nss.user.application.RefreshSessionService;
import com.example.nss.user.application.UserService;
import com.example.nss.user.security.EmailNormalizer;
import com.example.nss.user.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final UserService userService;
  private final AuthenticationManager authenticationManager;
  private final UserDetailsService userDetailsService;
  private final JwtService jwtService;

  @Value("${jwt.expiration:3600000}")
  private long accessExpiration;

  @Value("${api.auth.refresh-cookie-name:nss_refresh}")
  private String refreshCookieName;

  @Value("${api.auth.refresh-expiration:604800000}")
  private long refreshExpiration;

  @Value("${api.auth.secure-cookie:false}")
  private boolean secureCookie;

  private final com.example.nss.user.application.FirstAccessService firstAccessService;
  private final RefreshSessionService refreshSessions;
  private final AuthRateLimiter rateLimiter;
  private final CookieCsrfTokenRepository csrfTokenRepository;

  @PostMapping("/first-access")
  public ResponseEntity<Void> firstAccess(@Valid @RequestBody FirstAccessRequest request) {
    firstAccessService.complete(request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/password-reset/request")
  public ResponseEntity<Void> requestPasswordReset(
      @Valid @RequestBody PasswordResetRequest request) {
    rateLimiter.registerPasswordResetRequest(request.email());
    userService.requestPasswordReset(request.email());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/password-reset/complete")
  public ResponseEntity<Void> completePasswordReset(
      @Valid @RequestBody PasswordResetCompleteRequest request) {
    firstAccessService.completePasswordReset(request);
    return ResponseEntity.noContent().build();
  }

  @org.springframework.web.bind.annotation.GetMapping("/me")
  public ResponseEntity<MeResponse> me(Authentication authentication) {
    UserResponse user = userService.getByEmail(authentication.getName());
    boolean admin =
        authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    return ResponseEntity.ok(
        new MeResponse(
            user.email(),
            user.nome(),
            user.ativo(),
            admin ? java.util.List.of("USER_MANAGEMENT") : java.util.List.of()));
  }

  @org.springframework.web.bind.annotation.GetMapping("/csrf")
  public ResponseEntity<String> csrf(
      CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
    csrfTokenRepository.saveToken(token, request, response);
    return ResponseEntity.ok(token.getToken());
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    rateLimiter.registerLoginAttempt(request.email());
    String normalizedEmail = EmailNormalizer.normalize(request.email());
    Authentication authenticationRequest =
        new UsernamePasswordAuthenticationToken(normalizedEmail, request.password());

    Authentication authenticationResult;
    try {
      authenticationResult = authenticationManager.authenticate(authenticationRequest);
    } catch (AuthenticationException exception) {
      throw exception;
    }
    rateLimiter.loginSucceeded(request.email());

    if (!(authenticationResult.getPrincipal() instanceof UserDetails userDetails)) {
      throw new AuthenticationServiceException("A autenticação não retornou um usuário válido");
    }

    com.example.nss.user.domain.User entity = userService.entityByEmail(userDetails.getUsername());
    String token = jwtService.generateToken(entity);
    String refresh = refreshSessions.issue(entity);
    addRefreshCookie(response, refresh);
    UserResponse user = userService.getByEmail(userDetails.getUsername());
    return ResponseEntity.ok(
        new LoginResponse(token, accessExpiration / 1000, user, token, permissions(userDetails)));
  }

  @PostMapping("/refresh")
  public ResponseEntity<LoginResponse> refresh(
      HttpServletRequest request, HttpServletResponse response) {
    String token = cookie(request);
    RefreshSessionService.Rotation rotation = refreshSessions.rotateWithToken(token);
    UserDetails userDetails = userDetailsService.loadUserByUsername(rotation.user().getEmail());
    String access = jwtService.generateToken(rotation.user());
    addRefreshCookie(response, rotation.rawToken());
    return ResponseEntity.ok(
        new LoginResponse(
            access,
            accessExpiration / 1000,
            userService.getByEmail(userDetails.getUsername()),
            access,
            permissions(userDetails)));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    String token = cookie(request);
    refreshSessions.revoke(token);
    clearRefreshCookie(response);
    return ResponseEntity.noContent().build();
  }

  private String cookie(HttpServletRequest request) {
    if (request.getCookies() == null) return null;
    for (jakarta.servlet.http.Cookie c : request.getCookies())
      if (refreshCookieName.equals(c.getName())) return c.getValue();
    return null;
  }

  private java.util.List<String> permissions(UserDetails details) {
    return details.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))
        ? java.util.List.of("USER_MANAGEMENT")
        : java.util.List.of();
  }

  private void addRefreshCookie(HttpServletResponse response, String token) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(refreshCookieName, token)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(Duration.ofMillis(refreshExpiration))
            .build()
            .toString());
  }

  private void clearRefreshCookie(HttpServletResponse response) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(refreshCookieName, "")
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(Duration.ZERO)
            .build()
            .toString());
  }
}
