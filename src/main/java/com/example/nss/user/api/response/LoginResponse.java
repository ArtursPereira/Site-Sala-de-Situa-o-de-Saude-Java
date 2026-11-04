package com.example.nss.user.api.response;

public record LoginResponse(
    String accessToken,
    long expiresIn,
    UserResponse user,
    String token,
    java.util.List<String> permissions) {
  public LoginResponse(String accessToken, long expiresIn, UserResponse user) {
    this(accessToken, expiresIn, user, accessToken, java.util.List.of());
  }
}
