package com.example.nss.user.application;

import com.example.nss.user.api.mapper.UserMapper;
import com.example.nss.user.api.request.AccountInvitationRequest;
import com.example.nss.user.api.response.ManagedUserResponse;
import com.example.nss.user.api.response.UserPageResponse;
import com.example.nss.user.domain.AccountType;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.UserRepository;
import com.example.nss.user.security.EmailNormalizer;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {
  private final UserRepository users;
  private final FirstAccessService firstAccess;
  private final RefreshSessionService refreshSessions;
  private final UserMapper mapper;

  public UserPageResponse search(String search, int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), 60);
    org.springframework.data.domain.Page<User> result =
        search == null || search.isBlank()
            ? users.findAll(
                org.springframework.data.domain.PageRequest.of(
                    page, safeSize, org.springframework.data.domain.Sort.by("email").ascending()))
            : users.findByNomeContainingIgnoreCaseOrEmailContainingIgnoreCase(
                search,
                search,
                org.springframework.data.domain.PageRequest.of(
                    page, safeSize, org.springframework.data.domain.Sort.by("email").ascending()));
    return new UserPageResponse(
        result.getContent().stream().map(this::view).toList(),
        result.getNumber(),
        result.getSize(),
        result.hasNext(),
        result.getTotalElements());
  }

  @Transactional
  public ManagedUserResponse invite(AccountInvitationRequest request) {
    String normalizedEmail = EmailNormalizer.normalize(request.email());
    if (users.findByEmailIgnoreCase(normalizedEmail).isPresent())
      throw new IllegalArgumentException("E-mail já cadastrado");
    User user = new User();
    user.setEmail(normalizedEmail);
    user.setAccountType(request.accountType());
    user.setAtivo(true);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    User saved;
    try {
      saved = users.save(user);
    } catch (DataIntegrityViolationException exception) {
      throw new IllegalArgumentException("E-mail já cadastrado", exception);
    }
    firstAccess.issue(saved);
    return view(saved);
  }

  @Transactional
  public ManagedUserResponse setActive(Long id, boolean active, String actorEmail) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
    if (user.getEmail().equalsIgnoreCase(actorEmail) && !active)
      throw new IllegalArgumentException("Você não pode desativar sua própria conta");
    if (!active && user.getAccountType() == AccountType.ADMIN)
      throw new IllegalArgumentException("Admin não pode desativar outro admin");
    user.setAtivo(active);
    User saved = users.save(user);
    if (!active) refreshSessions.revokeAllForUser(saved.getId());
    return view(saved);
  }

  @Transactional
  public void resetPassword(Long id) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
    user.setPassword(null);
    user.setPasswordCreatedAt(null);
    users.save(user);
    refreshSessions.revokeAllForUser(user.getId());
    firstAccess.issuePasswordReset(user);
  }

  public java.util.List<com.example.nss.user.api.response.InvitationResponse> invitations() {
    return firstAccess.pendingInvitations();
  }

  public com.example.nss.user.api.response.InvitationResponse renewInvitation(Long id) {
    return firstAccess.renew(id);
  }

  public void cancelInvitation(Long id) {
    firstAccess.cancel(id);
  }

  private ManagedUserResponse view(User user) {
    return mapper.toManagedResponse(user, firstAccess.pending(user));
  }
}
