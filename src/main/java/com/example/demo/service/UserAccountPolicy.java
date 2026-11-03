package com.example.demo.service;


import com.example.demo.entity.AccountSetupStatus;
import com.example.demo.entity.User;
import com.example.demo.exception.AccountDisabledException;
import org.springframework.stereotype.Service;

@Service
public class UserAccountPolicy {

    public boolean canAuthenticate(User user) {
        return user.isAtivo()
                && user.getSetupStatus() == AccountSetupStatus.COMPLETED;
    }

    public void requireFirstAccess(User user) {
        requireActiveAccount(user);

        if (user.getSetupStatus() != AccountSetupStatus.PENDING) {
            throw new IllegalStateException(
                    "Primeiro acesso já concluído"
            );
        }
    }

    public void requirePasswordResetAllowed(User user) {
        requireActiveAccount(user);

        if (user.getSetupStatus() != AccountSetupStatus.COMPLETED) {
            throw new IllegalStateException(
                    "A conta ainda não concluiu o primeiro acesso"
            );
        }
    }

    private void requireActiveAccount(User user) {
        if (!user.isAtivo()) {
            throw new AccountDisabledException();
        }

    }
}
