package com.example.nss.user.infrastructure.repository;

import com.example.nss.user.domain.FirstAccessToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FirstAccessTokenRepository extends JpaRepository<FirstAccessToken, Long> {
  Optional<FirstAccessToken> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select token from FirstAccessToken token join fetch token.user where token.tokenHash = :tokenHash")
  Optional<FirstAccessToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  Optional<FirstAccessToken> findByUserId(Long userId);

  java.util.List<FirstAccessToken> findByStatusOrderByExpiresAtAsc(String status);
}
