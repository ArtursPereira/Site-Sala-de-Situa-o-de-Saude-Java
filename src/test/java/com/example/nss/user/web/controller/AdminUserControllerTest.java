package com.example.nss.user.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.nss.user.api.request.AccountInvitationRequest;
import com.example.nss.user.api.request.ActivationRequest;
import com.example.nss.user.api.response.InvitationResponse;
import com.example.nss.user.api.response.ManagedUserResponse;
import com.example.nss.user.api.response.UserPageResponse;
import com.example.nss.user.application.AdminUserService;
import com.example.nss.user.domain.AccountType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {
  @Mock AdminUserService service;
  @Mock Authentication authentication;
  @InjectMocks AdminUserController controller;

  @Test
  void searchesWithValidatedPagination() {
    UserPageResponse page = new UserPageResponse(List.of(), 0, 10, false, 0);
    when(service.search("ana", 0, 10)).thenReturn(page);

    assertEquals(page, controller.search("ana", 0, 10));
    verify(service).search("ana", 0, 10);
  }

  @Test
  void rejectsInvalidPaginationBeforeCallingService() {
    assertThrows(IllegalArgumentException.class, () -> controller.search(null, -1, 10));
    assertThrows(IllegalArgumentException.class, () -> controller.search(null, 0, 61));
  }

  @Test
  void createsInvitationWithCreatedStatus() {
    AccountInvitationRequest request =
        new AccountInvitationRequest("ana@example.com", AccountType.USER);
    ManagedUserResponse response =
        new ManagedUserResponse(1L, request.email(), "Ana", AccountType.USER, true, true, null);
    when(service.invite(request)).thenReturn(response);

    var result = controller.invite(request);

    assertEquals(HttpStatus.CREATED, result.getStatusCode());
    assertEquals(response, result.getBody());
  }

  @Test
  void delegatesActivationWithAuthenticatedActor() {
    ActivationRequest request = new ActivationRequest(false);
    ManagedUserResponse response =
        new ManagedUserResponse(1L, "ana@example.com", "Ana", AccountType.USER, false, false, null);
    when(authentication.getName()).thenReturn("admin@example.com");
    when(service.setActive(1L, false, "admin@example.com")).thenReturn(response);

    assertEquals(response, controller.activation(1L, request, authentication));
  }

  @Test
  void delegatesResetAndInvitationOperations() {
    InvitationResponse invitation =
        new InvitationResponse(2L, 1L, "ana@example.com", AccountType.USER, "PENDING", null, null);
    when(service.invitations()).thenReturn(List.of(invitation));
    when(service.renewInvitation(2L)).thenReturn(invitation);

    controller.reset(1L);
    controller.cancel(2L);

    assertEquals(List.of(invitation), controller.invitations());
    assertEquals(invitation, controller.renew(2L));
    verify(service).resetPassword(1L);
    verify(service).cancelInvitation(2L);
  }
}
