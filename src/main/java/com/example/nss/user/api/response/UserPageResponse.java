package com.example.nss.user.api.response;

import java.util.List;

public record UserPageResponse(
    List<ManagedUserResponse> items, int page, int size, boolean hasNext, long totalItems) {}
