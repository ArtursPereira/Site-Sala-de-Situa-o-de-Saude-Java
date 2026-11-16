package com.example.nss.post.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdatePostRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        String subtitle,

        @NotNull
        Map<String, Object> content

) {}