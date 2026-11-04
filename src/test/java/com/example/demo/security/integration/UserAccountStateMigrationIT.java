package com.example.demo.security.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class UserAccountStateMigrationIT {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void migrationShouldPreserveExistingUserAndInitializeAccountState() {
        // 1. Reproduz o banco anterior à inclusão do estado da conta.
        Flyway beforeMigration = Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
                .locations("classpath:db/migration")
                .target("2")
                .load();

        beforeMigration.migrate();

        assertEquals(
                "2",
                beforeMigration.info().current().getVersion().toString()
        );

        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
        );

        // 2. Insere um usuário usando apenas as colunas existentes na V2.
        String originalPasswordHash = "existing-password-hash";

        Long userId = jdbc.queryForObject("""
                INSERT INTO users (
                    nome,
                    email,
                    password,
                    matricula,
                    cargo,
                    role
                )
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "Artur Pereira",
                "migration@teste.com",
                originalPasswordHash,
                "12345678901",
                "Analista",
                "ADMIN"
        );

        assertNotNull(userId);

        // 3. Atualiza o mesmo banco até a V3.
        Flyway afterMigration = Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
                .locations("classpath:db/migration")
                .target("3")
                .load();

        afterMigration.migrate();

        assertEquals(
                "3",
                afterMigration.info().current().getVersion().toString()
        );

        // 4. Consulta o estado efetivamente persistido após a migração.
        var persisted = jdbc.queryForMap("""
                SELECT id, nome, email, password, matricula, cargo,
                       role, ativo, setup_status
                FROM users
                WHERE id = ?
                """,
                userId
        );

        assertAll(
                () -> assertEquals(
                        userId.longValue(),
                        ((Number) persisted.get("id")).longValue()
                ),
                () -> assertEquals("Artur Pereira", persisted.get("nome")),
                () -> assertEquals(
                        "migration@teste.com",
                        persisted.get("email")
                ),
                () -> assertEquals(
                        originalPasswordHash,
                        persisted.get("password")
                ),
                () -> assertEquals(
                        "12345678901",
                        persisted.get("matricula")
                ),
                () -> assertEquals("Analista", persisted.get("cargo")),
                () -> assertEquals("ADMIN", persisted.get("role")),
                () -> assertEquals(Boolean.TRUE, persisted.get("ativo")),
                () -> assertEquals(
                        "COMPLETED",
                        persisted.get("setup_status")
                )
        );

        // 5. Novas inserções sem setup_status devem receber PENDING.
        String newAccountStatus = jdbc.queryForObject("""
                INSERT INTO users (
                    nome,
                    email,
                    password,
                    matricula,
                    cargo,
                    role
                )
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING setup_status
                """,
                String.class,
                "Novo usuário",
                "new-migration@teste.com",
                "another-password-hash",
                "10987654321",
                "Analista",
                "USER"
        );

        assertEquals("PENDING", newAccountStatus);
    }
}