package com.example.nss.user.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "nome", length = 100)
  private String nome;

  @Column(name = "email", nullable = false, unique = true, length = 254)
  private String email;

  @Column(name = "password", length = 255)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_type", nullable = false, length = 10)
  private AccountType accountType = AccountType.USER;

  @Column(name = "ativo", nullable = false)
  private boolean ativo = true;

  @Column(name = "created_at", nullable = false)
  private java.time.Instant createdAt = java.time.Instant.now();

  @Column(name = "updated_at", nullable = false)
  private java.time.Instant updatedAt = java.time.Instant.now();

  @Column(name = "password_created_at")
  private java.time.Instant passwordCreatedAt;

  @PreUpdate
  void touch() {
    updatedAt = java.time.Instant.now();
  }
}
