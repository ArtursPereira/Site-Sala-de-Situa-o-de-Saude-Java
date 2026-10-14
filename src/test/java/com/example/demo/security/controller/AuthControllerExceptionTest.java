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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.mockito.ArgumentCaptor;
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

    @Test
    void loginValidoDeveNormalizarEmailERetornarToken() throws Exception {
        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername("artur@teste.com")
                .password("hash")
                .roles("USER")
                .build();
        Authentication authenticated = org.mockito.Mockito.mock(Authentication.class);

        when(authenticated.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any())).thenReturn(authenticated);
        when(jwtService.generateToken(principal)).thenReturn("token-assinado");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "Artur@Teste.COM",
                                  "password": "Artur123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-assinado"));

        ArgumentCaptor<Authentication> captor =
                ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertEquals("artur@teste.com", captor.getValue().getName());
        verify(jwtService).generateToken(principal);
    }

    @Test
    void loginComCredenciaisInvalidasDeveRetornar401() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("credenciais inválidas"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "artur@teste.com",
                                  "password": "senha-incorreta"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha inválidos"));
    }

    @Test
    void conflitoDeIntegridadeNaoDeveAfirmarQualRestricaoFalhou() throws Exception {
        when(userService.save(any()))
                .thenThrow(new DataIntegrityViolationException("constraint inesperada"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Artur Pereira",
                                  "email": "artur@teste.com",
                                  "cargo": "Estagiario",
                                  "matricula": "12345678901",
                                  "password": "Artur123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Não foi possível salvar os dados por conflito de integridade"
                ));
    }


}
