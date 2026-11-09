package com.example.nss.user.security;

import com.example.nss.user.domain.User;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Includes the account revision already loaded with the user's authorities. */
public final class VersionedUserDetails extends org.springframework.security.core.userdetails.User {
  private static final long serialVersionUID = 1L;
  private final long revisionValue;
  private final long userId;

  public VersionedUserDetails(User user) {
    super(
        user.getEmail(),
        user.getPassword(),
        user.isAtivo(),
        true,
        true,
        true,
        List.of(new SimpleGrantedAuthority("ROLE_" + user.getAccountType().name())));
    revisionValue = revision(user);
    userId = user.getId();
  }

  public static long revision(User user) {
    if (user.getAccountVersion() == null) {
      throw new IllegalStateException("Conta ainda não foi persistida");
    }
    return user.getAccountVersion();
  }

  public long accountId() {
    return userId;
  }

  public long accountRevision() {
    return revisionValue;
  }
}
