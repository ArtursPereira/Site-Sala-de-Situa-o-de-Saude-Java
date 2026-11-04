# Planejamento de organização e qualidade do backend

Este documento registra a evolução planejada do backend e a ordem sugerida dos
commits. A organização segue separação por responsabilidade e o histórico deve
usar Conventional Commits.

## Objetivos

- manter controllers finos e com respostas tipadas;
- concentrar regras de negócio nos services;
- limitar repositories à persistência e execução de consultas;
- recuperar mappers para conversões entre entidades e DTOs;
- tornar as fronteiras entre camadas verificáveis automaticamente;
- padronizar formatação, análise estática e testes no build.

## Organização desejada

```text
src/main/java/com/example/nss/
├── controller/       # HTTP, validação de entrada e status de resposta
├── DTO/
│   ├── mapper/       # conversões Entity <-> DTO
│   ├── request/      # contratos de entrada
│   └── response/     # contratos de saída
├── entity/           # entidades e enums persistidos
├── exception/        # erros de domínio e contrato de erro
├── repository/       # acesso JPA e execução de queries
├── security/         # JWT, autenticação e autorização
└── service/          # casos de uso e regras de negócio
```

O módulo analítico mantém a mesma separação dentro de `analytics/`. O
`EpidemiologyRepository` pode montar e executar SQL, mas não deve decidir regras
de cobertura territorial ou montar respostas HTTP. Essas decisões pertencem ao
service e a objetos de consulta tipados.

## Regras de responsabilidade

| Camada | Pode fazer | Não deve fazer |
| --- | --- | --- |
| Controller | Receber requests, validar DTOs e devolver responses tipadas | Acessar repository ou conter regra de negócio |
| Service | Coordenar casos de uso, validar regras e transações | Conhecer detalhes HTTP |
| Repository | Consultar e persistir dados | Montar resposta de API ou decidir fluxo de negócio |
| Mapper | Converter entidades, projections e DTOs | Consultar banco ou validar autorização |
| Entity | Representar estado e invariantes locais | Conhecer controller ou DTO de transporte |

## Plano de commits

Os commits devem ser pequenos, independentes e escritos em inglês, no
imperativo, seguindo o formato `type(scope): description`.

### Fase 1 — documentar as decisões

- `docs(architecture): define backend layering and naming conventions`

Critério: regras de dependência, nomes de pacotes e responsabilidades ficam
documentados e revisáveis.

### Fase 2 — recuperar conversões tipadas

- `refactor(user): restore typed entity to dto mappers`
- `refactor(admin): move managed user mapping into mapper`

Critério: `UserService` e `AdminUserService` não montam manualmente respostas;
controllers deixam de retornar `Object`.

### Fase 3 — separar regras analíticas do acesso a dados

- `refactor(analytics): extract typed query results and row mappers`
- `refactor(analytics): move territory rules into application services`

Critério: repositories retornam projections ou records tipados, sem
`Map<String, Object>` espalhado pela aplicação; regras de cobertura ficam fora
do repository.

### Fase 4 — proteger a arquitetura

- `test(architecture): enforce controller service repository boundaries`

Critério: testes ArchUnit impedem controller dependente de repository,
repository dependente de service e respostas HTTP sem tipagem.

### Fase 5 — automatizar qualidade

- `chore(quality): add spotless and checkstyle validation`
- `chore(quality): add pmd static analysis`
- `test(architecture): enforce controller service repository boundaries`
- `chore(quality): add static analysis to continuous integration`

Critério: Spotless formata automaticamente; Checkstyle falha o build quando o
estilo e a indentação estão errados; PMD detecta code smells; ArchUnit impede
dependências fora do sentido `Controller -> Service -> Repository`; o CI
executa todas as verificações e os testes.

### Fase 6 — validar o comportamento preservado

- `test(analytics): cover repository adapters and coverage rules`
- `test(auth): cover normalized login and authenticated principal reuse`

Critério: as refatorações preservam primeiro acesso, reset de senha, refresh
token, rate limiting, autorização administrativa e contrato da API NSS v1.

## Ordem de execução

1. Criar mappers e respostas tipadas.
2. Refatorar services e controllers de usuários.
3. Tipar resultados e separar regras do módulo analítico.
4. Adicionar testes de regressão.
5. Adicionar ArchUnit.
6. Adicionar Spotless, Checkstyle e análise estática ao CI.

Cada etapa deve manter `./mvnw test` verde antes do próximo commit.

## Status da execução

As fases de organização arquitetural, mappers tipados, separação do módulo
analítico, ArchUnit, Spotless, Checkstyle, PMD e integração das quality gates
no CI foram concluídas. A Fase 6 foi iniciada com testes de analytics e
autenticação, mas a validação comportamental completa dos fluxos de primeiro
acesso, reset, refresh, autorização administrativa e contrato HTTP ainda será
aprofundada no planejamento de testes.
