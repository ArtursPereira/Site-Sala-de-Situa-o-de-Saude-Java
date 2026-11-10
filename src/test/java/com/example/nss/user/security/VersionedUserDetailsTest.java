package com.example.nss.user.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.nss.user.domain.AccountType;
import com.example.nss.user.domain.User;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VersionedUserDetailsTest {
  @ParameterizedTest
  @CsvSource({"ADMIN, ROLE_ADMIN", "EDITOR, ROLE_EDITOR", "USER, ROLE_USER"})
  void mapsEachAccountTypeToExactlyOneRole(AccountType accountType, String expectedAuthority) {
    User user = persistedUser(accountType);

    VersionedUserDetails details = new VersionedUserDetails(user);

    Set<String> authorities =
        details.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .collect(Collectors.toSet());
    assertEquals(Set.of(expectedAuthority), authorities);
    if (accountType == AccountType.EDITOR) {
      assertFalse(authorities.contains("ROLE_ADMIN"));
    }
  }

  private static User persistedUser(AccountType accountType) {
    User user = new User();
    user.setId(10L);
    user.setAccountVersion(2L);
    user.setEmail(accountType.name().toLowerCase() + "@nss.test");
    user.setPassword("encoded-password");
    user.setAccountType(accountType);
    user.setAtivo(true);
    return user;
  }
}
