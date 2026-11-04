package com.example.nss.analytics.api.response;

public record CasoResumoResponse(
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
    Integer totalCasos) {}
