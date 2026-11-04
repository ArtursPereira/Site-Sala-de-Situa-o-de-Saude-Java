# Plano incremental para os achados de arquitetura, segurança e testes

Este plano transforma a revisão comparativa entre a `main` original e a
branch de referência em incrementos implementáveis. A referência de Artur é
usada como fonte de ideias, não como fonte para copiar código ou migrações sem
compatibilidade com o banco existente.

## Regras de execução

Cada incremento deve:

1. tratar uma regra ou risco claramente definido;
2. alterar código e testes na mesma etapa;
3. preferir resultado observável: resposta HTTP, estado persistido ou efeito
   externo;
4. executar `./mvnw clean verify`;
5. executar PIT quando a mudança alterar regra de domínio ou segurança;
6. atualizar esta documentação com o resultado;
7. gerar um commit semântico isolado.

Testes que verificam somente chamadas internas não são suficientes quando a
regra pode ser observada no estado final ou na resposta da aplicação.

## P0 — segurança e consistência do ciclo de vida

### P0.1 — impedir reativação indevida por primeiro acesso ou reset

Status: concluído no incremento `fix(auth): preserve administrative account blocks`.

- manter `ativo` como decisão administrativa de acesso;
- primeiro acesso configura nome e senha, mas não desbloqueia uma conta;
- reset de senha altera a credencial, mas não desbloqueia uma conta;
- garantir que conta desativada continue sem login e sem refresh;
- adicionar testes unitários e de integração para o estado persistido.

Critério de aceite: uma conta desativada permanece `ativo=false` depois de
concluir primeiro acesso ou reset e suas tentativas de autenticação falham.
Os testes unitários e de integração confirmam que o estado desativado não é
alterado.

### P0.2 — consumo único de tokens sob concorrência

Status: concluído no incremento `fix(auth): serialize first-access token consumption`.

- adicionar consulta com lock pessimista ou atualização condicional atômica;
- manter validação, alteração da credencial e consumo na mesma transação;
- rejeitar a segunda requisição concorrente sem alterar novamente a conta;
- testar no PostgreSQL.

Critério de aceite: duas requisições simultâneas com o mesmo token produzem
exatamente uma conclusão bem-sucedida. A consulta de conclusão usa lock
pessimista e o comportamento foi validado em PostgreSQL via Testcontainers.

### P0.3 — identidade imutável no JWT

- Status: concluído no incremento `fix(auth): bind jwt identity to user id`.

- usar o ID do usuário como `sub`;
- carregar o usuário pelo ID no filtro;
- definir estratégia de transição para tokens emitidos com e-mail;
- avaliar versão de credencial para invalidar access tokens após reset.

Critério de aceite: reutilizar um e-mail não reutiliza a identidade de um JWT
antigo e tokens antigos seguem a política de transição documentada. O filtro
agora exige um `sub` numérico e tokens legados que usavam e-mail são rejeitados
como inválidos; não há compatibilidade silenciosa com a identidade anterior.
O versionamento de credencial para revogação imediata de access tokens após
reset permanece como evolução complementar de segurança.

### P0.4 — revogação consistente de sessões

Status: concluído no incremento `fix(auth): revoke sessions after password reset`.

- revogar refresh sessions ao concluir reset;
- garantir persistência da revogação quando um token expirado ou reutilizado
  gerar erro;
- testar rotação concorrente e reutilização de refresh.

Critério de aceite: reset invalida sessões anteriores e a revogação não é
desfeita pelo rollback da resposta de erro. O reset agora chama
`RefreshSessionService.revokeAllForUser`, com cobertura unitária e integração
persistida. A rotação inválida usa `noRollbackFor` para preservar a revogação
de refresh expirado ou reutilizado, também validada por integração.

### P0.5 — geração de tokens testável

Status: concluído no incremento `refactor(auth): make token generation testable`.

