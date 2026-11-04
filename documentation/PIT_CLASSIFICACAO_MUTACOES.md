# Classificação das mutações sobreviventes

Este documento registra a revisão do relatório PIT do ciclo 5. O objetivo é
impedir que a porcentagem seja aumentada por testes artificiais.

## Resultado atual

- 191 mutações geradas;
- 180 mortas;
- mutation score: 94%;
- test strength: 96%;
- 3 mutações sem cobertura;
- 8 mutações sobreviventes.

O gate de 60% continua ativo no Maven.

## Mutações sem cobertura

| Local | Classificação | Decisão |
|---|---|---|
| `EpidemiologyService.metadata:95` | Equivalente no fluxo atual | O `groupingBy` já produz uma entrada única por ano; o merge do `toMap` não é alcançável com o contrato de `metadata()`. Não criar teste artificial para forçar uma estrutura impossível. |
| `FirstAccessService.cancel:166` | Irrelevante para o comportamento | O callback `ifPresent` é `void`; o método já é validado pelo estado do token e pela interação com o repositório. Um retorno nulo mutado não possui efeito observável. |
| `FirstAccessService.renew:152` | Infraestrutura do stream | O `map(this::view)` é exercitado quando a renovação retorna o convite. O mutante de retorno nulo no callback não altera o contrato válido; o caminho de ausência já é protegido por `orElseThrow`. |

Essas mutações permanecem visíveis no relatório e não são mascaradas por uma
exclusão ampla do PIT.

## Sobreviventes

| Local | Classificação | Decisão |
|---|---|---|
| `AdminUserService.invite:52` | Equivalente ao default da entidade | `User.ativo` nasce `true`; o setter repete o valor padrão e não altera o estado observável. |
| `AdminUserService.invite:53-54` | Equivalente ao default da entidade | `createdAt` e `updatedAt` são inicializados no construtor sem argumentos; a atribuição atual usa o mesmo instante lógico esperado para uma nova entidade. |
| `AuthRateLimiter.check:52` | Estado interno sem efeito externo neste ponto | Remover a limpeza do mapa antes do retorno não muda o resultado da chamada expirada nem o limite subsequente, que recria a janela. Não expor o mapa apenas para matar o mutante. |
| `FirstAccessService.issue:43` | Infraestrutura criptográfica | A mutação remove `SecureRandom.nextBytes`; não há teste determinístico seguro que prove entropia de uma amostra. O contrato observável já valida token não vazio, hash e link. |
| `FirstAccessService.issuePasswordReset:66` | Infraestrutura criptográfica | Mesmo caso da emissão de convite: a aleatoriedade não deve ser validada comparando dois valores aleatórios. |
| `RefreshSessionService.randomToken:106` | Infraestrutura criptográfica | A geração aleatória é um detalhe de segurança não determinístico; o contrato valida formato, hash e uso do token. |
| `RefreshSessionService.revoke:75` | Mutação equivalente para entradas cobertas | Entradas nulas e em branco são testadas, assim como tokens válidos; a alteração do operador não produz uma diferença de domínio adicional sem duplicar os cenários. |

Os sobreviventes acima não justificam reduzir o limiar, remover as asserções ou
adicionar testes que só observem implementação. Se o contrato de aleatoriedade,
limpeza de janelas ou datas padrão mudar, a classificação deve ser revisada.

## Regra para os próximos ciclos

Uma mutação sobrevivente só pode ser excluída após demonstrar que não existe
diferença observável válida ou que o caminho é estruturalmente inalcançável.
Caso contrário, o teste deve ser reescrito para verificar a regra de negócio,
ou o código deve ser corrigido quando a mutação revelar um defeito real.
