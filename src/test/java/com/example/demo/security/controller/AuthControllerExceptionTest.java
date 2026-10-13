package com.example.demo.security.controller;

import com.example.demo.controller.AuthController;
import com.example.demo.exception.DuplicateEmailException;
import com.example.demo.exception.DuplicateMatriculaException;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.security.JwtService;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
public class AuthControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void cadastroComEmailDuplicadoDeveRetornar409() throws Exception {

        String json = """
            {
                "nome": "Artur Pereira",
                "email": "artur@teste.com",
                "cargo": "Estagiario",
                "matricula": "12345678901",
                "password": "Artur123"
            }
            """;
        when(userService.save(any()))
                .thenThrow(new DuplicateEmailException());

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value("E-mail já cadastrado")
                );
        verify(userService).save(any());
    }

    @Test
    void cadastroComMatriculaDuplicadaDeveRetornar409() throws Exception {

        String json = """
            {
                "nome": "Artur Pereira",
                "email": "artur@teste.com",
                "cargo": "Estagiario",
                "matricula": "12345678901",
                "password": "Artur123"
            }
            """;

        when(userService.save(any()))
                .thenThrow(new DuplicateMatriculaException());

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value("Matrícula já cadastrada")
                );

        verify(userService).save(any());
    }


}
