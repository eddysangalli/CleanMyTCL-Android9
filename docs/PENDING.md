# Pendências e dependências (21/10/2026)

Estado do que ficou aberto depois da rodada de testes e do port dos cartões de entrada.

## 1. RSS / News Feed — bloqueado por dependência

**Não existe implementação de RSS em nenhuma branch**, local ou remota, nem no repositório geral
(`prfork` → `eddysangalli/cleanyourtv`). Verificado em 21/10/2026 por `git ls-tree` em todas as
branches, procurando `rss`, `feed`, `news`:

| Onde | Resultado |
|---|---|
| `prfork/main` (repo geral) | nenhum arquivo |
| `prfork/contrib/*`, `prfork/pr/*` | nenhum arquivo |
| branches locais deste repo | nenhum arquivo |
| `launcher/app/src/main/java/com/gathin/tvhome/` | 13 classes, nenhuma de feed |

O último commit publicado no repositório geral continua sendo `a3f37b5` (os cartões HDMI).
Confirmado de novo em **21/10/2026, com `git fetch --prune`** nas duas origens: `prfork/main`
continua em `a3f37b5`, e o upstream avançou para `d27bb06` (traduções e README) sem nada de feed.

**Decisão:** não inventar uma implementação substituta. O RSS fica bloqueado até o módulo existir
no repositório geral; o port para a Eddy TV é uma branch própria, depois.

Referência para quando houver: o PR #363 do upstream (`ref/prs/pr363.md`) mostra a estrutura visual
de uma faixa de cartões, mas lê o provedor de TV, não RSS, e foi fechado sem merge.

## 2. Cartões de entrada — caminho pelo menu precisa do controle físico

O recurso funciona e foi validado na TV, mas a **tela de configuração foi aberta por ADB**, com a
activity exportada só durante o teste (`exported=false` no commit).

Motivo: `input keyevent --longpress` não chega como long press neste firmware (upstream #485), e o
`sendevent` no receptor IR foi instável nos testes.

**Falta:** segurar OK em Configurações com o controle e conferir que "Cartões de entrada" abre.
O resto do fluxo (renomear, ícone, mostrar, abrir) já foi verificado.

## 3. Benchmark de quadros — corrigido, e a história não é comparável

Os dois medidores discordavam: `tcl-launcher-bench.sh` casava a tecla com o primeiro quadro cujo
**IntendedVsync** vinha depois dela; `tools/bench.py` casa com o primeiro quadro que **terminou**
depois. No foco instantâneo o quadro que reage nasce do vsync anterior à tecla e só termina depois,
então a regra antiga perdia teclas e misturava dois métodos.

Corrigido em `scripts/frame-match.awk` (usado pelo script), com os três cuidados do `bench.py`:
término do quadro, janela entre o reset e o dump, e corte de 150 ms para tecla sem reação. Seis
testes em `tests/test_frame_match.py`.

**Consequência:** números medidos antes desta correção usam a regra antiga e **não são diretamente
comparáveis** com os de depois. Cada página de resultado diz com qual método foi medida. Na TV, a
regra nova deu o mesmo p50 (18 ms) com cauda melhor (p95 24 → 22; movimento máx 444 → 434).

## 4. #446 — branch experimental, não promovido

Branch `exp/pr446-grid-single-pass`. Com 5 pares A/B (25 amostras por lado) o ganho de abertura a
frio caiu para **−6 ms no p50 (−1,2 %)**, dentro do ruído. Views (96) e RenderTarget (4,83 MB) não
mudaram. Detalhes em [benchmarks/results/2026-10-20-pr446-grid-single-pass.md](../benchmarks/results/2026-10-20-pr446-grid-single-pass.md).

## 5. Medições antigas potencialmente afetadas

O `am force-stop` derrubava o serviço de acessibilidade e nenhum script o devolvia (corrigido nesta
rodada). Consequências para o histórico:

| Script | Efeito nos números | Efeito na TV |
|---|---|---|
| `tcl-coldstart.sh` | **nenhum**: o force-stop acontecia antes da medição, então a abertura era realmente fria | deixava o remapeamento do Globoplay desligado até alguém religar |
| `tcl-launcher-bench.sh` | **nenhum**, pelo mesmo motivo | idem |
| `tcl-bench.sh` (com `BENCH_LAUNCHER`) | **nenhum** | idem |
| `tools/bench.py` | **sim**: restaurava os serviços **antes** de medir, e escrever a setting inicia o processo — a "abertura a frio" era quente, e a lista inteira era substituída | podia apagar serviços de outros apps |

As medições históricas de abertura a frio por `tcl-coldstart.sh` e `tcl-launcher-bench.sh` seguem
válidas como **abertura a frio sem serviço de acessibilidade**. Elas não representam a operação
normal descrita no item 5.

## 6. Abertura fria x operação normal

Medido em 21/10/2026, mesma TV:

| Estado | WaitTime |
|---|---|
| **Operação normal** (serviço de acessibilidade ligado, processo vivo) | **79 / 83 / 90 / 105 / 76 ms → p50 ~83 ms** |
| **Abertura a frio real** (processo morto, serviço desligado) | ~430–480 ms |

Com o serviço de acessibilidade habilitado — o padrão — o processo da Home está sempre vivo, então
a "abertura" que o telespectador vê é a da primeira linha. Os ~450 ms são o pior caso, não o caso
típico. Qualquer número de "abertura a frio" no repositório deve dizer qual dos dois estados mediu.
