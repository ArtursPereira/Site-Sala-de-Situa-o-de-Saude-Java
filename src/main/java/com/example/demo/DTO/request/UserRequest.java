package com.example.demo.DTO.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserRequest(@NotBlank(message = "Nome é obrigatório")
                          String nome,
                          @NotBlank(message = "Email é obrigatório")
                          String email,
                          @NotBlank(message = "Cargo é obrigatório")
                          String cargo,
                          @NotBlank(message = "Matricula é obrigatório")
                          @Email(message = "Email inválido")
                          String matricula )
{}
