# Planejamento incremental de testes

## Objetivo

Aumentar a confiança no backend com testes úteis, evitando metas artificiais.
Cada ciclo deve elevar em aproximadamente 5 pontos percentuais a cobertura
global de um contador, priorizando regras de negócio e fluxos de risco.

## Linha de base inicial

Medição feita com JaCoCo sobre os 28 testes executados pelo Maven:

| Métrica | Cobertura atual | Próxima meta (+5 p.p.) |
| --- | ---: | ---: |
| Linhas | 48,29% | 53,29% |
| Branches | 41,67% | 46,67% |
| Métodos | 46,80% | 51,80% |
| Instruções | 47,76% | 52,76% |
| Complexidade | 36,56% | 41,56% |

O JaCoCo gera os relatórios em `target/site/jacoco/` e o build impede
regressões abaixo da linha de base atual para linhas, branches e métodos.

Após o início do ciclo de API e persistência, a medição global chegou a
71,55% de linhas, 59,13% de branches e 67,98% de métodos, superando a
referência de 5 p.p. do ciclo anterior nos três contadores.

## Classificação dos testes

Cobertura de linhas, branches e métodos é medida pelo JaCoCo. “Teste unitário”
e “teste de integração” são categorias de execução e terão relatórios
separados, pois não são contadores de cobertura.

Estado atual do inventário:

- 24 testes unitários com JUnit/Mockito;
- 1 teste de contexto Spring Boot (`NssApplicationIT`), tratado como
  integração;
- 3 regras ArchUnit, tratadas como testes arquiteturais.

O primeiro ciclo separou os testes com Maven Surefire/Failsafe e classes `*IT`.
As coberturas por execução são:

| Execução | Linhas | Branches | Métodos |
| --- | ---: | ---: | ---: |
| Unitários/arquitetura | 39,82% | 39,29% | 36,45% |
| Integração | 13,78% | 5,16% | 15,27% |
| Global | 48,29% | 41,67% | 46,80% |

Os relatórios ficam em `target/site/jacoco/unit/`,
`target/site/jacoco/integration/` e `target/site/jacoco/`.

O ciclo de separação está concluído; os próximos ciclos começam pelo aumento
de cobertura comportamental, sem transformar a diferença entre unitário e
integração em uma meta artificial.

## Ciclos incrementais

### Ciclo 1 — baseline e separação

- manter JaCoCo no `verify`;
- gerar HTML, XML e CSV;
- separar Surefire e Failsafe;
- classificar testes existentes sem duplicá-los;
- registrar a cobertura unitária e de integração individualmente.

### Ciclo 2 — autenticação (+5 p.p.)

- testar login inválido e rate limiting;
- testar refresh, rotação e revogação;
- testar primeiro acesso e reset de senha;
- testar autorização administrativa e respostas HTTP.

Status: concluído. A cobertura global passou para 58,15% de linhas, 47,22% de
branches e 52,22% de métodos. O ciclo adicionou cenários de falha de login,
principal inválido, refresh, logout, rotação de sessão, primeiro acesso e
carregamento do usuário autenticado.

Próxima referência para o ciclo seguinte: 63,15% de linhas, 52,22% de
branches e 57,22% de métodos.

### Ciclo 3 — analytics (+5 p.p.)

- cobrir filtros por doença, período, sexo e faixa etária;
- cobrir município, distrito e bairro;
- cobrir estados `AVAILABLE`, `PARTIAL` e `UNAVAILABLE`;
- validar SQL parametrizado com PostgreSQL via Testcontainers.

Status: concluído. Foram adicionados testes para serviço, controllers, filtros,
repositórios e estados de disponibilidade. O ciclo elevou a cobertura global
para 63,72% de linhas, 52,38% de branches e 60,10% de métodos, superando a
meta de 5 p.p. em relação ao ciclo de autenticação.

Observação: o cenário de persistência foi validado com H2 compatível com o SQL
parametrizado atual. A validação com PostgreSQL via Testcontainers permanece
como parte do ciclo de API e persistência, para evitar duplicar testes sem
ganho comportamental nesta etapa.

Próxima referência para o ciclo seguinte: 68,72% de linhas, 57,38% de
branches e 65,10% de métodos.

### Ciclo 4 — API e persistência (+5 p.p.)

- adicionar testes `@WebMvcTest` para contratos HTTP;
- adicionar testes de repository com PostgreSQL real;
- validar erros de validação, autenticação e autorização;
- preservar o contrato JSON da API NSS v1.

Execução iniciada com cobertura do `AdminUserService`. O próximo incremento
deve priorizar contratos HTTP e persistência real, sempre validando respostas
observáveis e regras de autorização.

O incremento de cobertura está concluído, mas o ciclo permanece aberto: ainda
faltam testes `@WebMvcTest`, PostgreSQL real e a análise das mutações
sobreviventes do PIT.

Após os testes adicionais de sessões, rate limiting, catálogo territorial e
contratos de controller, a cobertura global chegou a 74,34% de linhas, 67,06%
de branches e 70,94% de métodos. O PIT também passou com 115 de 191 mutações
mortas (60%) e test strength de 91%. As 65 mutações sem cobertura permanecem
indicadores para a próxima análise; não foram ocultadas por exclusões amplas.

