package com.example.nss.user.exception;

public class AuthStoreUnavailableException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public AuthStoreUnavailableException(Throwable cause) {
    super("Armazenamento de autenticação indisponível", cause);
  }
}
