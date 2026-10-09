package com.example.demo.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("E-mail já cadastrado");
    }
}
