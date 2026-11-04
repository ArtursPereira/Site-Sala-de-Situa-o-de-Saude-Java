package com.example.nss.shared.web;

import com.example.nss.analytics.exception.InvalidFilterException;
import com.example.nss.user.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Dados de entrada inválidos", request);
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleBadCredentials(HttpServletRequest request) {
    return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Credenciais inválidas", request);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(HttpServletRequest request) {
    return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Credenciais inválidas", request);
  }

  @ExceptionHandler(AuthenticationServiceException.class)
  public ResponseEntity<ErrorResponse> handleAuthenticationService(HttpServletRequest request) {
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "AUTHENTICATION_ERROR",
        "Não foi possível realizar o login",
        request);
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<ErrorResponse> handleRateLimit(HttpServletRequest request) {
    return error(
        HttpStatus.TOO_MANY_REQUESTS,
        "RATE_LIMITED",
        "Muitas tentativas. Aguarde alguns minutos antes de tentar novamente.",
        request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleConflict(HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, "CONFLICT", "Recurso já existente", request);
  }

  @ExceptionHandler(InvalidFilterException.class)
  public ResponseEntity<ErrorResponse> handleFilter(
      InvalidFilterException e, HttpServletRequest r) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_FILTER", e.getMessage(), r);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleArgument(
      IllegalArgumentException e, HttpServletRequest r) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage(), r);
  }

  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  public ResponseEntity<ErrorResponse> handleNotFound(Exception e, HttpServletRequest r) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso não encontrado", r);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleOther(Exception e, HttpServletRequest r) {
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "Erro interno ao processar a solicitação",
        r);
  }

  private ResponseEntity<ErrorResponse> error(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(
            new ErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                UUID.randomUUID().toString()));
  }
}
