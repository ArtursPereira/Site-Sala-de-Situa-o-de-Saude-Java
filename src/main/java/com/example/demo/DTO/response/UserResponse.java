package com.example.demo.DTO.response;

public record UserResponse(
        Long id,
        String nome,
        String email,
        String cargo,
        String matricula

) {}
