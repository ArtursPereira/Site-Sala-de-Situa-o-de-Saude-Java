package com.example.nss.user.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirstAccessRequest(
    @Email @NotBlank String email,
    @NotBlank @Size(min = 3, max = 100) String name,
    @NotBlank @Size(min = 12, max = 128) String password,
    @NotBlank String token) {}
