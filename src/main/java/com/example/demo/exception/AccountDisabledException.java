package com.example.demo.exception;

public class AccountDisabledException extends RuntimeException {


    public AccountDisabledException() {

        super("Conta desativada");
    }
}
