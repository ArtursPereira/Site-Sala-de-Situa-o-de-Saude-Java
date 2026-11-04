package com.example.nss.user.security;

import com.example.nss.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey signingKey;
  private final long expiration;

  public JwtService(
      @Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expiration) {
    try {
      this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    } catch (DecodingException exception) {
      throw new IllegalStateException(
          "JWT_SECRET deve ser uma chave Base64 válida; gere-a com `openssl rand -base64 64`",
          exception);
    }
    this.expiration = expiration;
  }

  public String generateToken(User user) {
    Date issuedAt = new Date();
    Date expirationDate = new Date(issuedAt.getTime() + expiration);

    return Jwts.builder()
        .subject(String.valueOf(user.getId()))
        .issuedAt(issuedAt)
        .expiration(expirationDate)
        .signWith(signingKey)
        .compact();
  }

  public long extractUserId(String token) {
    return Long.parseLong(extractClaim(token, Claims::getSubject));
  }

  public boolean isTokenValid(String token, long userId, boolean enabled) {
    long tokenUserId = extractUserId(token);

    return tokenUserId == userId && enabled && !isTokenExpired(token);
  }

  private boolean isTokenExpired(String token) {
    return extractExpiration(token).before(new Date());
  }

  private Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  private <T> T extractClaim(String token, Function<Claims, T> resolver) {
    Claims claims =
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();

    return resolver.apply(claims);
  }
}
