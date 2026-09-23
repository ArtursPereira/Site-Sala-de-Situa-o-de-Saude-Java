package com.example.demo.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errors);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentiais(){
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "Message",
                        "Email ou senha inválidos"
                ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleConflict() {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "message",
                        "E-mail ou matrícula já cadastrados"
                ));
    }
}
