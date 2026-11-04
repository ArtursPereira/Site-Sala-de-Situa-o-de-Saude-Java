package com.example.nss.user.api.request;

import jakarta.validation.constraints.NotNull;

public record ActivationRequest(@NotNull Boolean active) {}
