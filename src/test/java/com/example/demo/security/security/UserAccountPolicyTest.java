package com.example.demo.security.security;

import com.example.demo.entity.AccountSetupStatus;
import com.example.demo.entity.User;
import com.example.demo.exception.AccountDisabledException;
import com.example.demo.service.UserAccountPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserAccountPolicyTest {

    private final UserAccountPolicy policy = new UserAccountPolicy();

    @Test
    void accountActiveAndConfiguredCanAuthenticate() {
        User user = usuario(true, AccountSetupStatus.COMPLETED);

        assertTrue(policy.canAuthenticate(user));
    }
    @Test
    void accountPendingCannotAuthenticate() {
        User user  = usuario(true, AccountSetupStatus.PENDING);

        assertFalse(policy.canAuthenticate(user));
    }

    @Test
    void accountDeactivatedCannotAuthenticate() {
        User user = usuario(false, AccountSetupStatus.COMPLETED);

        assertFalse(policy.canAuthenticate(user));
    }

    @Test
    void accountDeactivatedCannotCompleteInitialAccess() {
        User user = usuario(false, AccountSetupStatus.PENDING);

        assertThrows(
                AccountDisabledException.class,
                () -> policy.requireFirstAccess(user)
        );
    }

    @Test
    void accountDisabledCannotResetPassword() {
        User user = usuario(false, AccountSetupStatus.COMPLETED);

        assertThrows(
                AccountDisabledException.class,
                () -> policy.requirePasswordResetAllowed(user)
        );
    }

    @Test
    void contaConfiguradaNaoPodeRepetirPrimeiroAcesso() {
        User user = usuario(true, AccountSetupStatus.COMPLETED);

        assertThrows(
                IllegalStateException.class,
                () -> policy.requireFirstAccess(user)
        );
    }

    @Test
    void contaPendenteNaoPodeUsarResetComoPrimeiroAcesso() {
        User user = usuario(true, AccountSetupStatus.PENDING);

        assertThrows(
                IllegalStateException.class,
                () -> policy.requirePasswordResetAllowed(user)
        );
    }



    private User usuario(boolean ativo, AccountSetupStatus status) {
        User user = new User();
        user.setAtivo(ativo);
        user.setSetupStatus(status);
        return user;
    }
}
