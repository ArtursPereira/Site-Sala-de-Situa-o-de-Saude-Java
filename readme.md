# Sala de Situação de Saúde (NSS) — backend

Backend Spring Boot/Java 21 para autenticação e leitura do PostgreSQL analítico publicado pela pipeline. Não acessa PySUS, SINAN bruto, CNES operacional ou Parquet em runtime.

## Execução local

Configure `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `ANALYTICS_DATASOURCE_URL`, `ANALYTICS_DATASOURCE_USERNAME`, `ANALYTICS_DATASOURCE_PASSWORD` e `JWT_SECRET` sem versionar segredos. Exemplo:

```bash
export ANALYTICS_DATASOURCE_URL=jdbc:postgresql://localhost:5432/situacao_saude
export ANALYTICS_DATASOURCE_USERNAME=nss
export ANALYTICS_DATASOURCE_PASSWORD='...'
export JWT_SECRET='base64-com-pelo-menos-32-bytes'
./mvnw -Pdev spring-boot:run
```

O perfil Maven `dev` adiciona somente a integração do Spring Boot com o
`compose.yaml`. Ele é destinado à execução local e não é ativado no Docker.

## Dependências por ambiente

- **Produção/Docker:** o `Dockerfile` compila a aplicação com Java 21 e gera
  uma imagem final somente com o JRE e o JAR empacotado. As dependências de
  runtime da aplicação (Spring Web, JPA, PostgreSQL, segurança, JWT, Flyway,
  Actuator e métricas) são empacotadas nesse JAR.
- **Desenvolvimento local:** `./mvnw -Pdev spring-boot:run` habilita o starter
  `spring-boot-docker-compose`, que permite ao Spring iniciar o
  `compose.yaml` automaticamente.
- **Testes e build/CI:** `./mvnw test` ou `./mvnw verify` usam dependências com
  escopo `test` somente durante a compilação e execução dos testes. O perfil
  `dev` não é ativado no CI nem no build da imagem.
- **Compilação:** MapStruct e Lombok têm escopo `provided` e seus processadores
  geram o código durante o build; eles não são necessários em runtime.

Para executar tudo via Docker, use:

```bash
docker compose up --build
```

Nesse fluxo o Compose é executado externamente pelo Docker, portanto o
starter `spring-boot-docker-compose` não precisa estar na imagem final.

O compose usa o PostgreSQL configurado pela pipeline por padrão e não cria um `analytics-db` incompatível. O usuário analítico deve ser somente leitura. `CORS_ALLOWED_ORIGINS` e `SECURE_COOKIES` são explícitos para produção.

## API V1

Autenticação: `POST /api/v1/auth/login`, `/refresh` e `/logout`. O access token é retornado no corpo; refresh fica em cookie HttpOnly. Endpoints: `/api/v1/diseases`, `/api/v1/metadata`, `/api/v1/epidemiology/municipalities`, `/districts` e `/neighborhoods`.

Filtros: `disease`, `year`, `month`, `sex` (`M`/`F`), `ageBand`, `municipalityCode` e `districtCode`. Sexo omitido consulta `M`, `F` e `I`; ano/mês omitidos significam todos. O envelope sempre contém `totalNotifications`, `coverage` e `items`; o frontend não soma os itens.

O bairro é o bairro da notificação derivado da unidade notificadora via CNES, não o endereço residencial do paciente. Ausência de mapeamento é reportada em `coverage`, não como bairro fictício. Ver o relatório em [documentation/BACKEND_API_V1_AUDIT_AND_IMPLEMENTATION.md](documentation/BACKEND_API_V1_AUDIT_AND_IMPLEMENTATION.md).

## Testes

`./mvnw test` usa H2 em profile de teste e não depende de variáveis secretas. A validação PostgreSQL deve usar o schema real da pipeline e dataset mínimo controlado, sem apagar volumes nem publicar dados SINAN.

## Qualidade do código

`./mvnw verify` valida a formatação com Spotless, as regras de estilo com
Checkstyle e code smells com PMD. Para formatar os arquivos localmente antes do
commit, execute:

```bash
./mvnw spotless:apply
```

O JaCoCo coleta cobertura durante `test` e `verify` e publica relatórios HTML,
XML e CSV em `target/site/jacoco/`, com visões separadas em `unit/` e
`integration/`. O planejamento incremental está em
[documentation/PLANEJAMENTO_TESTES.md](documentation/PLANEJAMENTO_TESTES.md).

O checklist de deploy, smoke test, backup/restauração, rollback e observabilidade
está em [documentation/OPERACAO_PRODUCAO.md](documentation/OPERACAO_PRODUCAO.md).
