# Pendências e dependências (21/10/2026)

Estado do que ficou aberto depois da rodada de testes e do port dos cartões de entrada.

## 1. RSS / News Feed — portado, experimental, **não promovido**

**O RSS entrou no repositório geral**: commit `46fccd0` ("cinematic RSS news row on Home (v2.5, off
by default)"), merge da branch `feature/rss-cinematic-home`, em 21/10/2026.

**Port para a Eddy TV**: branch `feat/rss-news`.

### O benchmark que faltava (21/10/2026, BeyondTV2)

Mesmo APK, só a preferência muda. Jank medido em regime: Home aberta, 20 s de estabilização,
`gfxinfo reset`, 20 movimentos com o controle.

| Configuração | Jank | Views | PSS |
|---|---:|---:|---:|
| RSS desligado | **0,58 %** | 105 | 31,3 MB |
| RSS ligado (estado do primeiro port) | **83,6 %** | 133 | 38,1 MB |
| RSS ligado, sem o gradiente full-bleed | 41,2 % | — | — |
| RSS ligado, sem gradiente e sem sombra no texto | 7,3 % | — | — |
| Depois do pré-escalonamento do bitmap | 6–33 % (variável) | — | — |

**Duas causas encontradas e corrigidas**, ambas acrescentadas por mim no port, não no original:

1. **`<shape>` com gradiente full-bleed** sobre o carrossel: um `GradientDrawable` de 1700×250
   redesenhado em software a cada frame. Custava **~42 pontos percentuais** de jank. Removido; o
   scrim interno do `NewsBackdropView` já dá o contraste necessário.
2. **Sombra (`shadowRadius`) nos quatro TextViews**: desenhada em software. Custava **~34 pontos
   percentuais**. Removida.
3. **Bitmap reescalonado a cada frame**: o `drawPhoto` aplicava uma matriz de `centerCrop` em cada
   `onDraw`. Agora o recorte é feito uma vez, no worker, e o `onDraw` é um blit 1:1.

**Ainda não está pronto.** Mesmo corrigido, o carrossel fica entre 6 % e 33 % de quadros atrasados
(variação entre corridas), contra 0,58 % com o RSS desligado. O p50 do frame sobe de 14 para ~22 ms.
A causa restante não foi isolada: não é a foto (testado desligada) nem a rotação automática
(testado desligado).

**Decisão: o RSS continua experimental e fora do release.** Ele custa 6,8 MB de PSS a mais e uma
cauda de quadros que eu não consegui eliminar. Voltar a trabalhar nisso exige uma sessão dedicada a
profilar o `onDraw` do carrossel, não a ajustes às cegas.

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
