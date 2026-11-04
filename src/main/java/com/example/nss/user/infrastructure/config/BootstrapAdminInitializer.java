package com.example.nss.user.infrastructure.config;

import com.example.nss.user.domain.AccountType;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.UserRepository;
import com.example.nss.user.security.EmailNormalizer;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements CommandLineRunner {
  private final UserRepository users;
  private final PasswordEncoder encoder;

  @Value("${api.bootstrap-admin.email:}")
  private String email;

  @Value("${api.bootstrap-admin.password:}")
  private String password;

  @Override
  public void run(String... args) {
    String normalizedEmail = EmailNormalizer.normalize(email);
    if (normalizedEmail == null
        || normalizedEmail.isBlank()
        || password == null
        || password.isBlank()
        || users.findByEmailIgnoreCase(normalizedEmail).isPresent()) return;
    User admin = new User();
    admin.setEmail(normalizedEmail);
    admin.setNome("Administrador NSS");
    admin.setPassword(encoder.encode(password));
    admin.setPasswordCreatedAt(Instant.now());
    admin.setAccountType(AccountType.ADMIN);
    admin.setAtivo(true);
    admin.setCreatedAt(Instant.now());
    admin.setUpdatedAt(Instant.now());
    users.save(admin);
  }
}
