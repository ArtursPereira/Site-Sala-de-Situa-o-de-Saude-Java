package com.example.demo.security.integration;

import com.example.demo.entity.AccountSetupStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers

@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "jwt.expiration=3600000"
})

@AutoConfigureMockMvc
public class AccountAuthenticationIT {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // A aplicação exige o segundo datasource.
        // Neste teste, nenhuma consulta analítica será executada.
        registry.add("analytics.datasource.url", postgres::getJdbcUrl);
        registry.add("analytics.datasource.username", postgres::getUsername);
        registry.add("analytics.datasource.password", postgres::getPassword);

        // Chave exclusiva para testes, com 32 bytes.
        registry.add("jwt.secret", () ->
                Base64.getEncoder().encodeToString(
                        "0123456789abcdef0123456789abcdef"
                                .getBytes(StandardCharsets.UTF_8)
                )
        );
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void adminDeactivationShouldBlockExistingTokenAndNewLogin()
            throws Exception {

        User admin = persistAccount(
                "admin-flow@teste.com",
                "44444444444",
                Role.ADMIN
        );

        User target = persistAccount(
                "user-flow@teste.com",
                "55555555555",
                Role.USER
        );

        String adminToken = login(admin.getEmail());
        String userToken = login(target.getEmail());

        // O administrador consegue acessar a rota administrativa.
        mockMvc.perform(
                        get("/NSS/users")
                                .header("Authorization", "Bearer " + adminToken)
                )
                .andExpect(status().isOk());

        // O usuário está autenticado, mas não possui permissão administrativa.
        mockMvc.perform(
                        get("/NSS/users")
                                .header("Authorization", "Bearer " + userToken)
                )
                .andExpect(status().isForbidden());

        // A desativação acontece pelo endpoint, com JWT real do administrador.
        mockMvc.perform(
                        patch("/NSS/users/{id}/deactivation", target.getId())
                                .header("Authorization", "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());

        User persisted = userRepository.findById(target.getId())
                .orElseThrow();

        assertFalse(persisted.isAtivo());
        assertEquals(
                AccountSetupStatus.COMPLETED,
                persisted.getSetupStatus()
        );

        // O JWT do usuário agora deixa de autenticar a requisição.
        mockMvc.perform(
                        get("/NSS/users")
                                .header("Authorization", "Bearer " + userToken)
                )
                .andExpect(status().isUnauthorized());

        // A senha continua correta, mas a conta desativada não pode fazer login.
        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "email": "user-flow@teste.com",
                                      "password": "SenhaTeste123!"
                                    }
                                    """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.message")
                                .value("Email ou senha inválidos")
                )
                .andExpect(jsonPath("$.token").doesNotExist());

        // A operação não deve bloquear o administrador solicitante.
        assertTrue(
                userRepository.findById(admin.getId())
                        .orElseThrow()
                        .isAtivo()
        );

        mockMvc.perform(
                        get("/NSS/users")
                                .header("Authorization", "Bearer " + adminToken)
                )
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminDeveDesativarUsuarioPreservandoSeusDados() throws Exception {
        User user = persistAccount(
                "desativacao@teste.com",
                "11111111111",
                Role.USER
        );

        String originalPassword = user.getPassword();

        mockMvc.perform(
                        patch("/NSS/users/{id}/deactivation", user.getId())
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        User persisted = userRepository.findById(user.getId())
                .orElseThrow();

        assertFalse(persisted.isAtivo());
        assertEquals(AccountSetupStatus.COMPLETED, persisted.getSetupStatus());
        assertEquals(originalPassword, persisted.getPassword());
        assertEquals(Role.USER, persisted.getRole());
        assertEquals("desativacao@teste.com", persisted.getEmail());

        // Repetir a operação também deve funcionar.
        mockMvc.perform(
                        patch("/NSS/users/{id}/deactivation", user.getId())
                )
                .andExpect(status().isNoContent());

        assertFalse(
                userRepository.findById(user.getId())
                        .orElseThrow()
                        .isAtivo()
        );
    }

    @Test
    @WithMockUser(roles = "USER")
    void usuarioComumNaoDeveDesativarOutraConta() throws Exception {
        User target = persistAccount(
                "alvo@teste.com",
                "22222222222",
                Role.USER
        );

        mockMvc.perform(
                        patch("/NSS/users/{id}/deactivation", target.getId())
                )
                .andExpect(status().isForbidden());

        assertTrue(
                userRepository.findById(target.getId())
                        .orElseThrow()
                        .isAtivo()
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void contaAdministrativaNaoDeveSerDesativada() throws Exception {
        User target = persistAccount(
                "admin-protegido@teste.com",
                "33333333333",
                Role.ADMIN
        );

        mockMvc.perform(
                        patch("/NSS/users/{id}/deactivation", target.getId())
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message").value(
                                "Contas administrativas não podem ser desativadas por esta operação"
                        )
                );

        assertTrue(
                userRepository.findById(target.getId())
                        .orElseThrow()
                        .isAtivo()
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void desativarUsuarioInexistenteDeveRetornar404() throws Exception {
        mockMvc.perform(
                        patch("/NSS/users/{id}/deactivation", Long.MAX_VALUE)
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.message")
                                .value("Usuário não encontrado")
                );
    }

    @Test
    void anonimoNaoDeveConseguirDesativarConta() throws Exception {
        mockMvc.perform(
                        patch("/NSS/users/{id}/deactivation", 1L)
                )
                .andExpect(status().isUnauthorized());
    }

    private User persistAccount(String email, String matricula, Role role) {
        User user = new User();
        user.setNome("Usuário de teste");
        user.setEmail(email);
        user.setCargo("Analista");
        user.setMatricula(matricula);
        user.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        user.setRole(role);
        user.setAtivo(true);
        user.setSetupStatus(AccountSetupStatus.COMPLETED);

        return userRepository.saveAndFlush(user);
    }

    private String login(String email) throws Exception {
        String responseBody = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "email": "%s",
                                      "password": "SenhaTeste123!"
                                    }
                                    """.formatted(email))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(responseBody, "$.token");
    }




}
