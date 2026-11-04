package com.example.nss.user.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.nss.user.domain.AccountType;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class CustomUserDetailsServiceTest {
  @Test
  void loadsNormalizedActiveUserWithRole() {
    UserRepository repository = mock(UserRepository.class);
    CustomUserDetailsService service = new CustomUserDetailsService(repository);
    User user = new User();
    user.setEmail("ana@example.com");
    user.setPassword("encoded");
    user.setAccountType(AccountType.ADMIN);
    user.setAtivo(true);
    when(repository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user));

    UserDetails details = service.loadUserByUsername(" Ana@Example.COM ");

    assertEquals("ana@example.com", details.getUsername());
    assertEquals("encoded", details.getPassword());
    assertEquals("ROLE_ADMIN", details.getAuthorities().iterator().next().getAuthority());
    org.mockito.Mockito.verify(repository).findByEmailIgnoreCase("ana@example.com");
  }

  @Test
  void rejectsAUserWithoutConfiguredPassword() {
    UserRepository repository = mock(UserRepository.class);
    CustomUserDetailsService service = new CustomUserDetailsService(repository);
    User user = new User();
    user.setEmail("ana@example.com");
    when(repository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user));

    assertThrows(
        UsernameNotFoundException.class, () -> service.loadUserByUsername("ana@example.com"));
  }

  @Test
  void rejectsAnUnknownUser() {
    UserRepository repository = mock(UserRepository.class);
    CustomUserDetailsService service = new CustomUserDetailsService(repository);
    when(repository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

    assertThrows(
        UsernameNotFoundException.class, () -> service.loadUserByUsername("missing@example.com"));
  }
}
