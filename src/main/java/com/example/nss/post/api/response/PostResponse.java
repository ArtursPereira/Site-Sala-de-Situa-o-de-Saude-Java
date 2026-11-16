package com.example.nss.post.api.response;

import com.example.nss.post.domain.NewsStatus;

import java.time.Instant;
import java.util.Map;

public record PostResponse(
        Long id,
        String title,
        String subtitle,
        String slug,
        Map<String, Object> content,
        Long authorId,
        String authorName,
        NewsStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt
) {}
