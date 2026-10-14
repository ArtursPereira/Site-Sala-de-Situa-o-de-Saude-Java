package com.example.demo.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final UserDetails userDetails =
            org.springframework.security.core.userdetails.User
                    .withUsername("artur@teste.com")
                    .password("hash")
                    .roles("USER")
                    .build();

    @Test
    void tokenValidoDeveConterUsuarioEPassarNaValidacao() {
        JwtService jwtService = new JwtService(SECRET, 60_000);

        String token = jwtService.generateToken(userDetails);

        assertEquals("artur@teste.com", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void tokenDeOutroUsuarioDeveSerRejeitado() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        UserDetails otherUser =
                org.springframework.security.core.userdetails.User
                        .withUsername("outro@teste.com")
                        .password("hash")
                        .roles("USER")
                        .build();

        String token = jwtService.generateToken(userDetails);

        assertFalse(jwtService.isTokenValid(token, otherUser));
    }

    @Test
    void tokenExpiradoDeveSerRejeitado() {
        JwtService jwtService = new JwtService(SECRET, -1);
        String token = jwtService.generateToken(userDetails);

        assertThrows(JwtException.class, () -> jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void tokenAdulteradoDeveSerRejeitado() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        String token = jwtService.generateToken(userDetails);
        char replacement = token.endsWith("a") ? 'b' : 'a';
        String tamperedToken = token.substring(0, token.length() - 1) + replacement;

        assertThrows(JwtException.class, () -> jwtService.extractUsername(tamperedToken));
    }
}
