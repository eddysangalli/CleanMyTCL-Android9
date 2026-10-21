# Auditoria — Eddy TV × CleanYourTV 2.5.0

Data: 21/10/2026. Referências consultadas por tag e SHA, não pela `main`.

| Referência | SHA | Observação |
|---|---|---|
| upstream `v2.4.0` | — | base da release anterior |
| upstream `v2.5.0` | `9071ec8` | release publicada em 21/10/2026 |
| upstream `main` | `622789f` | 21/10/2026, **259 commits depois da 2.5.0** |

## A. Inventário do projeto

| Item | Estado |
|---|---|
| `main` | `4d676d6` (v0.5.9 + o teste do `tools/bench.py`) |
| `develop` | `1553a25` — **atrás da main** (sem o teste) |
| Branch de trabalho atual | `feat/rss-news` @ `088f1a4` |
| Tags | `v0.5.8`, `launcher-v0.5.8`, `v0.5.9`, `launcher-v0.5.9`, `v2.4.0` |
| Árvore | limpa |
| APK publicado da v0.5.9 | SHA-256 `1DAC16CEBF8F74DC50D1F5E8236C4B4BE3A492211DBB248E9E26B77CC2643119` |
| **APK instalado na TV** | `474f18d7…` — **NÃO é a v0.5.9 publicada**: é o build experimental do RSS (`feat/rss-news`) |
| Home | `br.eddy.tvhome/com.gathin.tvhome.MainActivity` |
| Acessibilidade | `KeyRemapService` habilitada **e vinculada** |
| Pacotes desabilitados | 23 |

**Primeira correção desta auditoria:** o relatório da v0.5.9 descreve o APK publicado, mas a TV
está com um build experimental. Qualquer número medido hoje na TV pertence ao `feat/rss-news` +
RSS ligado, não à v0.5.9.

### Branches: o que é exclusivo de cada uma

| Branch | Exclusivo | Situação |
|---|---|---|
| `fix/bench-accessibility` | `tv_acc_save/restore/recover`, 7 testes | **incorporada** na `main` |
| `fix/frame-matching` | `frame-match.awk`, 6 testes | **incorporada** |
| `fix/input-key-service-events` | `input_key_service.xml` sem eventos | **incorporada** |
| `exp/pr446-grid-single-pass` | padding no `onLayoutChildren` | **incorporada** (só o item 1) |
| `perf/apploader-cache` | dirty flag + `reorder()` | **incorporada** |
| `feat/hdmi-input-cards` | cards HDMI/AV | **incorporada** |
| `docs/v058-measurement-attribution` | histórico v0.5.2→v0.5.8 | **incorporada** |
| `release/v0.5.9` | merge de integração | **incorporada** |
| `feat/rss-news` | RSS completo | medida; **apta a promover** depois da bateria física |
| `feat/service-repair` | cópia da `main` para o teste do bench | sem trabalho próprio |
| `pr/bench-accessibility-fix` | correção do PR upstream #437 | enviada ao fork |

## B. Release 2.5.0 — o que realmente mudou

324 commits entre `v2.4.0` e `v2.5.0`. Estado real das PRs citadas:

### Já temos equivalente

| PR | Assunto | Nosso estado |
|---|---|---|
| **#583** | não carregar ícone quando há banner | **já fazemos**: `build()` só pede o ícone quando `banner == null` |
| **#584** | uma consulta LEANBACK por rebuild | **já fazemos**: `load()` faz uma `queryIntentActivities` |
| **#436** | anel de foco no layout inicial | **já temos** (contribuímos, merged) |
| **#435** | Home guard contra `FallbackHome` | **já temos** (contribuímos, merged) |
| **#437** | benchmark de latência | **nosso PR, merged** |

### Relevante e ainda não temos

