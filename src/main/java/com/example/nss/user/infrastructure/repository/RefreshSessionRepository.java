package com.example.nss.user.infrastructure.repository;

import com.example.nss.user.domain.RefreshSession;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

/** Legacy test seam retained while refresh persistence lives in Redis. */
public interface RefreshSessionRepository {
  RefreshSession save(RefreshSession session);

  Optional<RefreshSession> findByTokenHashForUpdate(String tokenHash);

  int revokeActiveByUserId(Long userId, Instant revokedAt);

  List<RefreshSession> findAll();

  RefreshSession saveAndFlush(RefreshSession session);
}
