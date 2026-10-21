# A/B — grade em uma passada (ideia do upstream PR #446) na TCL BeyondTV2

**Data:** 20/10/2026, 22:43–23:20. **TV:** TCL BeyondTV2, Android TV 9 (API 28), Realtek
RTD2851, firmware R851T02 V660, 2 GB, 32 bits.

**Pergunta:** aplicar o `paddingTop` da grade dentro do `onLayoutChildren`, antes de
`super.onLayoutChildren` (a ideia do PR #446 do upstream), muda alguma coisa nesta TV?

**Escopo:** só o item 1 do PR #446 — a passada dupla da grade. Os outros itens (FBO/
`hasOverlappingRendering`, texturas 128×72, `ofArgb`) **não** foram aplicados.

## Builds

| Lado | APK | SHA-256 |
|---|---|---|
| A — referência | v0.5.8 (code 30), o publicado | `D9EDA30FE285FC3AE643F26FB19C05121F30DC7E963DF4B6DB9F4A461A96EF10` |
| B — grade em uma passada | v0.5.8 + `onLayoutChildren` | `2C6CED9878203045E65D4136094B70C365DFA6EE06E517681FD2583F7839BD98` |

O hash do A bate com o que o `COMPATIBILITY.md` registra para a v0.5.8. Os dois builds foram
instalados com `adb install -r` na mesma TV, na mesma sessão.

## 1. `tcl-launcher-bench.sh` — 36 teclas, 10 voltas (uma corrida por lado)

Estado: papel de parede *Earth at night*, mola M3E, anel simples.

| Métrica | A (v0.5.8) | B (+ grade) |
|---|---:|---:|
| Resposta da tecla p50 / p95 / p99 / max | 18 / 24 / 25 / 25 ms | 18 / 21 / 30 / 30 ms |
| Movimento completo p50 / p95 / p99 / max | 427 / 443 / 444 / 444 ms | 428 / 437 / 444 / 444 ms |
| Quadros atrasados | 8,4 % de 927 | 5,1 % de 930 |
| Abertura a frio p50 / max | **436 / 535 ms** | **417 / 451 ms** |
| Volta ao Home p50 / p95 / max | 29 / 143 / 143 ms | 34 / 148 / 148 ms |
| CPU ociosa (total 4 núcleos) | 16,0 % | 14,1 % |
| CPU do launcher | 0 % | 0 % |
| PSS | 28 MB (26 MB após 10 voltas) | 28 MB (26 MB) |

## 2. `tcl-view-count.sh` — A-B-B-A, Views e memória de GPU

Método: `am force-stop`, abre a Home, espera 6 s, lê o meminfo **antes de navegar** (é onde o
upstream mediu 125 → 61), depois 10 movimentos para a direita e 10 para a esquerda e lê de novo.
Uma corrida por célula, na ordem A-B-B-A.

| Corrida | Build | Views (1º layout) | Views (após navegar) | PSS | RenderTarget | Quadros janky |
|---|---|---:|---:|---:|---:|---:|
| A1 | referência | 96 | 96 | 23 485 KB | 4,83 MB (20) | 9 (2,00 %) |
| B1 | + grade | 96 | 96 | 23 549 KB | 4,83 MB (20) | 46 (10,29 %) |
| B2 | + grade | 96 | 96 | 23 374 KB | 4,83 MB (20) | 7 (1,57 %) |
| A2 | referência | 96 | 96 | 23 432 KB | 4,83 MB (20) | 11 (2,48 %) |

## O que conclui

- **A abertura a frio melhorou**: p50 436 → 417 ms e o pior caso 535 → 451 ms (−84 ms no pior
  caso). É o alvo da mudança e o único sinal consistente.
- **Views não mudou**: 96 nos quatro casos, no primeiro layout e depois da navegação. O ganho
  de 125 → 61 Views que o PR relata **não se reproduz nesta TV**.
- **RenderTarget não mudou**: 4,83 MB em 20 entradas nos quatro casos. Isso é esperado — essa
  memória vem do item 2 do PR (o FBO do `GlassView`), que **não** foi aplicado aqui.
- **PSS e quadros atrasados**: sem sinal. O B1 marcou 10,29 % de janky contra 1,57–2,48 % nos
  outros, o que mostra que a métrica é ruidosa nesta janela; não serve para decidir.
- **Resposta da tecla**: p95 caiu (24 → 21 ms) e p99/max subiram (25 → 30 ms). Com uma corrida
  de 36 teclas por lado, é ruído.

## Limites

- **Uma corrida por lado** no `tcl-launcher-bench.sh`. A regra do projeto pede ≥ 5 repetições
  para a bateria principal e ≥ 3 corridas para a do launcher. A diferença de abertura a frio
  precisa ser repetida antes de entrar na `main`.
- As duas medições de PSS não são comparáveis entre si: o bench do launcher lê depois de 60 s
  com a tela parada (28 MB), e o `tcl-view-count.sh` logo depois de uma abertura a frio
  (~23,5 MB). Estados diferentes.
- O `dumpsys meminfo` do Android 9 reporta `Graphics: 0`; a memória de GPU só aparece no
  `dumpsys gfxinfo` (`GPU Caches: RenderTarget`), que **existe** nesta TV (ao contrário do
  Android 8 do teste do Philips no PR).
- O item 2 do PR (`hasOverlappingRendering() = false` no `GlassView`) fica para um experimento
  separado: o nosso `GlassView` desenha brilho, preenchimento, reflexo e borda sobrepostos, e
  desligar a composição por camada pode mudar a transparência nos modos Equilibrado e Visual.
  Precisa de captura comparada antes.

**Nada foi para a `main`.** A mudança está no branch `exp/pr446-grid-single-pass`, e a TV foi
devolvida ao APK publicado da v0.5.8 (hash `D9EDA30F…`).
