package com.example.nss.user.infrastructure.repository;

import com.example.nss.user.domain.RefreshSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select session from RefreshSession session join fetch session.user where session.tokenHash = :tokenHash")
  Optional<RefreshSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  @Query(
      "update RefreshSession session set session.revokedAt = :revokedAt where session.user.id = :userId and session.revokedAt is null")
  @org.springframework.data.jpa.repository.Modifying
  int revokeActiveByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);
}
