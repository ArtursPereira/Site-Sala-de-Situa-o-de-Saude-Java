package com.example.nss.user.api.response;

import com.example.nss.user.domain.AccountType;
import java.time.Instant;

public record ManagedUserResponse(
    Long id,
    String email,
    String name,
    AccountType accountType,
    boolean active,
    boolean firstAccessPending,
    Instant passwordCreatedAt) {}
