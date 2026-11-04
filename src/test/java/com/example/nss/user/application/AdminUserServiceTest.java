package com.example.nss.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.nss.user.api.mapper.UserMapper;
import com.example.nss.user.api.request.AccountInvitationRequest;
import com.example.nss.user.domain.AccountType;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class AdminUserServiceTest {
  private final UserRepository users = mock(UserRepository.class);
  private final FirstAccessService firstAccess = mock(FirstAccessService.class);
  private final RefreshSessionService refreshSessions = mock(RefreshSessionService.class);
  private final UserMapper mapper = Mappers.getMapper(UserMapper.class);
  private final AdminUserService service =
      new AdminUserService(users, firstAccess, refreshSessions, mapper);

  @Test
  void searchesWithoutTermUsingCappedPageSize() {
    User user = user(1L, "ana@example.com", AccountType.USER);
    when(users.findAll(any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 60), 1));
    when(firstAccess.pending(user)).thenReturn(true);

    var page = service.search("  ", 0, 100);

    assertEquals(1, page.items().size());
    assertEquals(60, page.size());
    verify(users).findAll(any(org.springframework.data.domain.Pageable.class));
  }

  @Test
  void searchesWithTermAndMapsPendingInvitation() {
    User user = user(2L, "bruno@example.com", AccountType.ADMIN);
    when(users.findByNomeContainingIgnoreCaseOrEmailContainingIgnoreCase(
            eq("bruno"), eq("bruno"), any()))
        .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(1, 10), 11));
    when(firstAccess.pending(user)).thenReturn(false);

    var page = service.search("bruno", 1, 10);

    assertEquals("bruno@example.com", page.items().get(0).email());
    assertEquals(1, page.page());
  }

  @Test
  void invitesNormalizedEmailAndIssuesFirstAccess() {
    AccountInvitationRequest request =
        new AccountInvitationRequest("  NEW@Example.COM ", AccountType.ADMIN);
    User saved = user(3L, "new@example.com", AccountType.ADMIN);
    when(users.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(firstAccess.pending(saved)).thenReturn(false);

    var response = service.invite(request);

    assertEquals("new@example.com", response.email());
    var captor = org.mockito.ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(users).save(captor.capture());
    User invited = captor.getValue();
    assertEquals("new@example.com", invited.getEmail());
    assertEquals(AccountType.ADMIN, invited.getAccountType());
    assertEquals(true, invited.isAtivo());
    org.junit.jupiter.api.Assertions.assertNotNull(invited.getCreatedAt());
    org.junit.jupiter.api.Assertions.assertNotNull(invited.getUpdatedAt());
    verify(firstAccess).issue(invited);
  }

  @Test
  void rejectsDuplicateInvitation() {
    User existing = user(4L, "existing@example.com", AccountType.USER);
    when(users.findByEmailIgnoreCase("existing@example.com")).thenReturn(Optional.of(existing));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.invite(new AccountInvitationRequest("existing@example.com", AccountType.USER)));
  }

  @Test
  void deactivatesUserAndRevokesSessions() {
    User user = user(5L, "user@example.com", AccountType.USER);
    when(users.findById(5L)).thenReturn(Optional.of(user));
    when(users.save(user)).thenReturn(user);
    when(firstAccess.pending(user)).thenReturn(false);

    var response = service.setActive(5L, false, "admin@example.com");

    assertEquals(false, user.isAtivo());
    assertEquals(false, response.active());
    verify(refreshSessions).revokeAllForUser(5L);
  }

  @Test
  void protectsActorAndOtherAdminsFromDeactivation() {
    User actor = user(6L, "admin@example.com", AccountType.ADMIN);
    when(users.findById(6L)).thenReturn(Optional.of(actor));
    when(users.save(actor)).thenReturn(actor);
    assertThrows(
        IllegalArgumentException.class, () -> service.setActive(6L, false, actor.getEmail()));
    assertEquals(true, service.setActive(6L, true, actor.getEmail()).active());

    User otherAdmin = user(7L, "other@example.com", AccountType.ADMIN);
    when(users.findById(7L)).thenReturn(Optional.of(otherAdmin));
    assertThrows(
        IllegalArgumentException.class, () -> service.setActive(7L, false, "admin@example.com"));
  }

  @Test
  void resetsPasswordAndDelegatesInvitationOperations() {
    User user = user(8L, "reset@example.com", AccountType.USER);
    user.setPassword("old");
    user.setPasswordCreatedAt(java.time.Instant.now());
    when(users.findById(8L)).thenReturn(Optional.of(user));
    when(users.save(user)).thenReturn(user);

    service.resetPassword(8L);

    assertEquals(null, user.getPassword());
    assertEquals(null, user.getPasswordCreatedAt());
    verify(refreshSessions).revokeAllForUser(8L);
    verify(firstAccess).issuePasswordReset(user);
  }

  @Test
  void rejectsOperationsForUnknownUsers() {
    when(users.findById(99L)).thenReturn(Optional.empty());

    assertThrows(
        IllegalArgumentException.class, () -> service.setActive(99L, false, "admin@example.com"));
    assertThrows(IllegalArgumentException.class, () -> service.resetPassword(99L));
  }

  @Test
  void delegatesInvitationListingRenewalAndCancellation() {
    var invitations =
        List.of(
            new com.example.nss.user.api.response.InvitationResponse(
                1L, 2L, "a@nss.test", AccountType.USER, "PENDING", null, null));
    var renewed = invitations.getFirst();
    when(firstAccess.pendingInvitations()).thenReturn(invitations);
    when(firstAccess.renew(2L)).thenReturn(renewed);

    assertEquals(invitations, service.invitations());
    assertEquals(renewed, service.renewInvitation(2L));
    service.cancelInvitation(2L);

    verify(firstAccess).cancel(2L);
  }

  private static User user(Long id, String email, AccountType type) {
    User user = new User();
    user.setId(id);
    user.setEmail(email);
    user.setNome(email.substring(0, email.indexOf('@')));
    user.setAccountType(type);
    return user;
  }
}
