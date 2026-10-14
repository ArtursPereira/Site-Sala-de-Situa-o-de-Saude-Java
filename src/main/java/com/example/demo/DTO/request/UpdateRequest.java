package com.example.demo.DTO.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateRequest(@Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
                            @NotBlank(message = "Nome é obrigatório")
                            String nome,
                            @NotBlank(message = "Email é obrigatório")
                            @Email(message = "Email válido")
                            String email,
                            @NotBlank(message = "Cargo é obrigatório")
                            @Size(min = 3, max = 30, message = "O cargo deve ter entre 3 e 30 caracteres")
                            String cargo,
                            @NotBlank(message = "Matricula é obrigatório")
                            @Pattern(regexp = "\\d{11}", message = "Matrícula deve conter exatamente 11 dígitos numéricos")
                            String matricula) {
}