Com a execução HTTP e PostgreSQL real, a medição oficial do `clean verify`
passou a ser 77,12% de linhas, 69,05% de branches e 74,38% de métodos. Os
relatórios separados registraram 67,64%/64,68%/64,04% para unitários e
21,11%/14,29%/21,18% para integração.

Após os testes de `UserService`, `MailService`, primeiro acesso, reset de senha,
renovação de convite e cenários analíticos adicionais, a medição oficial mais
recente chegou a 88,75% de linhas, 74,60% de branches e 85,22% de métodos.
Unitários ficaram em 78,63%/69,84%/73,40% e integração em
22,00%/15,87%/23,15%.

Também foram adicionados três testes `@WebMvcTest` com `MockMvc` e um teste
`EpidemiologyRepositoryPostgresIT` com PostgreSQL 16 via Testcontainers. O
contrato HTTP e a persistência real estão cobertos; autorização completa por
JWT permanece como próximo cenário, pois os testes HTTP atuais desabilitam os
filtros para isolar o contrato do controller.

Esse cenário também foi validado com `AdminUserAuthorizationIT` usando a
aplicação completa: acesso anônimo retorna 401, usuário sem role ADMIN retorna
403 e usuário ADMIN acessa o endpoint. O ciclo 4 agora está concluído quanto
aos itens de API, persistência e autorização previstos.

### Ciclo 5 — força dos testes

- executar PIT nos services de autenticação e analytics;
- analisar mutantes sobreviventes;
- criar testes somente para comportamentos que os mutantes demonstrarem não
  estar protegidos.

Critério definido em `documentation/CRITERIOS_QUALIDADE_TESTES.md`. A primeira
execução do PIT encontrou 191 mutações, matou 84 (44%) e registrou test
strength de 74%; portanto o gate falhou corretamente e há mutações a analisar
antes de considerar esta fase concluída.

Depois dos ciclos de reforço, o PIT passou a matar 155 de 191 mutações (81%),
com test strength de 89%. A revisão dos testes encontrou uma lacuna real no
teste de convite: ele verificava um objeto de preparação, e não a entidade
persistida. O teste foi reescrito para capturar a entidade real e foram
adicionados cenários de reset, expiração, convite concluído, usuário ausente,
hash e resposta indisponível.

O resultado atual do PIT é 180 de 191 mutações mortas (94%), test strength de
96% e três mutações sem cobertura. As oito sobreviventes e as três sem
cobertura foram classificadas em `documentation/PIT_CLASSIFICACAO_MUTACOES.md`.
As classificadas como equivalentes, irrelevantes ou de infraestrutura não foram
transformadas em testes artificiais nem ocultadas por exclusões amplas.
O ciclo 5 está concluído.

### Ciclo 6 — contratos HTTP de autenticação (+5 p.p. no escopo de integração)

- validar payload inválido no limite HTTP e impedir chamada ao serviço;
- validar conclusão de primeiro acesso com resposta `204`;
- validar solicitação de reset com resposta `202` e rate limiting acionado;
- validar logout, revogação do refresh e expiração do cookie;
- manter o contexto Spring Security real, isolando apenas serviços de domínio.

Foram adicionados contratos HTTP para primeiro acesso, reset, login, refresh,
logout, `/auth/me`, falha de autenticação e rate limiting. Os testes validam
status, JSON, cabeçalho `Set-Cookie`, permissões, chamadas de serviço e que a
validação rejeita payload inválido antes de alcançar o domínio. O incremento é
de integração real, não apenas chamada direta ao controller.

O ciclo elevou a cobertura de integração de 26,04% para 35,52% em linhas, de
17,06% para 23,41% em branches e de 28,08% para 38,92% em métodos, superando
a meta de 5 p.p. nas três métricas. O `clean verify` permaneceu verde, com
Spotless, Checkstyle, PMD e ArchUnit aprovados.

### Ciclo 7 — persistência administrativa e regressão de convites

- exercitar convite, busca e desativação com `AdminUserService` real;
- validar reset de senha com repositórios JPA e token persistido;
- validar duplicidade de e-mail no banco;
- executar PIT no mesmo ciclo e revisar mutações introduzidas pela correção.

O teste `AdminUserServicePersistenceIT` encontrou e protegeu um defeito real:
ao substituir um convite existente por um token de reset, o banco podia tentar
inserir o novo registro antes de remover o antigo, violando a restrição única
por usuário. `FirstAccessService` agora faz `flush` após remover o token
anterior, e os testes unitário e de integração protegem esse comportamento.

A cobertura de integração passou de 35,52% para 49,12% em linhas, de 23,41%
para 28,17% em branches e de 38,92% para 47,06% em métodos. O incremento
superou aproximadamente 5 p.p. nas métricas planejadas, com linhas e métodos
acima da meta e branches em 4,76 p.p.

