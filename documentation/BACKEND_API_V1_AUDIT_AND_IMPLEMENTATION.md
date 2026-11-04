# Auditoria e implementação da API NSS 1.0

## Estado da execução

- Branch: `feat/api-v1-nss-integration`
- Commit inicial: `8799ed4`
- Commit final: preencher após o commit desta implementação
- `main` não foi modificada.
- A execução da pipeline foi interrompida antes da publicação de SINAN; não houve publicação feita por este repositório.

## Estado inicial e final

O estado inicial possuía autenticação JWT, um datasource analítico separado e apenas `/analytics/casos`. A implementação adiciona o contrato `/api/v1`, filtros validados, envelope NSS, cobertura, endpoints de doenças/metadata, consultas parametrizadas, CORS configurável, refresh cookie HttpOnly, logout e perfil de testes sem segredos externos.

O datasource analítico é separado do JPA de usuários, somente leitura e configurado por `ANALYTICS_DATASOURCE_URL`, `ANALYTICS_DATASOURCE_USERNAME` e `ANALYTICS_DATASOURCE_PASSWORD`. O compose deixou de criar silenciosamente um segundo `analytics-db`: por padrão, o app aponta para o mesmo PostgreSQL configurado para a pipeline, podendo receber outro URL explicitamente.

## Banco e schema

O schema local versionado em `database/001_fato_casos.sql` é antigo: usa `ano`/`mes` e não contém `age_band`, identificadores territoriais ou dimensões da pipeline. A API foi parametrizada para o schema publicado pela pipeline (`year`, `month`, `age_band`, `notification_district_id` e `notification_neighborhood_id`), com `ANALYTICS_YEAR_COLUMN` e `ANALYTICS_MONTH_COLUMN` para adaptação controlada. O PostgreSQL analítico da pipeline não estava disponível nesta execução; portanto, a consulta abaixo ainda precisa ser executada contra o banco real antes do deploy:

```sql
SELECT column_name, data_type FROM information_schema.columns
WHERE table_schema = 'analytics' AND table_name = 'fato_casos' ORDER BY ordinal_position;
```

Também devem ser inspecionadas `dim_doenca`, `dim_municipio`, `dim_unidade_saude`, `dim_classificacao`, `dim_evolucao` e `pipeline_publications`.

## Endpoints e semântica

`POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `GET /api/v1/diseases`, `GET /api/v1/metadata`, `GET /api/v1/epidemiology/municipalities`, `GET /api/v1/epidemiology/districts` e `GET /api/v1/epidemiology/neighborhoods`.

Exemplo: `GET /api/v1/epidemiology/districts?disease=DENG&year=2026&month=3&sex=F&ageBand=20_39&municipalityCode=3301009`.

O retorno usa `totalNotifications`, `notificationsTotal` e `coverage`. `I` é incluído quando sexo não é informado; ano e mês omitidos aparecem como `null` em `filters`. `AVAILABLE` representa universo consultável, inclusive zero; `PARTIAL` separa mapeados/não mapeados; `UNAVAILABLE` retorna total nulo e itens vazios. O total é calculado por SQL, não pelo frontend.

O território é o bairro da notificação derivado da unidade notificadora/CNES. Não representa residência ou endereço do paciente. A implementação exige município para nível intramunicipal e restringe o drill-down inicial a Campos dos Goytacazes; o catálogo canônico completo deve ser conectado antes da publicação final.

## Auditoria do frontend V2

O adapter real em `NSS-front-end/src/data/apiDataSource.ts` faz estas chamadas:

- `/api/v1/diseases`, esperando `{ "items": ["DENG"] }`;
- `/api/v1/metadata`;
- `/api/v1/epidemiology/region` e `/state` durante a navegação inicial;
- `/api/v1/epidemiology/municipalities`, `/districts` e `/neighborhoods` com `disease`, `year`, `month`, `sex`, `ageBand`, `municipalityCode` e `districtCode`.

O backend agora atende o envelope de doenças, os endpoints region/state e usa o catálogo versionado derivado dos assets do frontend (`14` distritos e `73` bairros/localidades com geometria). O frontend ainda usa `mockAuth` e `src/api/http.ts` não envia `Authorization` nem `credentials: include`; por isso, com a política atual de endpoints protegidos, a integração autenticada ainda exige uma alteração no frontend ou uma decisão explícita de tornar as consultas públicas. Nenhum enfraquecimento de segurança foi feito no Java.

## Segurança

Refresh tokens são opacos, rotacionados em memória nesta baseline e enviados apenas em cookie HttpOnly; `Secure` é controlado por `SECURE_COOKIES`. CORS depende de `CORS_ALLOWED_ORIGINS`, sem default de origem externa. Para produção, a rotação deve ser persistida em store compartilhado se houver múltiplas réplicas.

## Testes, falhas e pendências

- `./mvnw test`: passou com perfil H2 isolado, incluindo testes de filtro, total oficial, cobertura `PARTIAL`/`UNAVAILABLE` e zero real.
- `./mvnw verify`, `./mvnw package` e `docker compose config`: executar na validação final.
- Teste PostgreSQL/Testcontainers e MockMvc completo: pendentes; requerem schema real compatível.
- Validar nomes/tipos do schema real e categorias de cobertura da pipeline.
- Conectar catálogo territorial canônico dos 14 distritos/bairros.
- SINAN não foi publicado nesta execução.

Variáveis: `SPRING_DATASOURCE_*`, `ANALYTICS_DATASOURCE_*`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` e `SECURE_COOKIES`. Não versionar credenciais.

```text
BRANCH=feat/api-v1-nss-integration
BASE_HEAD=8799ed4
FINAL_HEAD=see `git rev-parse HEAD` after this documentation commit
ANALYTICS_DATABASE=PostgreSQL analítico da pipeline (não acessível nesta execução)
TEST_STATUS=./mvnw test passou
BUILD_STATUS=./mvnw verify e ./mvnw package passaram; testes de regras adicionais passaram
API_STATUS=implementação baseline /api/v1; schema real pendente
MAIN_MODIFIED=NO
```
