package com.example.nss.user.application;

import com.example.nss.shared.notification.MailService;
import com.example.nss.user.api.request.FirstAccessRequest;
import com.example.nss.user.domain.FirstAccessToken;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.FirstAccessTokenRepository;
import com.example.nss.user.infrastructure.repository.UserRepository;
import com.example.nss.user.security.EmailNormalizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FirstAccessService {
  private static final String PENDING_STATUS = "PENDING";
  private final UserRepository users;
  private final FirstAccessTokenRepository tokens;
  private final PasswordEncoder encoder;
  private final MailService mail;
  private final RefreshSessionService refreshSessions;

  @Value("${api.first-access.frontend-url:http://localhost:5173}")
  private String frontendUrl;

  @Value("${api.first-access.expiration:86400}")
  private long expirationSeconds;

  private final TokenGenerator tokenGenerator;

  @Autowired
  public FirstAccessService(
      UserRepository users,
      FirstAccessTokenRepository tokens,
      PasswordEncoder encoder,
      MailService mail,
      RefreshSessionService refreshSessions,
      TokenGenerator tokenGenerator) {
    this.users = users;
    this.tokens = tokens;
    this.encoder = encoder;
    this.mail = mail;
    this.refreshSessions = refreshSessions;
    this.tokenGenerator = tokenGenerator;
  }

  public FirstAccessService(
      UserRepository users,
      FirstAccessTokenRepository tokens,
      PasswordEncoder encoder,
      MailService mail,
      RefreshSessionService refreshSessions) {
    this(users, tokens, encoder, mail, refreshSessions, new SecureRandomTokenGenerator());
  }

  @Transactional
  public void issue(User user) {
    tokens.findByUserId(user.getId()).ifPresent(this::deleteExistingToken);
    byte[] bytes = new byte[32];
    tokenGenerator.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    FirstAccessToken token = new FirstAccessToken();
    token.setUser(user);
    token.setTokenHash(hash(raw));
    token.setStatus(PENDING_STATUS);
    token.setPurpose("FIRST_ACCESS");
    token.setSentAt(Instant.now());
    token.setExpiresAt(Instant.now().plus(Duration.ofSeconds(expirationSeconds)));
    tokens.save(token);
    String link = frontendUrl.replaceAll("/$", "") + "/primeiro-acesso?token=" + raw;
    mail.sendTextAfterCommit(
        user.getEmail(),
        "Convite de primeiro acesso ao NSS",
        "Acesse o NSS pelo link abaixo para criar seu nome e senha:\n\n"
            + link
            + "\n\nEste convite expira em 24 horas e só pode ser usado uma vez.");
  }

  @Transactional
  public void issuePasswordReset(User user) {
    tokens.findByUserId(user.getId()).ifPresent(this::deleteExistingToken);
    byte[] bytes = new byte[32];
    tokenGenerator.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    FirstAccessToken token = new FirstAccessToken();
    token.setUser(user);
    token.setTokenHash(hash(raw));
    token.setStatus(PENDING_STATUS);
    token.setPurpose("PASSWORD_RESET");
    token.setSentAt(Instant.now());
    token.setExpiresAt(Instant.now().plus(Duration.ofSeconds(expirationSeconds)));
    tokens.save(token);
    String link = frontendUrl.replaceAll("/$", "") + "/redefinir-senha?token=" + raw;
    mail.sendTextAfterCommit(
        user.getEmail(),
        "Redefinição de senha do NSS",
        "Use o link abaixo para criar uma nova senha:\n\n"
            + link
            + "\n\nEste link expira em 7 dias e só pode ser usado uma vez.");
  }

  @Transactional
  public void completePasswordReset(
      com.example.nss.user.api.request.PasswordResetCompleteRequest request) {
    FirstAccessToken invite =
        tokens
            .findByTokenHashForUpdate(hash(request.token()))
            .filter(
                t ->
                    PENDING_STATUS.equals(t.getStatus())
                        && "PASSWORD_RESET".equals(t.getPurpose())
                        && t.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new IllegalArgumentException("Link inválido ou expirado"));
    User user = invite.getUser();
    if (!user.getEmail().equals(EmailNormalizer.normalize(request.email())))
      throw new IllegalArgumentException("E-mail não corresponde ao link");
    user.setPassword(encoder.encode(request.password()));
    user.setPasswordCreatedAt(Instant.now());
    refreshSessions.revokeAllForUser(user.getId());
    users.save(user);
    invite.setStatus("COMPLETED");
    invite.setCompletedAt(Instant.now());
    invite.setPasswordCreatedAt(Instant.now());
    tokens.save(invite);
  }

  @Transactional
  public void complete(FirstAccessRequest request) {
    FirstAccessToken invite =
        tokens
            .findByTokenHashForUpdate(hash(request.token()))
            .filter(
                t ->
                    PENDING_STATUS.equals(t.getStatus())
                        && "FIRST_ACCESS".equals(t.getPurpose())
                        && t.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new IllegalArgumentException("Convite inválido ou expirado"));
    User user = invite.getUser();
    if (!user.getEmail().equals(EmailNormalizer.normalize(request.email())))
      throw new IllegalArgumentException("E-mail não corresponde ao convite");
    user.setNome(request.name().trim());
    user.setPassword(encoder.encode(request.password()));
    user.setPasswordCreatedAt(Instant.now());
    users.save(user);
    invite.setStatus("COMPLETED");
    invite.setCompletedAt(Instant.now());
    invite.setPasswordCreatedAt(Instant.now());
    tokens.save(invite);
  }

  public boolean pending(User user) {
    return tokens
        .findByUserId(user.getId())
        .map(t -> PENDING_STATUS.equals(t.getStatus()) && t.getExpiresAt().isAfter(Instant.now()))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public java.util.List<com.example.nss.user.api.response.InvitationResponse> pendingInvitations() {
    return tokens.findByStatusOrderByExpiresAtAsc(PENDING_STATUS).stream().map(this::view).toList();
  }

  @Transactional
  public com.example.nss.user.api.response.InvitationResponse renew(Long id) {
    FirstAccessToken old =
        tokens
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));
    if (!PENDING_STATUS.equals(old.getStatus()))
      throw new IllegalArgumentException("Convite não está pendente");
    old.setStatus("CANCELLED");
    tokens.save(old);
    issue(old.getUser());
    return tokens.findByUserId(old.getUser().getId()).map(this::view).orElseThrow();
  }

  @Transactional
  public void cancel(Long id) {
    FirstAccessToken token =
        tokens
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));
    if (PENDING_STATUS.equals(token.getStatus())) {
      token.setStatus("CANCELLED");
      tokens.save(token);
    }
  }

  private com.example.nss.user.api.response.InvitationResponse view(FirstAccessToken t) {
    return new com.example.nss.user.api.response.InvitationResponse(
        t.getId(),
        t.getUser().getId(),
        t.getUser().getEmail(),
        t.getUser().getAccountType(),
        t.getStatus(),
        t.getSentAt(),
        t.getExpiresAt());
  }

  private void deleteExistingToken(FirstAccessToken token) {
    tokens.delete(token);
    tokens.flush();
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
