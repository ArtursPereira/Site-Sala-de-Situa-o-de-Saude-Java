package com.example.nss.user.security;

import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

    User user =
        userRepository
            .findByEmailIgnoreCase(EmailNormalizer.normalize(email))
            .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

    return toUserDetails(user);
  }

  public UserDetails loadUserById(Long id) throws UsernameNotFoundException {
    return toUserDetails(
        userRepository
            .findById(id)
            .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado")));
  }

  private UserDetails toUserDetails(User user) {
    if (user.getPassword() == null)
      throw new UsernameNotFoundException("Conta ainda não configurada");
    return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
        .password(user.getPassword())
        .roles(user.getAccountType().name())
        .disabled(!user.isAtivo())
        .build();
  }
}
