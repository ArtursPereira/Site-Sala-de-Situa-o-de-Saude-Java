package com.example.demo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
    @Column(name = "nome", nullable = false, length = 100)
    private String nome;


    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ser válido")
    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;


    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    @Column(name = "password", nullable = false, length = 100)
    private String password;


    /*TODO verificar se a matricula pode ou não ter zeros a esquerda
    Para poder trocar para Long ou deixar como String*/
    @NotBlank(message = "Matrícula é obrigatória")
    @Pattern(regexp = "\\d{11}", message = "Matrícula deve conter exatamente 11 dígitos numéricos")
    @Column(name = "matricula", nullable = false, unique = true, length = 11)
    private String matricula;


    @NotBlank(message = "Cargo é obrigatório")
    @Size(min = 3, max = 30, message = "Cargo deve ter entre 3 e 30 caracteres")
    @Column(name = "cargo", nullable = false, length = 30)
    private String cargo;

}
