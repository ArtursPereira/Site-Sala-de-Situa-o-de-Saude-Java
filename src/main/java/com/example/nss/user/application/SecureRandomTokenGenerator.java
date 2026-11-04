package com.example.nss.user.application;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class SecureRandomTokenGenerator implements TokenGenerator {
  private final SecureRandom random = new SecureRandom();

  @Override
  public void nextBytes(byte[] bytes) {
    random.nextBytes(bytes);
  }
}
