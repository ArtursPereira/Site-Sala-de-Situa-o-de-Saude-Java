package com.example.nss.user.security;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  @Value("${api.cors.allowed-origins:}")
  private String allowedOrigins;

  @Value("${api.auth.secure-cookie:false}")
  private boolean secureCookie;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  CookieCsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieCustomizer(
        cookie -> cookie.secure(secureCookie).sameSite("Strict").path("/"));
    return repository;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository())
                    .ignoringRequestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/first-access",
                        "/api/v1/auth/password-reset/request",
                        "/api/v1/auth/password-reset/complete",
                        // Admin mutations authenticate with the short-lived
                        // bearer token, not a browser cookie. They are not
                        // CSRF-sensitive and must remain usable by the SPA.
                        "/api/v1/admin/**"))
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/first-access",
                        "/api/v1/auth/password-reset/request",
                        "/api/v1/auth/password-reset/complete")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/auth/csrf",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (request, response, exception) -> response.sendError(401))
                    .accessDeniedHandler((request, response, exception) -> response.sendError(403)))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    if (!allowedOrigins.isBlank())
      config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
    config.setAllowedMethods(java.util.List.of("GET", "POST", "PATCH", "OPTIONS"));
    config.setAllowedHeaders(
        java.util.List.of("Authorization", "Content-Type", "X-Request-Id", "X-XSRF-TOKEN"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
