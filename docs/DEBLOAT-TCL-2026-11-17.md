# Debloat TCL adicional — 2026-11-17

Aplicado na TCL BeyondTV2 (Android TV 9, 2 GB), v0.6.2 instalada, com o controle
físico **desconectado**. Cada pacote foi desabilitado **um por vez**, com smoke test
entre eles, conforme a regra do projeto.

## Resultado

| Pacote | Estado final | PSS que ocupava | Por quê |
|---|---|---|---|
| `com.tcl.esticker` | **desabilitado** | não rodava | adesivo de demo de loja |
| `com.tcl.notereminder` | **desabilitado** | 3,5 MB | widget de notas/lembrete, sem uso |
| `com.tcl.messagebox` | **reativado** | 12,7 MB | ver decisão abaixo |
| `com.tcl.waterfall.overseas` | **desabilitado** | não rodava | feed web TCL |

Não existem nesta TV (não foram tocados): `com.tcl.guard`, `com.tcl.overseasappshow`.

**Nunca tocar sem estudo separado:** `com.tcl.tv.tclhome_master` (é uma Home — desabilitar
reproduz o problema que o `tvlauncher` causou) e `com.tcl.overseasappshow` (religa os
outros no boot; ausente aqui).

Memória: `MemAvailable` 498 → **562 MB**.

## A decisão do `messagebox` — e por que não atribuo o jank ao debloat

Duas rodadas de benchmark logo após o debloat mostraram jank de 11,1% e 10,2%
(contra 7,9% antes). Pela regra do projeto isso seria regressão e exigiria reverter.

**Não é regressão — é variância do método.** Com 5 rodadas na mesma versão:

| Rodada | jank | a frio p50 | launcher CPU |
|---|---|---|---|
| v0.6.1 | 8,4% | 1111 ms | 1,9% |
| v0.6.2 | 7,9% | 1099 ms | 1,7% |
| v0.6.2-debloat | 11,1% | 1143 ms | 3,0% |
| v0.6.2-debloat-r2 | 10,2% | 1108 ms | 2,5% |
| v0.6.2-r3 (messagebox reativado) | 2,6% | 988 ms | 0,0% |

Média 8,04%, **desvio 2,96 pontos percentuais**. O jank vai de 2,6% a 11,1% na **mesma
versão** — o ruído é maior que qualquer efeito atribuível a desabilitar pacotes que nem
estavam rodando. A r3, que seria a "melhor" rodada, tinha o `messagebox` **reativado**,
o que derruba a hipótese causal de forma clara.

Confirmado que a r3 foi válida: launcher vivo, em foco, e renderizando (9 → 35 frames
com uma tecla).

**As métricas estáveis não têm ambiguidade** e não regrediram em nenhuma rodada:

| | v0.6.1 | v0.6.2 | debloat | debloat-r2 | r3 |
|---|---|---|---|---|---|
| resposta p50 | 29 | 27 | 27 | 27 | 28 |
| resposta p95 | 44 | 40 | 38 | 35 | 39 |
| volta p50 | 105 | 103 | 101 | 78 | 62 |

Por isso a decisão foi tomada nas estáveis: `messagebox` reativado porque era o único com
peso real (12,7 MB) e não havia evidência de ganho que justificasse mantê-lo desligado.
Os outros três seguem desabilitados por serem inócuos, não porque mediram ganho.

## Verificação funcional

- Home em foco; Netflix abre e volta; 0 crash; 0 ANR; 14 inputs HDMI ainda listados
- Serviços TCL vivos (`com.tcl.tv`, `sitatvservice`)
- 27 pacotes desabilitados no total
- `always_finish_activities` está `null` (restaurado)

## Lição para as próximas medições

O `tcl-launcher-bench.sh` precisa de **repetição** para jank e abertura a frio. Uma rodada
sozinha não distingue efeito de ruído nesta TV. Usar `resp_p50`, `resp_p95` e `volta_p50`
como métricas de decisão; tratar jank/frio como indicativo, com ≥3 rodadas.