- extrair uma abstração de geração segura de tokens;
- manter `SecureRandom` na produção;
- usar fonte determinística somente nos testes;
- testar que os bytes aleatórios influenciam o token e o hash persistido.

Critério de aceite: a remoção da geração de bytes faz um teste de segurança
falhar sem depender de probabilidade. `SecureRandomTokenGenerator` permanece
como implementação de produção, enquanto o teste injeta bytes fixos e valida
o link enviado e o hash armazenado.

### P0.6 — probes de saúde funcionais

Status: concluído no incremento `fix(ops): expose actuator health probes`.

- liberar liveness e readiness explicitamente;
- manter endpoints de negócio protegidos;
- iniciar a imagem com dependências de teste;
- executar smoke test real dos endpoints de saúde.

Critério de aceite: o healthcheck configurado no Docker passa em uma instância
saudável sem credenciais e endpoints de negócio continuam protegidos. As
probes foram testadas por HTTP com PostgreSQL real em container; liveness e
readiness retornaram 200 e um endpoint administrativo sem credencial retornou
401.

## P1 — testes que comprovam comportamento

### P1.1 — login completo

Status: concluído no incremento `test(auth): cover the real login flow`.

Cobrir PostgreSQL, Flyway, senha codificada, login HTTP, JWT retornado,
`/auth/me`, autorização administrativa, senha inválida, usuário desativado,
token expirado e assinatura inválida. Esses cenários foram implementados em
`AuthenticationFlowPostgresIT`; a integração também revelou e corrigiu o
retorno 500 indevido para `DisabledException`, que agora retorna 401. PIT
permaneceu em 94% de mutações eliminadas e 96% de test strength.

### P1.2 — qualidade dos testes unitários

Preservar testes de regras úteis, mas usar mapper real quando necessário,
capturar entidades persistidas, conferir campos alterados, verificar ausência
de efeitos e reduzir mocks sem comportamento.

### P1.3 — convites e administração

Documentar e testar convidar, renovar, cancelar, expirar, desativar usuários,
proteger o último administrador e definir a política para administradores.

### P1.4 — reset voluntário e administrativo

Separar os dois fluxos, verificar a senha com `matches`, definir quando o
login é bloqueado e revogar sessões de acordo com P0.4.

### P1.5 — unicidade normalizada de e-mail

Status: concluído no incremento `fix(user): enforce normalized email uniqueness`.

Auditar colisões, normalizar dados e criar índice único compatível com a
normalização adotada. A migração V6 falha se encontrar colisões após
`trim`/`lower`, normaliza os valores existentes e cria um índice único por
`LOWER(email)`. O service traduz violações concorrentes para conflito de
cadastro, validado com PostgreSQL e Testcontainers.

### P1.6 — contrato web

Status: concluído no incremento `fix(auth): secure browser authentication contract`.

`/api/v1/auth` passou a ser a rota canônica; o alias `/auth` foi removido.
Refresh e logout usam o cookie `nss_refresh` com `HttpOnly`, `Secure`
configurável, `SameSite=Strict`, expiração configurável e remoção explícita.
Operações baseadas em cookie exigem o token CSRF enviado pelo cookie
`XSRF-TOKEN` e pelo header `X-XSRF-TOKEN`; login, primeiro acesso e reset
público permanecem isentos por não dependerem de cookie de sessão. O CORS
agora permite `PATCH`, credenciais e o header CSRF. Os contratos HTTP cobrem
emissão do token, rejeição sem CSRF, logout, refresh e preflight com `PATCH`.

Validação: `./mvnw -B verify` passou com 97 testes unitários e 48 testes de
integração; PIT passou com 182/193 mutações eliminadas e força de teste de
96%.

### P1.7 — rate limit e e-mail

Status: concluído no incremento `fix(auth): make rate limiting atomic and post-commit mail`.

