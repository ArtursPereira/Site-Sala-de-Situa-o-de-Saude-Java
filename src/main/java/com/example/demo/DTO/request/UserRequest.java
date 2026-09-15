package com.example.demo.DTO.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRequest(@NotBlank(message = "Nome é obrigatório")
                          @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
                          String nome,
                          @NotBlank(message = "Email é obrigatório")
                          @Email(message = "Email inválido")
                          String email,
                          @NotBlank(message = "Cargo é obrigatório")
                          @Size(min = 3, max = 30, message = "O cargo deve ter entre 3 e 30 caracteres")
                          String cargo,
                          @NotBlank(message = "Matricula é obrigatório")
                          @Pattern(regexp = "\\d{11}", message = "Matrícula deve conter exatamente 11 dígitos numéricos")
                          String matricula,
                          @NotBlank(message = "Senha é obrigatória")
                          @Size(min = 8, max = 128, message = "Senha deve ter entre 8 e 128 caracteres")
                          String password)

{}
