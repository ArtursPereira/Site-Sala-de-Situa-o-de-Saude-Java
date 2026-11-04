package com.example.nss.analytics.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AnalyticsRepositoryTest {
  @Test
  void mapsRowsFromTheAnalyticsFactTable() {
    JdbcTemplate jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
    jdbc.execute("CREATE SCHEMA analytics");
    jdbc.execute(
        "CREATE TABLE analytics.fato_casos (disease_codigo VARCHAR, ano INT, mes INT, cd_mun VARCHAR, cd_unidade VARCHAR, cd_classificacao VARCHAR, cd_evolucao VARCHAR, cd_sexo VARCHAR, semana_notif INT, ano_nascimento INT, cases_total INT)");
    jdbc.update(
        "INSERT INTO analytics.fato_casos VALUES ('DENG', 2024, 3, '3301009', 'UNIT-1', 'A', 'C', 'F', 12, 1990, 4)");

    var result = new AnalyticsRepository(jdbc).buscarCasos(2024, 3);

    assertEquals(1, result.size());
    assertEquals("DENG", result.getFirst().codigoDoenca());
    assertEquals("3301009", result.getFirst().codigoMunicipio());
    assertEquals(4, result.getFirst().totalCasos());
  }
}