| PR | Assunto | Ganho | Risco | Decisão |
|---|---|---|---|---|
| **#585** | soltar tiles sob pressão de memória | memória em segundo plano | baixo | **implementado nesta auditoria** |
| **#544** | pills atualizadas após o 1º frame | menos trabalho no resume | baixo | vale experimentar |
| **#569** | foco com 12 anéis finos | visual | baixo | apenas referência |
| **#570** | wallpaper 32 bits | visual | memória | rejeitado por desempenho |
| **#581** | desligar brilho ambiente | já temos no ultralight | — | duplicado |
| **#596** | AV sem estado de conexão | correção | baixo | vale portar |
| **#592** | teclas por scancode | controle | médio | precisa do controle físico |

### Abertas, ainda não incorporadas

| PR | Assunto | Decisão |
|---|---|---|
| **#446** | grade em uma passada + FBO + texturas | **parcial**: só o item 1 entrou; o resto fica experimental |
| **#523** | movimento de 90 ms | **não se aplica**: nosso Desempenho é instantâneo |
| **#595** | foco sem overshoot | apenas referência |
| **#535** | esconder HDMI inativo e atualizar o menu | **vale experimentar** para os cards |
| **#534** | escolher sintonizador | **pendente de evidência** — não testamos TV ao vivo |
| **#573** | esconder/desinstalar app pelo tile | vale portar |
| **#505** | dock com `fill_dock:false` | não se aplica |
| **#527** | paletas de tema | vale experimentar no modo Visual |
| **#621** | perfis por firmware exato | **vale portar** (segurança) |
| **#619** | restaurar componente Home exato | **vale portar** (recuperação) |
| **#497** | auditoria de voz/telemetria | vale experimentar, só leitura |
| **#618/#623/#624/#625** | Companion (ContentProvider, credenciais) | **rejeitado por superfície de segurança** |

## C. Depois da 2.5.0 (259 commits)

O upstream continua ativo. As merged depois da release incluem **#583, #584, #585, #586, #544,
#552, #596, #592, #556, #581, #569, #570, #620, #614, #580, #610, #434, #437, #567, #571, #630,
#616**. Nenhuma delas muda a decisão acima, exceto o #585, agora implementado.

**Não confundir:** `#552` (Home já visível) é diferente de `#538` (retorno de outro app, fechada).
Nosso `onResume` já restaura o foco sem reconstruir quando a lista não mudou.

## D. Matriz de decisão

| Origem | Recurso | Já temos? | Ganho esperado | Risco | Status | Decisão |
|---|---|---|---|---|---|---|
| #585 | soltar tiles sob pressão | não | memória em background | baixo | implementado, precisa de teste | **implementado** |
| #583 | ícone só sem banner | sim | — | — | — | já implementado |
| #584 | 1 query por rebuild | sim | — | — | — | já implementado |
| #446 | grade em 1 passada | parcial | −6 ms p50, −123 ms p99 | baixo | medido | experimental |
| #446 | FBO / texturas 128×72 | não | GPU | visual | não aplicado | pendente de captura |
| #544 | pills após 1º frame | não | CPU no resume | baixo | não medido | vale experimentar |
| #535 | HDMI dinâmico | não | UX | foco | não medido | vale experimentar |
| #621 | perfil por firmware | não | segurança | médio | não medido | vale portar |
| Companion | provider + credenciais | não | — | **alto** | — | rejeitado |

## E. Performance da v0.5.9 — o que os números provam e o que não provam

5 pares A/B, 25 amostras de cold start e 45 de retorno por lado:

| Métrica | v0.5.8 | v0.5.9 | Leitura |
|---|---:|---:|---|
| Cold start p50 | 447 | 446 | igual |
| Cold start máx | 589 | 552 | melhor |
| Retorno p50 | 34 | 30 | melhor |
| Retorno p95 | 164 | 177 | **+13 ms, não conclusivo** |
| Resposta p50/p95 | 18 / 22 | 18 / 22 | igual |
| PSS | 31 MB | 31 MB | igual |
| CPU ociosa | 12,8–16,3 % | 15,1–15,6 % | sobrepostas |

### As dez perguntas

