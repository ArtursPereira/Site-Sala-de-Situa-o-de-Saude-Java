package com.example.nss.user.application;

@FunctionalInterface
public interface TokenGenerator {
  void nextBytes(byte[] bytes);
}
