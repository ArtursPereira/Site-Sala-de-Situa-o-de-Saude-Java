package com.example.demo.analytics.repository;

import com.example.demo.analytics.dto.CasoResumo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsRepository(
            @Qualifier("analyticsJdbcTemplate") JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CasoResumo> buscarCasos(Integer ano, Integer mes) {
        String sql = """
                SELECT
                    f.disease_codigo,
                    f.ano,
                    f.mes,
                    f.cd_mun,
                    f.cd_unidade,
                    f.cd_classificacao,
                    f.cd_evolucao,
                    f.cd_sexo,
                    f.semana_notif,
                    f.ano_nascimento,
                    f.cases_total
                FROM analytics.fato_casos f
                WHERE f.ano = ?
                  AND f.mes = ?
                ORDER BY f.cd_mun, f.disease_codigo
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new CasoResumo(
                        rs.getString("disease_codigo"),
                        rs.getObject("ano", Integer.class),
                        rs.getObject("mes", Integer.class),
                        rs.getString("cd_mun"),
                        rs.getString("cd_unidade"),
                        rs.getString("cd_classificacao"),
                        rs.getString("cd_evolucao"),
                        rs.getString("cd_sexo"),
                        rs.getObject("semana_notif", Integer.class),
                        rs.getObject("ano_nascimento", Integer.class),
                        rs.getObject("cases_total", Integer.class)
                ),
                ano,
                mes
        );
    }
}