O rate limit agora reserva a tentativa e incrementa o contador atomicamente sob
concorrência, tanto para login quanto para solicitação de reset. As chaves
expiradas são removidas durante as reservas e o mapa possui limite configurável
por `api.auth.rate-limit.maximum-entries` (padrão 10.000), com eviction da
entrada mais antiga quando necessário. A proteção continua sendo single-node;
ambientes com múltiplas instâncias ainda precisam de armazenamento
compartilhado, como Redis, para uma política global.

O envio de convite e reset foi movido para `afterCommit`: a transação grava o
token antes de publicar o e-mail, evitando que uma falha SMTP faça rollback do
estado ou envie um link para uma alteração não persistida. Fora de uma
transação, o serviço mantém envio imediato para usos síncronos controlados.
Falhas de SMTP após o commit não são reprocessadas automaticamente; devem ser
monitoradas e serão tratadas por outbox/retry em uma evolução operacional.

Validação: `./mvnw -B verify` passou com 99 testes unitários e 48 testes de
integração; PIT passou com 189/210 mutações eliminadas e força de teste de
93%.

## P2 — migrações e arquitetura

### P2.1 — reorganização por contexto

Status: concluído neste incremento.

O código foi reorganizado para que `analytics` e `user` sejam contextos
independentes dentro de `com.example.nss`. O contexto de usuário agora contém
`api/request`, `api/response`, `api/mapper`, `domain`, `application`,
`infrastructure`, `security`, `exception` e `web/controller`. Analytics segue
o mesmo padrão, incluindo `exception` e `infrastructure/mapper` para o
`EpidemiologyRowMapper`. Componentes transversais ficaram em `shared/web` e
`shared/notification`; `NssApplication` permaneceu na raiz.

O modelo JDBC `CasoResumo` foi separado do contrato HTTP
`CasoResumoResponse`, evitando que o repositório exponha DTO de API. O
`UserMapper` está em `user/api/mapper`, onde converte os contratos públicos
para o domínio de usuário.

### P2.2 — limites arquiteturais automatizados

Status: concluído neste incremento.

O ArchUnit agora valida que:

- `analytics` e `user` não dependem diretamente um do outro;
- controllers não acessam infraestrutura;
- application não acessa web;
- repositories não acessam web;
- domain não depende de camadas externas;
- api não depende de infraestrutura;
- a direção Controller → Application → Repository não é violada.

As classes de teste são excluídas da análise para evitar que a localização
física dos testes seja confundida com dependência de produção. A regra foi
executada com sucesso junto aos testes dos novos pacotes.

### P2.3 — migrações e contratos restantes

Próximos itens: preservar migrações aplicadas e criar somente versões novas,
testar banco vazio e atualização de banco existente no PostgreSQL, escolher
uma única representação de permissão e garantir que contratos documentados
tenham validação automatizada.

## P3 — analytics

- validar o schema real do pipeline;
- separar ausência de publicação de zero casos;
- resolver opções de filtro anunciadas e não aceitas;
- testar agregações, filtros combinados e cobertura territorial no PostgreSQL.

## P4 — qualidade e operação

- manter JaCoCo em 90%/75%/88% e PIT em 80% como baseline;
- ampliar PIT somente quando o escopo estiver estável;
- publicar relatórios no CI;
- testar imagem, smoke test, backup, restauração, rollback e observabilidade
  em homologação.

## Ordem dos incrementos

1. P0.1 — estado administrativo não é reativado por token.
2. P0.4 — reset revoga sessões.
3. P0.2 — token de uso único sob concorrência.
4. P0.6 — readiness/liveness reais.
5. P1.1 — login completo com PostgreSQL e Flyway.
6. P0.3 — identidade imutável no JWT.
7. P0.5 — geração de tokens injetável.
8. P1.5 — unicidade real de e-mail.
9. P1.6/P1.7 — contrato web, rate limit e e-mail.
10. P2/P3 — migrações, arquitetura e analytics.

Não serão usados incrementos de cobertura como objetivo isolado. A cobertura
deve crescer como consequência de testes que protegem riscos concretos.
