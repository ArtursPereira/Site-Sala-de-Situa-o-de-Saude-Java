# Checklist operacional de produção

Este documento define o que precisa ser verificado antes, durante e depois do
deploy do NSS. Os comandos usam variáveis de ambiente; nenhum segredo deve ser
gravado no repositório ou no histórico do shell.

## Antes do deploy

- [ ] Fixar a imagem por tag imutável ou digest, nunca por `latest`.
- [ ] Definir `SPRING_PROFILES_ACTIVE=prod`.
- [ ] Definir `JWT_SECRET` com pelo menos 32 bytes após decodificação Base64.
- [ ] Definir `SPRING_DATASOURCE_URL`, usuário, senha e, se necessário, usuário
      e senha Flyway separados.
- [ ] Definir `ANALYTICS_DATASOURCE_URL` e credencial somente leitura.
- [ ] Definir `CORS_ALLOWED_ORIGINS` somente para as origens oficiais.
- [ ] Definir `SECURE_COOKIES=true` (o perfil `prod` já força esse valor).
- [ ] Definir SMTP, `MAIL_FROM` e `FIRST_ACCESS_FRONTEND_URL` oficiais.
- [ ] Revisar `BOOTSTRAP_ADMIN_EMAIL` e `BOOTSTRAP_ADMIN_PASSWORD`; remover as
      variáveis depois do primeiro bootstrap quando a política operacional
      exigir isso.
- [ ] Confirmar que o banco possui backup recente e que a restauração foi
      testada no ambiente de homologação.
- [ ] Confirmar janela de deploy e plano de rollback para a imagem anterior.

## Smoke test pós-deploy

Substitua `BASE_URL` pela URL interna ou pública do serviço:

```bash
export BASE_URL=https://nss.exemplo.gov.br
curl --fail --silent "$BASE_URL/actuator/health/liveness" | jq .
curl --fail --silent "$BASE_URL/actuator/health/readiness" | jq .
curl --fail --silent "$BASE_URL/actuator/prometheus" >/dev/null
```

O endpoint de readiness deve retornar HTTP 200. O Prometheus deve conseguir
coletar métricas sem autenticação adicional, conforme a política do ambiente.
Endpoints de negócio devem ser validados com uma conta de teste não produtiva
ou com o fluxo de autenticação aprovado pela equipe responsável.

## Migrações e banco

O Hibernate está configurado com `ddl-auto=validate`; a alteração de schema é
responsabilidade do Flyway. Antes do deploy:

```bash
./mvnw -B verify
```

No ambiente de produção, revisar o histórico Flyway e o plano de execução da
migração antes de liberar tráfego. Nunca remover volumes para resolver falhas
de migração.

Exemplo de backup lógico PostgreSQL:

```bash
pg_dump --format=custom --file=nss-$(date +%F-%H%M).dump "$SPRING_DATASOURCE_URL"
```

Exemplo de restauração em banco isolado de validação:

```bash
createdb nss_restore_check
pg_restore --clean --if-exists --dbname=nss_restore_check nss-YYYY-MM-DD-HHMM.dump
```

A restauração deve ser executada primeiro em banco isolado. O resultado e o
tempo de recuperação devem ser registrados; este passo não é substituído pelo
`mvn verify`.

## Rollback

1. Interromper o rollout se readiness ou erro de aplicação excederem o limite
   definido pelos responsáveis.
2. Reapontar o deployment para a imagem anterior conhecida como saudável.
3. Se uma migração incompatível já foi aplicada, executar o procedimento de
   rollback da própria migração ou restaurar backup conforme o plano aprovado.
4. Repetir liveness, readiness, login, refresh e `/auth/me`.
5. Registrar causa, horário, versão e impacto.

Não fazer downgrade automático do schema sem uma migração reversa revisada.

## Observabilidade e segurança

- [ ] Alertar indisponibilidade de liveness/readiness.
- [ ] Alertar erro de conexão com banco operacional e analítico.
- [ ] Alertar falhas SMTP separadamente; o health check de mail está desativado
      para não reiniciar o serviço por indisponibilidade de e-mail.
- [ ] Monitorar taxa de 401/403, 5xx, latência e limite de login.
- [ ] Garantir que logs não contenham JWT, refresh token, senha ou dados
      pessoais desnecessários.
- [ ] Executar teste de carga e revisão de segurança antes da primeira
      exposição pública.

## Critério de liberação

O checklist só pode ser marcado como concluído quando os itens dependentes do
ambiente real tiverem evidência anexada ao processo de deploy. A aprovação de
`mvn verify`, cobertura ou PIT não substitui backup/restauração, carga,
segurança, observabilidade e rollback.
