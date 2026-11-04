package com.example.nss.user.exception;

public class RateLimitExceededException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public RateLimitExceededException() {
    super("Muitas tentativas. Aguarde alguns minutos antes de tentar novamente.");
  }
}
