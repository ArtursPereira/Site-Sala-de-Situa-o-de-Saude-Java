package com.example.nss.analytics.infrastructure.repository;

import java.time.Instant;

public record PublicationMetadataRow(Instant sourceExtractedAt, Instant addedAt) {}
