package com.example.nss.user.api.response;

import java.util.List;

public record MeResponse(String email, String name, boolean active, List<String> permissions) {}
