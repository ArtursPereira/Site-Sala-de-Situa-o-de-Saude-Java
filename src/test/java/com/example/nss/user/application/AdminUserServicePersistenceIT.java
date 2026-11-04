package com.example.nss.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.example.nss.shared.notification.MailService;
import com.example.nss.user.api.request.AccountInvitationRequest;
import com.example.nss.user.domain.AccountType;
import com.example.nss.user.domain.FirstAccessToken;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.FirstAccessTokenRepository;
import com.example.nss.user.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class AdminUserServicePersistenceIT {
  @Autowired AdminUserService service;
  @Autowired UserRepository users;
  @Autowired FirstAccessTokenRepository tokens;

  @MockitoBean MailService mail;

  @Test
  void invitesSearchesAndDeactivatesAUserUsingPersistentState() {
    String email = "persistence-admin@nss.test";

    var invited = service.invite(new AccountInvitationRequest(email, AccountType.USER));

    User persisted = users.findByEmail(email).orElseThrow();
    FirstAccessToken invitation = tokens.findByUserId(persisted.getId()).orElseThrow();
    assertEquals(email, invited.email());
    assertEquals(AccountType.USER, persisted.getAccountType());
    assertEquals("PENDING", invitation.getStatus());
    assertEquals(true, invited.firstAccessPending());

    var page = service.search(email, 0, 10);
    assertEquals(1, page.items().stream().filter(item -> email.equals(item.email())).count());

    var deactivated = service.setActive(persisted.getId(), false, "admin@nss.test");

    assertEquals(false, deactivated.active());
    assertEquals(false, users.findById(persisted.getId()).orElseThrow().isAtivo());
  }

  @Test
  void resetsPasswordByClearingCredentialsAndPersistingAResetInvitation() {
    String email = "persistence-reset@nss.test";
    service.invite(new AccountInvitationRequest(email, AccountType.USER));
    User persisted = users.findByEmail(email).orElseThrow();
    persisted.setPassword("old-password");
    users.save(persisted);

    service.resetPassword(persisted.getId());

    User reset = users.findById(persisted.getId()).orElseThrow();
    FirstAccessToken invitation = tokens.findByUserId(persisted.getId()).orElseThrow();
    assertEquals(null, reset.getPassword());
    assertEquals("PASSWORD_RESET", invitation.getPurpose());
    assertEquals("PENDING", invitation.getStatus());
    verify(mail)
        .sendTextAfterCommit(
            eq(email), eq("Redefinição de senha do NSS"), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void rejectsASecondInvitationForTheSamePersistentEmail() {
    String email = "persistence-duplicate@nss.test";
    service.invite(new AccountInvitationRequest(email, AccountType.USER));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.invite(new AccountInvitationRequest(email.toUpperCase(), AccountType.USER)));

    assertNotNull(users.findByEmail(email).orElse(null));
  }
}
