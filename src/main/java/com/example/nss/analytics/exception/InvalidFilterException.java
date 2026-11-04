package com.example.nss.analytics.exception;

public class InvalidFilterException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public InvalidFilterException(String message) {
    super(message);
  }
}
