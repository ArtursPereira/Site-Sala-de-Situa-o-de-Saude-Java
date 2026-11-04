# Critérios de qualidade dos testes

## Princípio

JaCoCo mede se o código foi executado; não prova que o teste detectaria um
defeito. Por isso, aumento de linhas, branches ou métodos cobertos é apenas um
sinal de execução. O teste deve verificar uma regra observável da aplicação.

## Critério obrigatório por teste

Todo teste novo precisa:

- ter um nome que descreva o comportamento esperado;
- preparar uma situação representativa do domínio;
- executar uma ação da unidade ou fluxo sob teste;
- verificar uma saída, estado persistido, erro, efeito externo ou interação
  relevante;
- falhar se a regra descrita no nome for removida ou invertida.

Testes que apenas chamam um método sem uma verificação, ou que verificam
somente que nenhuma exceção foi lançada, são testes de fumaça e não contam
como evidência de comportamento, exceto no teste explícito de subida do
contexto Spring.

## Gates automatizados

1. `./mvnw verify` deve passar com JaCoCo, Spotless, Checkstyle e PMD.
2. JaCoCo deve proteger linhas, branches e métodos contra regressões, mas não
   será usado sozinho para aprovar a qualidade de um teste.
3. PIT deve executar mutações nos services de autenticação e analytics:
   `./mvnw -B test-compile org.pitest:pitest-maven:mutationCoverage`.
4. O build de mutation testing falha se o mutation score ficar abaixo de 60%
   ou o test strength ficar abaixo de 60%.
5. Cada mutação sobrevivente deve ser classificada como:
   - comportamento não protegido: adicionar ou reescrever teste;
   - mutação equivalente ou irrelevante: documentar a justificativa e excluir
     somente aquela classe, método ou mutador;
   - defeito no código: corrigir a implementação e manter o teste de
     regressão.

## Revisão sistemática

Antes de aceitar um teste, a revisão deve responder:

- Qual regra de negócio este teste protege?
- Qual regressão ele detectaria?
- Qual é a asserção principal e por que ela é suficiente?
- O teste continua significativo se a implementação interna for refatorada?
- O teste mata as mutações relevantes no método?
- Ele duplica outro teste sem adicionar uma decisão, entrada ou efeito novo?

Um teste que aumenta JaCoCo mas deixa mutações relevantes sobreviverem deve ser
reescrito ou removido; não se deve reduzir o limiar para acomodá-lo.

## Relatórios

- JaCoCo: `target/site/jacoco/`;
- PIT: `target/pit-reports/`;
- o relatório PIT é a fonte para localizar mutações sobreviventes e orientar
  o próximo teste útil.

Referências: [JaCoCo coverage counters](https://www.jacoco.org/jacoco/trunk/doc/counters.html),
[PIT Maven quick start](https://pitest.org/quickstart/maven/) e
[PIT basic concepts](https://pitest.org/quickstart/basic_concepts/).
