package com.example.demo.security.controller;

import com.example.demo.controller.AuthController;
import com.example.demo.exception.DuplicateEmailException;
import com.example.demo.exception.DuplicateMatriculaException;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.security.JwtService;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
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

    @Test
    void loginDeveNormalizarEmailERetornarTokenDoUsuarioAutenticado()
            throws Exception {

        UserDetails principal =
                org.springframework.security.core.userdetails.User
                        .withUsername("artur@teste.com")
                        .password("hash-da-senha")
                        .roles("USER")
                        .build();

        Authentication authenticated =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authenticated);

        when(jwtService.generateToken(principal))
                .thenReturn("token-de-teste");

        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "email": "Artur@Teste.COM",
                                      "password": "Artur123"
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-de-teste"));

        ArgumentCaptor<Authentication> captor =
                ArgumentCaptor.forClass(Authentication.class);

        verify(authenticationManager).authenticate(captor.capture());

        Authentication submitted = captor.getValue();

        assertEquals("artur@teste.com", submitted.getName());
        assertEquals("Artur123", submitted.getCredentials());

        verify(jwtService).generateToken(principal);
    }

    @Test
    void loginComSenhaIncorretaNaoDeveGerarToken() throws Exception {


        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(
                        new BadCredentialsException("Credenciais inválidas")
                );

        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "email": "artur@teste.com",
                                      "password": "senha-incorreta"
                                    }
                                    """)
                )
                .andExpect(status().isUnauthorized())
                        .andExpect(
                                jsonPath("$.message")
                                        .value("Email ou senha inválidos"));

        verifyNoInteractions(jwtService);
    }

    @Test
    void loginComPrincipalInvalidadoNaoDeveGerarToken() throws Exception {
        Authentication authenticationResult =
                new UsernamePasswordAuthenticationToken(
                        "artur@teste.com",
                        null,
                        List.of()
                );

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authenticationResult);

        mockMvc.perform(
                post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email": "artur@teste.com",
                              "password": "Artur123"
                            }
                            """)
        )
                .andExpect(status().isInternalServerError())
                .andExpect(
                        jsonPath("$.message")
                                .value("Não foi possível realizar o login")
                );

        verifyNoInteractions(jwtService);
    }


}