O PIT foi executado após o ciclo: 182 de 193 mutações mortas (94%), test
strength de 96% e três mutações sem cobertura, mantendo o gate aprovado. A
mutação do `flush` foi inicialmente sobrevivente porque PIT não executa os
testes Failsafe; foi adicionada uma asserção unitária específica para garantir
que a regressão também seja protegida pelo mutation testing.

### Ciclo 8 — fluxo administrativo HTTP completo

- validar convite administrativo pelo endpoint protegido por `ROLE_ADMIN`;
- validar listagem e consulta de convites pendentes;
- validar renovação e cancelamento de convite;
- validar desativação e reset pelo limite HTTP;
- validar paginação inválida e executar PIT ao final do ciclo.

O teste `AdminUserControllerPersistenceIT` cobre o fluxo Controller → Service →
Repository com persistência real e contexto de segurança ativo. A cobertura de
integração evoluiu de 49,12% para 54,53% em linhas, de 28,17% para 30,56% em
branches e de 47,06% para 54,90% em métodos. O ganho de branches foi de 2,39
p.p. neste incremento, mas o acumulado do ciclo administrativo ultrapassou a
meta de 5 p.p. desde a medição anterior do ciclo.

O PIT foi executado novamente e permaneceu aprovado: 182 de 193 mutações
mortas (94%), test strength de 96% e três mutações sem cobertura classificadas.

### Ciclo 9 — contratos HTTP de epidemiologia

- validar municípios disponíveis pelo `EpidemiologyService` real;
- validar drilldown de bairro indisponível sem consultar o repositório;
- validar endpoints de doenças e metadados;
- executar `clean verify` e PIT antes de aceitar o incremento.

O teste `EpidemiologyControllerIntegrationIT` usa o contexto Spring Security,
`MockMvc` e o serviço epidemiológico real, isolando somente o repositório JDBC
para controlar os dados do contrato. A cobertura de integração subiu de
54,53% para 64,86% em linhas, de 30,56% para 39,68% em branches e de 54,90%
para 67,16% em métodos.

O PIT foi executado neste ciclo e permaneceu aprovado com 182 de 193 mutações
mortas (94%), test strength de 96% e três mutações sem cobertura já
classificadas.

### Ciclo 10 — autenticação JWT no fluxo protegido

- validar JWT real em endpoint administrativo protegido;
- validar token inválido como acesso anônimo;
- validar usuário autenticado sem a role `ADMIN` como `403`;
- executar `clean verify` e PIT no mesmo ciclo.

O teste `JwtAuthenticationIntegrationIT` usa `JwtService`,
`JwtAuthenticationFilter`, `CustomUserDetailsService`, banco H2 e `MockMvc`
reais. A cobertura de integração evoluiu de 64,86% para 69,90% em linhas, de
39,68% para 43,65% em branches e de 67,16% para 70,59% em métodos.

O PIT foi executado após o incremento e permaneceu aprovado: 182 de 193
mutações mortas (94%), test strength de 96% e três mutações sem cobertura
classificadas. O próximo ciclo deve priorizar branches restantes do fluxo de
autenticação e sessões de refresh.

### Ciclo 11 — refresh e sessões persistentes

- validar rotação de refresh token com sessão persistida;
- validar revogação persistente no logout;
- validar rejeição de refresh expirado;
- validar JWT de usuário desativado;
- executar `clean verify` e PIT no mesmo ciclo.

Os testes `RefreshSessionIntegrationIT` e `JwtAuthenticationIntegrationIT`
exercitam a cadeia HTTP com `RefreshSessionService`, `RefreshSessionRepository`,
`JwtService` e `JwtAuthenticationFilter` reais. A cobertura de integração
subiu de 69,90% para 77,83% em linhas, de 43,65% para 49,60% em branches e
de 70,59% para 75,00% em métodos.

O PIT foi executado após o incremento e permaneceu aprovado: 182 de 193
mutações mortas (94%), test strength de 96% e três mutações sem cobertura
classificadas.

### Ciclo 12 — primeiro acesso e reset persistentes

- concluir primeiro acesso pelo endpoint HTTP com token persistido;
- concluir reset de senha e reativar a conta persistida;
- solicitar reset para conta ativa e validar token e e-mail;
- executar `clean verify` e PIT no mesmo ciclo.

O teste `FirstAccessIntegrationIT` usa `FirstAccessService`, `UserService`,
repositórios JPA e `MockMvc` reais, mockando somente o gateway de e-mail. A
cobertura de integração subiu de 77,83% para 83,50% em linhas, de 49,60%
para 52,78% em branches e de 75,00% para 78,43% em métodos.

O PIT foi executado após o incremento e permaneceu aprovado: 182 de 193
mutações mortas (94%), test strength de 96% e três mutações sem cobertura
classificadas.

## Critérios de aceitação

- cada ciclo aumenta a métrica planejada em aproximadamente 5 p.p.;
- nenhum teste é criado apenas para executar uma linha sem verificar um
  comportamento observável;
- `./mvnw verify` permanece verde;
- cobertura global não pode regredir abaixo da linha de base protegida;
- os relatórios unitário, integração e global são publicados no CI.
