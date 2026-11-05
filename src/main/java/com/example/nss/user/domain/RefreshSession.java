package com.example.nss.user.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RefreshSession {
  private UUID id;

  private User user;

  private String tokenHash;

  private Instant createdAt;

  private Instant expiresAt;

  private Instant revokedAt;

  private UUID replacedBySessionId;
}
