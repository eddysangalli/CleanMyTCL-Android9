# Auditoria do upstream CleanYourTV, dos forks e das contribuições de retorno — 20/10/2026

Escopo desta rodada: auditoria de código real do upstream (v2.4.0, `main` = `fd1ee0a`), levantamento
da rede de forks, integração dos PRs de @roky146, implementação das melhorias selecionadas e
preparação de contribuições de volta ao projeto original.

**Regra de ouro desta rodada:** nada de número de performance inventado. Onde não há medição
nossa, o texto diz "não medido nesta TV". Onde há medição, ela vem de
`benchmarks/results/2026-10-19-v0.5.6-wallpaper.md` (v0.5.6) e é rotulada com a versão.

- Nosso repositório: `eddysangalli/CleanMyTCL-Android9` — `main` em v0.5.6 (`94e4890`),
  `develop` em `3ccf9b7`, branch de integração `integrate/all-prs`.
- Upstream: `Gathin23/cleanyourtv` — `main` em `fd1ee0a`, tag `v2.4.0` (`69c93b6`).
- TV de referência: TCL BeyondTV2, Android TV 9 (API 28), Realtek RTD2851, firmware R851T02 V660,
  2 GB RAM, 32 bits. Launcher `br.eddy.tvhome`.

## 1. Inventário e proteção

| Item | Estado verificado em 20/10/2026 |
|---|---|
| Versão no código | `0.5.7`, `versionCode 29` (era 28 antes da integração) |
| Versão instalada na TV | `0.5.7`, `versionCode 29`, instalada 20/10/2026 11:38 |
| APK instalado na TV | SHA-256 `AAE067C60E8D1E0D90162BAA9DFB1DC87C2CFDDDC2ACB7CACB764D5A6FC7A712` (puxado da TV e conferido) |
| APK anterior (code 28) | SHA-256 `ACDF90EC631FB0FCBAECD012C371C8ABCBF9B5E2117EC00006C34943A30FAA5B`, cópia preservada em `medicoes/apks/tvhome-0.5.7-code28.apk` |
| APK publicado v0.5.6 | `medicoes/apks/tvhome-0.5.6.apk` (SHA-256 `8D5C…`), inalterado |
| `main` | `94e4890` (v0.5.6) — **não mexido** |
| `develop` | `3ccf9b7` — **não publicado nesta rodada** (ver seção 8) |
| Branch de integração | `integrate/all-prs`, com merges de #6, #7, #8, #10, #11 e da branch de auditoria |
| Sem(root)/sem desinstalação | A TV não tem root (`su` ausente, `adb root` negado); nada foi desinstalado, nenhum pacote do sistema foi desativado |
| Recuperação | `scripts/tcl-restore.ps1`, `tools/restore.sh` e o serviço de acessibilidade continuam presentes; o `FallbackHome` do firmware TCL segue disponível como última linha |

**Correção de dado obsoleto.** `docs/UPSTREAM-AUDIT-2026-10-20.md` falava em "versionCode 19";
o código já estava em 28 e agora está em 29. A linha do COMPATIBILITY que citava "v0.5.6
(versionCode 17)" como instalação atual foi atualizada para a instalação real.

## 2. Auditoria do upstream (v2.4.0)

### 2.1 O que mudou desde o nosso ancestral comum

Ancestral comum: `9ef40c1`. O upstream, depois disso, fez merge de 61 PRs e chegou à v2.4.0. O que
interessa ao nosso fork Android 9:

