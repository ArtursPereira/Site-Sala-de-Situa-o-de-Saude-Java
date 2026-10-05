package com.example.demo.analytics.dto;

public record CasoResumo(
        String codigoDoenca,
        Integer ano,
        Integer mes,
        String codigoMunicipio,
        String codigoUnidade,
        String classificacao,
        String evolucao,
        String sexo,
        Integer semanaNotificacao,
        Integer anoNascimento,
        Integer totalCasos
) {
}