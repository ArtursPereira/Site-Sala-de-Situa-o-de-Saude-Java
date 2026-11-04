# Apresentação NSS V1

Os slides são escritos em `apresentacao.qmd` usando Quarto + Reveal.js.

## Renderização

O pacote `quarto` atualmente disponível no Nixpkgs possui uma incompatibilidade
com a versão do Pandoc empacotada. Use o shell local, que aplica a correção
necessária:

```bash
nix-shell shell.nix --run 'quarto preview apresentacao.qmd'
```

Para gerar o HTML definitivo:

```bash
nix-shell shell.nix --run 'quarto render apresentacao.qmd'
```

Para apresentar, abra o HTML gerado. Para exportar PDF, use o modo de impressão
do Reveal.js (`E`) e imprima em paisagem com margens nulas e gráficos de fundo.

## Base da análise dos Parquet

Os números da seção de dados foram obtidos dos artefatos existentes em:

```text
NSS-pipeline/data/bronze/sinan
NSS-pipeline/data/silver/sinan
NSS-pipeline/data/gold/serving/sinan
```

Eles representam o estado dos Parquet analisados em 2026-10-08 e não substituem
uma auditoria histórica de todos os layouts já publicados pelo SINAN.