| Área | Mudança no upstream | Situação no Eddy TV | Decisão |
|---|---|---|---|
| Fontes | #225 (merged): cada peso do Outfit é construído uma vez por processo, em vez de `fontVariationSettings` por `TextView`; #370 (merged): pesos em cache entre layouts | Nosso fork ainda fazia `fontVariationSettings` em cada `TextView` | **Portado**: 5 recursos `font/outfit_*.xml` + layouts e estilos apontando para eles (`e32e409`) |
| Cor dos tiles | #370: amostragem de cor sem `Color.colorToHSV` por pixel | Nosso `AppLoader.vividColor` fazia HSV por pixel | **Portado** com aritmética inteira (`71af547`) |
| Home guard | #350 (merged): não desabilitar o dono do Home | Nosso `tools/disable.sh` não tinha a proteção | **Portado** com uma melhoria: o `FallbackHome` de vendor não conta como segundo launcher (`393a933`) |
| Recuperação | #317 (merged): validar o registro antes de reaplicar; #319 (merged): avisar quando `ro.adb.secure=0`; #318 (merged): fixar dependências | Nosso repositório não tem `tools/recovery.py` | **Não portado ainda**: exige um `recovery.py` novo, que é escopo de uma rodada própria |
| Biblioteca de apps | #305 (merged): biblioteca opcional de apps instalados | Não temos | Fora de escopo: 1284 linhas e uma tela nova |
| Reordenar | #299 (merged): reordenar pela TV; #340 (merged): editar o dock pela TV | Já temos reordenar por "segurar OK" e dock fixo | Equivalente em função; a forma é diferente |
| Grade | #293 (merged): `rows_below_dock`; #280 (merged): testes de limites | Nosso fork tem colunas 4/5/6 com testes próprios | Já resolvido no nosso lado |
| Foco | #289, #246, #138 (merged): rolagem e retorno de foco | Já portados anteriormente (docs de 19/10) | Mantido |
| Entradas/HDMI | #291 (merged), #339 (merged), #342 (merged) | #291 portado; #339 e #342 não se aplicam ao nosso conjunto de entradas | Mantido o que já existe |
| Desempenho | #39 (aberto), #150 (aberto) | Já temos `AppLoader` com cache e tiles em background (portado do #39 antes) e ciclo de vida leve | **Não reimplementar**: o conceito já está; o código upstream atual reescreve a arquitetura, o que seria uma regressão de estabilidade no nosso fork antigo |

### 2.2 PRs abertos que valem a pena acompanhar

| PR | Autor | Estado | Interessante para nós? |
|---|---|---|---|
| #370 | @leovvay | **merged** (o texto do PR ainda aparecia como aberto no dia) | Já portado |
| #373 | @luyzfernando08 | aberto | Data em português com o dia da semana por extenso. Pequeno, mas a nossa Home é em pt-BR e a data curta funciona bem: **dispensável** |
| #176 | @dennyschwender | aberto, 24 arquivos | Mapa de teclas para abrir apps. Complexo demais e redundante com o remapeamento de tecla que já temos |
| #311 | @nikhiltalreja | aberto, 18 arquivos | Pill de Bluetooth. Específico de remotes BT; nossa TV é IR |
| #327 | @UavL | aberto, 21 arquivos | Segurar OK para mover/ info + fundo nas configurações. É a mesma ideia do #6/#7/#8 que acabamos de integrar |
| #307 | @PatrickNoFilter | fechado | Seletor de tema, wallpaper, mover ícones. Escopo grande; partes já existem no nosso menu |
| #39, #150 | @leovvay, @ptrinh | abertos | Reescrevem a arquitetura do launcher. Já temos o equivalente funcional |
| #299, #305, #323, #340, #342, #343, #344, #291, #328, #337, #339 | vários | merged/closed | Já revisados; nada que o nosso fork precise agora |

