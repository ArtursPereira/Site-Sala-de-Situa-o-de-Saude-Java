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
    @Column(name = "nome", nullable = false, length = 100)
    private String nome;


    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;


    @Column(name = "password", nullable = false, length = 255)
    private String password;


    @Column(name = "matricula", nullable = false, unique = true, length = 11)
    private String matricula;


    @Column(name = "cargo", nullable = false, length = 30)
    private String cargo;

}
