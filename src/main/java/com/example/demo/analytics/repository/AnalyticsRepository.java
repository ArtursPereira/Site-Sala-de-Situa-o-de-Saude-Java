package com.example.demo.analytics.repository;

import com.example.demo.analytics.dto.CasoResumo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<CasoResumo> buscarCasos(
            Integer ano,
            Integer mes
    ) {
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
                        rs.getInt("ano"),
                        rs.getInt("mes"),
                        rs.getString("cd_mun"),
                        rs.getString("cd_unidade"),
                        rs.getString("cd_classificacao"),
                        rs.getString("cd_evolucao"),
                        rs.getString("cd_sexo"),
                        rs.getInt("semana_notif"),
                        rs.getInt("ano_nascimento"),
                        rs.getInt("cases_total")
                ),
                ano,
                mes
        );
    }
}