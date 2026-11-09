package com.example.nss.user.exception;

public class RateLimitExceededException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final long retryDelaySeconds;

  public RateLimitExceededException() {
    this(60);
  }

  public RateLimitExceededException(long retryAfterSeconds) {
    super("Muitas tentativas. Aguarde alguns minutos antes de tentar novamente.");
    this.retryDelaySeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryDelaySeconds;
  }
}
