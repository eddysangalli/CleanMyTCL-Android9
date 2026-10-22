# Pendências e dependências (21/10/2026)

Estado do que ficou aberto depois da rodada de testes e do port dos cartões de entrada.

## 1. RSS / News Feed - **publicado na v0.6.0**, aguardando a bateria física

O recurso entrou no release 0.6.0 (opt-in, desligado por padrão). Benchmark e método em
[`benchmarks/results/2026-10-21-rss-row.md`](../benchmarks/results/2026-10-21-rss-row.md); notas do
release em [`RELEASE-0.6.0.md`](RELEASE-0.6.0.md).

| Métrica (V660, mesmo APK) | Desligado | Ligado |
|---|---:|---:|
| Quadros atrasados em regime | 0,0–0,8% | 0,0–2,5% |
| Views | 104 | 105 |
| PSS | 28,3 MB | 34,4 MB (+6,1 MB) |
| Cold start (p50) | 440 ms | 513 ms (+73 ms) |

O jank saiu de 83,6% no primeiro port para ~1%, com sete causas medidas e corrigidas (gradiente
full-bleed, sombra nos textos, 25 TextViews de dots, `canvas.clipPath`, shaders por quadro, bitmap
reescalado por quadro e redesenho durante a animação de foco).

**Falta a bateria física:** controle físico, imagem real nas portas HDMI, standby, boot a frio, soak de
8 h e o retorno à Home com a linha ligada.

**Feed da Folha removido:** `feeds.folha.uol.com.br/folha/cotidiano/rss091.xml` parou em maio de 2015 e
continua respondendo 200; a linha mostrava notícias de onze anos atrás com links `http://` que o leitor
não abria. O cache ganhou versão (`CACHE_VERSION`) para não sobreviver à troca de catálogo.

## 1b. #585 (largar tiles sob pressão) - incluído, não exercitável por ADB

O `am send-trim-memory` recusa níveis de fundo nesta firmware mesmo com a Home atrás: o serviço de
acessibilidade mantém o processo em `curProcState=2`. O gancho só roda sob pressão real do sistema e
não age com a Home visível (`started`). Não há número para mostrar; o que se sabe é que ele não pode
apagar a tela de quem está olhando.
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