**Cobertura e limitação.** Foram lidos os diffs reais de #225, #350, #370, #39, #150, #277, #373 e
#307, além do diff completo de `upstream/main` contra `develop`. Não foi feita uma leitura linha a
linha dos 61 PRs abertos: a triagem usou tamanho do diff, tema e overlap com o que já temos. PRs de
perfil de aparelho (#325, #326, e os forks da rede) foram verificados como listas de debloat, sem
cópia automática.

### 2.3 Rede de forks

- Forks listados pela API: **1083** (a página inicial do repositório anuncia 1072; a API devolve
  alguns a mais, provavelmente forks criados durante a própria consulta).
- Com push depois da criação: **313**. A maioria são forks de arranque (criados e empurrados em
  minutos), sem mudança própria.
- Com branch padrão diferente de `main`: **10** — foram os candidatos reais de análise.
- `SHOWCASE.md` do upstream lista 19 projetos derivados, incluindo o próprio CleanMyTCL.

Candidatos com alteração real (commit de topo diferente do upstream):

| Fork | Branch | O que faz | Avaliação |
|---|---|---|---|
| `henry-design/cleanyourtv` | `hybrid-launcher` | "Hybrid Home": botões do topo com mola, dock sem corte, "Jump Back In" com mola | Interessante como referência de UX, mas é a mesma família de mola que o #10 traz; **não portar** para não duplicar |
| `joaomarigo/cleantv-br` | `br/launcher-pt-br` | README e scripts em pt-BR, `tela-inicial.ps1` | Confirma que a demanda por pt-BR é real; a tradução já está no nosso launcher |
| `LuizPT/cleanyourtv` | `custom-2.4` | Organizar apps e controle parental | Controle parental é um recurso grande e sensível; **documentar** |
| `Gitawake/tv-vibehack-xiaomi` | `xiaomi-android9` | Botões do topo que não são cortados no foco, indicador de reordenação | Pequeno e bom; o nosso menu de opções já evita o corte |
| `priku/cleanyourtv` | `philips-65pus8102` | Perfil Philips | Perfis de aparelho já são um padrão nosso |
| `kirankunapuli/cleanyourtv` | `beem-customizations` | Fork Beem | Idem |
| `chrx3/cleanyourtv` | `acuarela` | Tema aquarela, rotação de fundos, screensaver | Ideia de rotação de fundos: **documentar** como possibilidade, sem implementação (custo contínuo de GPU) |
| `beta7xx/cleanyourtv` | `local` | Mantém o #305 (biblioteca) e um SECURITY.md | Interessante: SECURITY.md é um modelo para nós |
| `viniassuncao1/cleanyourtv` | `android-4.4` | Roda em Android 4.4 (minSdk 19) | Fora do nosso alvo (Android 9) |
| `roninriddle/tv-vibehack` | `sony` | Fork Sony com temas e atalhos de stock | Já é do autor que mantém o SHOWCASE |

**Nenhuma melhoria de fork foi portada nesta rodada.** Os candidatos reais ou já existem no nosso
launcher ou exigem arquitetura que a nossa base Android 9 não tem. Isso é um resultado honesto,
não uma omissão.

## 3. O que foi implementado

| Mudança | Origem | Commit | Estado |
|---|---|---|---|
| Fontes Outfit construídas uma vez por processo | upstream #225/#370 | `e32e409` | Build OK, instalado, sem erro de runtime |
| Amostragem de cor dos tiles com aritmética inteira | upstream #370 | `71af547` | Build OK, instalado |
| Home guard nos scripts de debloat | upstream #350 + melhoria própria | `393a933` | 6 testes passando |
| PR #6 — menu de opções como painel com steppers | @roky146 | `28225ca` | Integrado |
| PR #7 — fundos extras (cores, Earth at night) | @roky146 | `571c2da` | Integrado |
| PR #8 — BlurHash | @roky146 | `84e34e4` | Integrado, com aviso de custo estendido |
| PR #10 — spring Material 3 Expressive | @roky146 | `5355f15` | Integrado, correção de anel preservada |
| PR #11 — perfil TCL L32S6500 | @roky146 | `fe687ff` | Integrado, ID de HDMI local removido |
| `versionCode` 28 → 29 | integração | `59c9084` | Build e instalação conferidos |

### 3.1 Detalhes que exigiram decisão semântica

- **Presets x steppers (#6).** O painel do #6 foi escrito antes dos presets. A linha "Experiência"
  e o caminho de aviso de custo do fundo foram mantidos, e o item de fundo aponta para o
  `stepWallpaper` local, que abre o diálogo de custo antes de aplicar.
- **Custo do fundo (#7/#8).** Só o Aurora tem medição (v0.5.6). Earth at night, os BlurHash e as
  cores solids são marcados como **não medidos** na UI e na documentação. Um BlurHash também abre o
  diálogo de aviso, e trocar de Aurora para um blur volta a avisar, porque o custo deixa de ser o
  medido.
- **Anel de foco x mola nova (#10).** A correção local de cold start (`f5d8cc6`, `refitGlass`) foi
  preservada; `nudgeGlass` é ignorado no modo sem movimento.
- **Perfil do #11.** O identificador de entrada HDMI específico do aparelho foi removido e o perfil
  segue marcado como reportado por contribuidor, não reproduzido pelos mantenedores.

## 4. Contribuições preparadas para o upstream (não publicadas)

Regra de autorização respeitada: **nada foi enviado**. As branches existem localmente, em um
worktree em `C:\Users\eddyr\AppData\Local\Temp\opencode\upstream-contrib`, com base em
`upstream/main` (`fd1ee0a`).

### 4.1 PR 1 — "a vendor FallbackHome is not a second launcher in the Home guard"

- Branch: `contrib/home-guard-fallback-home`, commit `0e43620` sobre `fd1ee0a`.
- Necessidade: o #350 (merged) já ignora o setup wizard e os apps de Settings, mas não conhece o
  `FallbackHome` do fabricante. Verificado nesta TV:
  `cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`
  responde três atividades — o launcher, `com.tcl.keycustomfunctionservice/.FallbackHome` e
  `com.android.tv.settings/.system.FallbackHome`. Contá-los como "outro app que responde Home"
  deixa o guard desabilitar o único Home usável.
- Equivalente no upstream: **não**. É a mesma ideia do #350, com o caso do fallback de vendor.
- Evidência: teste com `tools/tv.py` simulada; 9 casos, 8 passam neste Windows. O caso do registro
  de recuperação assume `$HOME` POSIX e falha aqui, **não** por causa da mudança: o registro foi
  lido e continha `com.google.android.tvlauncher: enabled`, exatamente como o teste espera.
- Arquivos: `tools/disable.sh`, `tools/disable.ps1`, `tests/test_home_guard.py`.
- Autor: Eddy Sangalli, sobre o trabalho de @ppjsw (#350). Sem dados privados.
- Pendência: rodar o CI do upstream (Linux) antes de enviar.

### 4.2 PR 2 — "refit the glass highlight when cold-start layout settles"

- Branch: `contrib/cold-start-focus-ring`, commit `b568529` sobre `fd1ee0a` (rebase feito).
- Necessidade: o `onTileFocused` do upstream roda no primeiro foco, que pode acontecer antes ou
  durante o layout inicial do grid (ou com mais de 4 colunas em sets Android TV 9 de 32 bits). Com
  `card.getWidth() == 0`, a largura do vidro é calculada sobre um tile sem tamanho e o anel fica
  no tamanho do XML até o próximo toque de tecla. O próprio upstream já comenta que "focus
  restored before the first layout (cold start): the tile has no size yet".
- Equivalente no upstream: **não**. O PR #9 do @roky146 e o commit local `f5d8cc6` resolvem isso
  na nossa base; o upstream ainda não tem.
- Evidência: o comportamento foi reproduzido e corrigido nesta TCL BeyondTV2; a versão portada
  compila (`assembleDebug` + `testDebugUnitTest` OK no worktree). A correção foi verificada na TV
  com o anel de foco no tamanho certo do tile logo após o cold start.
- Arquivos: `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java`.
- Créditos: `Co-authored-by: roky146` (o PR #9 é a base do diagnóstico).
- Pendência: como o upstream não tem um `refitGlass` equivalente, o PR precisa sertestado em
  pelo menos um aparelho Android 9 com 5 colunas antes de ser enviado.

### 4.3 PR 3 — "tools: a benchmark for the launcher's key latency, jank and idle cost"

- Branch: `contrib/key-latency-benchmark`, commit `7fdb040` sobre `fd1ee0a`.
- Necessidade: o upstream não tem ferramenta de medição de performance. Ele tem testes
  automatizados, mas nada que meça a resposta do controle, o jank e o custo ocioso do launcher.
  Sem isso, cada PR de performance no upstream é avaliado por descrição, e quem escreve a
  descrição é quem faz a medição.
- Equivalente no upstream: **não**. Não existe `tools/bench.py` nem equivalente.
- Evidência: a ferramenta foi **escrita e validada nesta TCL BeyondTV2**, e é a mesma que
  mediu o build integrado. Três erros de método foram encontrados e corrigidos no caminho, e
  cada um deles custa uma série inteira de medições se passar:
  1. O quadro que reage a uma tecla nasce do vsync **anterior** a ela: casar pelo
     IntendedVsync descarta a maioria das teclas. A medição passou de 9 de 36 para 36 de 36.
  2. Os keycodes do Android são 19 CIMA, 20 BAIXO, 21 ESQUERDA, 22 DIREITA: o filtro do log
     procurava a tecla errada.
  3. Uma tecla que não move nada deixa o quadro seguinte, de outra causa, entrar na medida com
     uma latência de segundos.
- Arquivos: `tools/bench.py` (novo).
- Autor: Eddy Sangalli. Sem IP, sem número de série, sem conta, sem localização.
- Pendência: rodar o CI do upstream e revisar se o formato do resultado (JSON) serve para a
  comunidade. A ferramenta avisa e para se o launcher não for a janela em foco, em vez de
  publicar números vazios.

### 4.4 Candidatos que foram descartados (e por quê)

| Candidato | Motivo |
|---|---|
| Tradução da data em pt-BR por extenso (#373) | Já existe no upstream como PR aberto; duplicaria |
| Mola do #10 (Material 3 Expressive) | É trabalho de @roky146; não é nosso para devolver |
| Biblioteca de apps (#305) | Escopo grande; já está no upstream |
| `SECURITY.md` do fork `beta7xx` | Bom modelo, mas o upstream já tem `SECURITY.md`; verificar o conteúdo antes de propor |
| Perfis de aparelho dos forks | Já temos um perfil por modelo, com padrão de dados de verificação |

## 5. Desempenho: o que é medido e o que não é

| Item | Valor | Origem |
|---|---|---|
| Resposta de tecla, mola, jank, PSS, cold start e retorno do Aurora x preto | key p50 17 ms, mola p50 ~634 ms, jank 0,19% → 6,89%, PSS 23 → 32 MB, cold p50 391/404 ms, volta p50 36/40 ms | **v0.5.6**, `benchmarks/results/2026-10-19-v0.5.6-wallpaper.md` |
| v0.5.7 code 29 (build integrado) | Bateria em andamento, sem resultado publicado ainda | `medicoes/launcher/` |
| Mola Material 3 Expressive (#10) | **Não medido nesta TV** | — |
| BlurHash (#8) | **Não medido nesta TV** | — |
| Earth at night, cores sólidas (#7) | **Não medido nesta TV** | — |
| Guard de fonte em cache (#225/#370) | **Não medido nesta TV** | — |
| Home guard (#350) | Sem custo de runtime; só proteção de script | — |

Nenhum número da v0.5.6 foi reaproveitado como resultado da v0.5.7. Onde a UI mostra um custo, ela
diz de qual versão ele veio.

## 6. Estado da TV

- `br.eddy.tvhome` 0.5.7 (code 29), SHA-256 conferido, instalado 20/10/2026 11:38.
- Home abre a frio; anel de foco no tamanho correto do tile; 6 colunas, nome "Eddy", clima em
  pt-BR ("17° CÉU LIMPO"), dock com 6 apps, serviço de acessibilidade
  `br.eddy.tvhome/com.gathin.tvhome.KeyRemapService` ativo.
- Sem erro no `logcat` após a instalação. Sem crash e sem ANR.
- Nenhum pacote do sistema foi desativado; nada foi desinstalado; nenhum dado de app foi limpo.
- A TV é compartilhada: a bateria de benchmark só roda com ela ociosa.

## 7. Correções de documentação feitas nesta rodada

- `docs/PR-REVIEW-ROKY146-2026-10-20.md`: reescrito com o estado real dos cinco PRs integrados,
  os conflitos resolvidos e o build code 29.
- `docs/PERSONALIZATION.md`: linha do fundo reescrita — BlurHash e Earth at night marcados como
  não medidos, BlurHash descrito pelo que ele é (bitmap 32×18, sem imagem no APK).
- `COMPATIBILITY.md`: ID de entrada HDMI do #11 removido; instalação atual corrigida.
- `devices/tcl-uniontv-rtd284x-r41kt01-v391/README.md`: ID de entrada HDMI removido.
- `docs/UPSTREAM-AUDIT-2026-10-20.md`: versionCode obsoleto (19) corrigido.
- `launcher/NOTICE.md`: créditos do #8 (BlurHash, fotos NASA/ESO) e dos PRs integrados.

## 8. Estado do GitHub e próximos passos

- `main` **inalterada** em `94e4890` (v0.5.6). Nenhuma tag e nenhum release publicados.
- `develop` **publicada** em 20/10/2026 com os merges de #6, #7, #8, #10 e #11, os portes
  auditados do upstream, o novo probe de latência e a documentação. O GitHub detectou
  automaticamente os commits de merge e marcou os cinco PRs do @roky146 como *merged*; nenhum
  foi fechado ou aprovado à mão, e todos receberam antes um comentário técnico objetivo.
- Contribuições ao upstream: **3 branches prontas, nenhuma enviada**.

### A/B do spring que ficou pendente

O A/B de um parâmetro só (spring M3E 380/0,8 contra o antigo 260/0,58) tem a build de
comparação pronta em `medicoes/apks/tvhome-0.5.7-code29-oldspring.apk` e a branch local
`measure/old-spring`. A instalação ficou pendente porque a **TV saiu da rede** no meio da
medição: o ADB deixou de responder, o endereço sumiu da tabela ARP e uma varredura da faixa
192.168.1.0/24 não encontrou nenhum outro host com ADB. Nada foi reinstalado por cima, e a TV
continua com a build M3E (`AAE067C6…`).

### Ordem sugerida

1. Quando a TV voltar à rede: instalar a build de mola antiga e refazer as três séries de 36
   teclas nas **mesmas** condições (6 colunas, movimento suave, acessibilidade ligada), para
   fechar o A/B do #10.
2. A/B do BlurHash (#8) contra o Aurora, com 5 repetições.
3. Bateria completa da v0.5.7 (5 repetições), com a TV ociosa.
4. Autorizar o envio dos 3 PRs ao upstream.
