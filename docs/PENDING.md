# Pendências e dependências (21/10/2026)

Estado do que ficou aberto depois da rodada de testes e do port dos cartões de entrada.

## 1. RSS / News Feed — portado, medido e **apto a promover** (aguarda a bateria física)

**O RSS entrou no repositório geral**: commit `46fccd0` ("cinematic RSS news row on Home (v2.5, off
by default)"), merge da branch `feature/rss-cinematic-home`, em 21/10/2026.

**Port para a Eddy TV**: branch `feat/rss-news`. Benchmark completo em
[`benchmarks/results/2026-10-21-rss-row.md`](../benchmarks/results/2026-10-21-rss-row.md).

### O que o carrossel custa hoje (BeyondTV2, mesmo APK, só a preferência muda)

| Métrica | Desligado | Ligado |
|---|---:|---:|
| Quadros atrasados em regime | 0,38–0,77% | **0,0–3,3%** (mediana 0,8%) |
| Views | 104 | 105 |
| PSS | 27,1 MB | 33,2 MB (**+6,1 MB**) |
| Cold start (p50) | 437 ms | 510 ms (**+73 ms**) |

O jank deixou de ser um problema; o preço que sobra é memória e partida.

### O que estava errado (todas as causas medidas, nenhuma estimada)

| Correção | Ganho |
|---|---:|
| Remover o `<shape>` com gradiente full-bleed (1700×250 em software, todo quadro) | 42 pp |
| Remover `shadowRadius` dos quatro TextViews (software) | 34 pp |
| Um TextView de dots em vez de um por manchete (28 views a menos) | 5–20 pp |
| Desenhar pelo `Path` arredondado em vez de `canvas.clipPath` (não acelerado nesta GPU) | ~8 pp |
| Cachear os dois `LinearGradient` do scrim em `onSizeChanged` | ~1,5 ms/quadro |
| Assar recorte, escala e fades no bitmap, na thread de decode | 2 preenchimentos a menos por quadro |
| Camada de hardware no carrossel (a animação da grade o redesenhava) | ~2–3 ms/quadro |

`setClipToOutline` foi tentado no lugar do `clipPath` e foi **pior** (83%): força uma camada por
quadro. Está registrado no benchmark para não ser repetido.

### Por que ainda não está no release

Não é mais o jank. Falta a bateria que nenhum recurso novo dispensa: **soak de 8 h, standby, boot a
frio, controle físico e fonte HDMI real**, além do retorno à Home (Settings → Home) com a linha
ligada, que não foi medido. O recurso é opt-in, desligado por padrão e reversível, então o custo de
+6,1 MB e +73 ms só aparece para quem o ligar — mas a promoção depende da bateria, não da minha
leitura dos números.

Referência de arquitetura: o PR #363 do upstream (`ref/prs/pr363.md`) mostra uma faixa de cartões,
mas lê o provedor de TV, não RSS, e foi fechado sem merge.

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