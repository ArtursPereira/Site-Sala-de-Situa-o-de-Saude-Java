package com.example.demo.exception;

public class DuplicateMatriculaException extends RuntimeException{

    public DuplicateMatriculaException() {
        super("Matrícula já cadastrada");
    }
}
