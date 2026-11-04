package com.example.nss.user.api.request;

import com.example.nss.user.domain.AccountType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record AccountInvitationRequest(
    @Email @jakarta.validation.constraints.NotBlank String email,
    @NotNull AccountType accountType) {}