1. **Por que o #446 entrou sem ganho conclusivo?** Entrou no combinado por decisão explícita, com
   o critério "não prejudica". O p50 ficou em −6 ms (1,2 %) e a cauda melhorou; não há ganho de
   mediana comprovado.
2. **Que parte do #446 entrou?** Só o item 1 — `applyGridPadding()` chamado no `onLayoutChildren`
   antes do `super`. **Não** entraram o FBO, as texturas 128×72, o `ofArgb` nem o
   `setItemViewCacheSize`.
3. **Por que o AppLoader entrou com p95 pior?** O +9 ms estava dentro da dispersão do lado A
   (127–174 ms). O ganho é arquitetural: uma consulta ao PackageManager a menos por retorno. Não há
   ganho de latência.
4. **O teste final bastou?** Para "não prejudicar", sim nas métricas medidas. **Não** cobre standby,
   boot, 8 h nem pressão de memória.
5. **O +13 ms no p95 é real?** Não conclusivo: as faixas se sobrepõem parcialmente (A 127–174,
   B 167–226) e B é consistentemente mais alto. Precisa de mais amostras.
6. **Mesma metodologia?** Sim: `frame-match.awk` (término do quadro) para as teclas e
   `am start -W` WaitTime para o cold start, nos dois lados.
7. **A lista continua atualizada?** Sim, verificado com o VLC (desabilitar remove o tile,
   reabilitar devolve).
8. **A redução de consultas é efetiva?** Por construção, sim. Não medida em CPU isoladamente.
9. **Custo acumulativo de memória?** PSS estável em 31 MB; não testado com dezenas de retornos.
10. **Complexidade sem benefício?** `reload()` vs `reorder()` acrescenta um conceito, mas é o que
    impede a lista de ficar velha. Aceitável.

## F. Funcionalidades

| Área | Estado | Evidência |
|---|---|---|
| Cards HDMI/AV | funciona | ADB + capturas; **falta o controle físico** |
| RSS | funciona na branch | medido em regime: **0–3,3% de jank** com a linha ligada, contra 0,38–0,77% desligada; +6,1 MB e +73 ms de cold start; **aguarda a bateria física** |
| Recuperação da Home | funciona | opção na Home + `tcl-restore.ps1` |
| Remapeamento Globoplay | funciona | abriu o UniTVnet em ~1 s |
| Acessibilidade | íntegra | habilitada e vinculada |
| TV ao vivo | **não testada** | sem antena |
| Voz e microfone | parcial | smoke abre; captação real não |
| Chromecast | descoberta sim, transmissão não | `mediashell` anuncia |
| Standby / boot frio | **não medidos** | — |

## G. Pendências

1. **Testar o #585 na TV** (implementado hoje, sem medição).
2. Controle físico: OK longo em Configurações, botão Input, menu de entradas.
3. Vídeo real nas portas HDMI.
4. Standby, boot a frio, 8 h.
5. ~~Benchmark do RSS ligado~~ - feito em 21/10 (benchmarks/results/2026-10-21-rss-row.md).
6. `develop` atrás da `main`.
7. A TV está com o build experimental, não com a v0.5.9 publicada.

## H. Commits e PRs

- PR upstream **#437** (nosso `tools/bench.py`): aberto, atualizado com a correção da
  acessibilidade e 5 testes.
- Comentários com evidência da V660 em **#666** e **#670**.
- Nenhuma PR nova aberta nesta auditoria.

## I. Próximas ações, por prioridade

| # | Ação | Por quê |
|---|---|---|
| 1 | Devolver a TV à v0.5.9 publicada | o estado atual é experimental |
| 2 | Medir o #585 com pressão de memória | é a única mudança de memória testável |
| 3 | Alinhar `develop` com a `main` | evita divergência |
| 4 | Bateria física do RSS (soak, standby, boot, controle) | o benchmark já foi feito; falta validar no aparelho |
| 5 | Portar #621 e #619 | segurança de recuperação |
| 6 | Testar o #544 | menos trabalho no resume |
