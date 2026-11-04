package com.example.demo.exception;

public class AccountDeactivationNotAllowedException extends RuntimeException {
    public AccountDeactivationNotAllowedException() {
        super("Contas administrativas não podem ser desativadas por esta operação");
    }
}
