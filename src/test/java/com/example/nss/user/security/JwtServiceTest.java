package com.example.nss.user.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.nss.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

class JwtServiceTest {
  private final JwtService jwt =
      new JwtService("ZmFrZS10ZXN0LXNlY3JldC1sb25nLWVub3VnaC1mb3ItaHMyNTYtMzI=", 60_000);

  @Test
  void acceptsAnEnabledUserBoundToTheToken() {
    User entity = entity(42L);
    UserDetails details = details("user@nss.test", false);
    String token = jwt.generateToken(entity);

    assertEquals(42L, jwt.extractUserId(token));
    assertTrue(jwt.isTokenValid(token, entity.getId(), details.isEnabled()));
  }

  @Test
  void rejectsAPreviouslyIssuedTokenWhenTheUserIsDisabled() {
    User entity = entity(42L);
    UserDetails disabled = details("user@nss.test", true);

    assertFalse(jwt.isTokenValid(jwt.generateToken(entity), entity.getId(), disabled.isEnabled()));
  }

  @Test
  void doesNotUseTheEmailAsTheJwtIdentity() {
    String token = jwt.generateToken(entity(42L));

    assertEquals(42L, jwt.extractUserId(token));
  }

  @Test
  void rejectsASecretThatIsNotBase64() {
    assertThrows(IllegalStateException.class, () -> new JwtService("not-a-base64-secret", 60_000));
  }

  private User entity(long id) {
    User user = new User();
    user.setId(id);
    user.setEmail("user@nss.test");
    user.setAtivo(true);
    return user;
  }

  private UserDetails details(String email, boolean disabled) {
    return org.springframework.security.core.userdetails.User.withUsername(email)
        .password("ignored")
        .roles("USER")
        .disabled(disabled)
        .build();
  }
}
