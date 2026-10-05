CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.fato_casos (
    id               BIGSERIAL PRIMARY KEY,
    disease_codigo   TEXT        NOT NULL,
    ano              SMALLINT    NOT NULL,
    mes              SMALLINT    NOT NULL CHECK (mes BETWEEN 1 AND 12),
    cd_mun           TEXT        NOT NULL,
    cd_unidade       TEXT,
    cd_classificacao TEXT,
    cd_evolucao      TEXT,
    cases_total      INTEGER     NOT NULL DEFAULT 1,
    batch_id         TEXT        NOT NULL,
    loaded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cd_sexo          CHAR(1),
    semana_notif     INTEGER,
    ano_nascimento   SMALLINT
);

CREATE INDEX IF NOT EXISTS idx_fato_doenca_periodo
    ON analytics.fato_casos (disease_codigo, ano, mes);
CREATE INDEX IF NOT EXISTS idx_fato_municipio
    ON analytics.fato_casos (cd_mun);
CREATE INDEX IF NOT EXISTS idx_fato_batch
    ON analytics.fato_casos (batch_id);
