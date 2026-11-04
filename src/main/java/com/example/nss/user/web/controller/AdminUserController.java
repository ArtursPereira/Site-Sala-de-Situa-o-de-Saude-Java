package com.example.nss.user.web.controller;

import com.example.nss.user.api.request.AccountInvitationRequest;
import com.example.nss.user.api.request.ActivationRequest;
import com.example.nss.user.api.response.InvitationResponse;
import com.example.nss.user.api.response.ManagedUserResponse;
import com.example.nss.user.api.response.UserPageResponse;
import com.example.nss.user.application.AdminUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
  private final AdminUserService service;

  @GetMapping
  public UserPageResponse search(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "60") int size) {
    if (page < 0 || size < 1 || size > 60) {
      throw new IllegalArgumentException("page deve ser >= 0 e size deve estar entre 1 e 60");
    }
    return service.search(search, page, size);
  }

  @PostMapping
  public ResponseEntity<ManagedUserResponse> invite(
      @Valid @RequestBody AccountInvitationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.invite(request));
  }

  @PatchMapping("/{id}/activation")
  public ManagedUserResponse activation(
      @PathVariable Long id, @Valid @RequestBody ActivationRequest request, Authentication auth) {
    return service.setActive(id, request.active(), auth.getName());
  }

  @PostMapping("/{id}/password-reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reset(@PathVariable Long id) {
    service.resetPassword(id);
  }

  @GetMapping("/invitations")
  public List<InvitationResponse> invitations() {
    return service.invitations();
  }

  @PostMapping("/invitations/{id}/renew")
  public InvitationResponse renew(@PathVariable Long id) {
    return service.renewInvitation(id);
  }

  @PostMapping("/invitations/{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@PathVariable Long id) {
    service.cancelInvitation(id);
  }
}
