package com.example.demo.entity;

import jakarta.persistence.*;
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
    @GeneratedValue
    private Long id;
    @Column(name = "users_nome")
    private String nome;
    @Column(name = "users_email")
    private String email;
    @Column(name = "users_password")
    private String password;
    @Column(name = "users_matricula")
    private String matricula;
    @Column(name = "users_matricula")
    private String cargo;

}
