# CleanMyTCL-Android9 v0.5.8 — o que muda

**Data:** 20/10/2026 · **Aparelho de referência:** TCL BeyondTV2, Android TV 9 (API 28),
Realtek RTD2851, firmware R851T02 V660, 2 GB RAM, 32 bits.

Esta versão junta as contribuições de @roky146 que estavam em revisão, os portes auditados do
upstream CleanYourTV v2.4.0 e o que o nosso próprio benchmarking mostrou sobre a animação.

> **Regra desta versão:** desempenho por padrão, personalização por escolha, transparência sobre
> o custo. Toda função opcional é reversível, e nada é apresentado como medido sem ter sido medido
> nesta TV.

## Resumo

| | |
|---|---|
| Versão | 0.5.8 (versionCode 30) |
| Publicada em | `main` |
| APK de desenvolvimento | `medicoes/apks/tvhome-0.5.8.apk` (arquivo local, não distribuído pelo GitHub) |
| Pacote do launcher | `br.eddy.tvhome` (não comprova qual versão está instalada agora) |
| Última instalação comprovada em relatório público | v0.5.7 / versionCode 29 (20/10/2026, 11:38); verificar o APK atual pela TV |
| Validação da v0.5.8 | Build/testes de código reportados abaixo; bateria completa de hardware, standby, Cast e soak pendentes |
| Upstream revisado | CleanYourTV v2.4.0 (`fd1ee0a`), com adaptações seletivas; a árvore não é um merge integral da 2.4.0 |

## Novidades

### Navegação e menus

- **Painel de opções à direita** (@roky146 #6). As opções do Home ficam num painel lateral em
  vez de uma lista que ocupa a tela. Os valores mudam com esquerda/direita **sem fechar o menu**.
  A linha **Experiência** (Performance / Balanced / Visual / Custom) e o aviso de custo do fundo
  foram preservados na integração.
- **Foco de retorno correto** (@roky146 #9 + correção local). O anel de foco fica no tamanho
  certo do tile logo no cold start, em vez de largo até a primeira tecla.
- **Entradas deduplicadas por CEC** (upstream #429). Um console ou soundbar ligado por HDMI-CEC
  deixa de virar uma segunda entrada: a porta HDMI aparece uma vez, com o nome do aparelho.
  Portas AV não mostram mais "Conectado" quando não há nada pluggedado. **Verificado na TV**: o
  menu passou a mostrar 3 portas HDMI (antes o "HDMI Console de jogos" era uma entrada à parte)
  e a linha AV ficou sem o estado.
- **Botão de fonte do controle** (upstream #244). O `InputKeyService` também escuta
  `KEYCODE_MEDIA_RECORD` e `KEYCODE_TV_INPUT`, e abre o menu de Entradas do launcher em vez de
  deixar a tecla morrer com o launcher original desabilitado.
- **Painel de fonte como activity** (upstream #411). Algumas TVs têm o painel de fonte como
  activity e não como service; o launcher agora tenta os dois.

### Personalização

- **Mais fundos** (@roky146 #7): seis cores sólidas escuras (contraste de pelo menos 13:1 com o
  texto branco) e *Earth at night* (NASA, domínio público).
- **Fundos BlurHash** (@roky146 #8): três blurs que vêm de um hash de 28 caracteres. Só o hash
  entra no APK — a foto não. `scripts/tcl-blurhash.ps1` gera o hash a partir de uma foto sua.
- **A mola mais curta** (@roky146 #10): o spring Material 3 Expressive (stiffness 380, damping
  0,8) e um leve "bounce" nas bordas da grade.
- **Perfil de aparelho** (@roky146 #11): TCL L32S6500 (UnionTV, RTD284x, R41KT01 V391), com
  lista de debloat de 4 pacotes e smoke test documentado.

### Desempenho e estabilidade

- **Cada peso da fonte é construído uma vez por processo** (upstream #225 e #370). Antes, cada
  `TextView` reconstruía a fonte variável na hora de inflar a tela.
- **Cor dos tiles sem HSV por pixel** (upstream #370).
- **Wallpaper decodificado no tamanho da tela** (upstream #277). O decode passa a ser amostrado
  para a resolução do painel e a recusar fotos acima de 4K. **Nesta TV não muda nada**: os dois
  wallpapers inclusos são 1920×1080 e a tela é 1920×1080, então o ganho é zero aqui — o
  trabalho é para telas maiores ou fotos maiores.
- **Grade testada em 4, 5 e 6 colunas** (upstream #280). A aritmética da grade saiu do
  `MainActivity` para uma classe própria e ganhou 12 casos de teste: o dock tem exatamente
  COLUMNS vagas, um app escondido liberta uma vaga e nenhuma, a largura do tile encolhe
  corretamente e o foco cabe na célula.
- **Home guard nos scripts de debloat** (upstream #350). O script se recusa a desabilitar o app
  que responde ao Home enquanto nenhum outro puder assumir. O `FallbackHome` do fabricante
  (o placeholder do firmware) não conta como substituto.
- **Pacotes de HDMI/AV protegidos** (upstream #421). Um pacote que pede
  `android.permission.TV_INPUT_HARDWARE` é a camada de software sob as entradas: desabilitá-lo
  deixa a porta notar o cabo e mostrar "No Signal", e reabilitar em lotes não traz a imagem de
  volta. O script agora pula esses pacotes, com `TVINPUT_ALLOW=1` para forçar.
- **Registro do que vai ser mudado** (upstream #317). `tools/recovery.py` lê a TV antes do
  primeiro comando e grava o estado, e o `restore.sh` reproduz esse estado em vez de adivinhar.
  Um registro danificado é recusado antes de chegar à TV.
- **Aviso quando o ADB de rede está aberto** (upstream #319). `ro.adb.secure=0` significa que
  qualquer aparelho da rede ganha uma shell sem o "Allow"; o script avisa antes de mexer.
- **Dependências fixadas por hash** (upstream #318). O `tools/requirements.txt` fixa a versão e
  o sha256 de cada dependência, e o Gradle é baixado com o checksum conferido.
- **A TV segue acessível**: o serviço de remapeamento continua registrado depois da instalação.

### O que do upstream não se aplica aqui

- **#412** (o OK não abrir o app durante a reordenação) protege um bug do modo de reordenação por
  botão na barra superior. Este launcher reorganiza por **segurar OK no tile**, e não tem esse
  modo, então o problema não existe aqui. O PR foi enviado ao upstream, onde o bug existe.

### Ferramentas

- `scripts/tcl-key-latency.sh` — mede a latência do controle, a duração do movimento e o jank,
  com o método corrigido (ver abaixo).
- `scripts/tcl-compare-latency.py` — compara duas execuções lado a lado.
- `tests/test_home_guard.py` — o guard de Home, testado com uma TV simulada.

## O que a medição mostrou

### A mola nova terminou o movimento mais cedo na medição específica da v0.5.7

Comparação controlada na mesma TV, na mesma sessão, com **um único parâmetro trocado** (a mola),
4 colunas, movimento suave, fundo preto, acessibilidade ativa. Três séries de 36 teclas por lado.

| Métrica | Mola M3E (380 / 0,8) | Mola antiga (260 / 0,58) |
|---|---|---|
| Resposta da tecla p50 | 18 ms | 18 ms |
| **Movimento p50** | **441 ms / 440 ms** | **635 ms / 629 ms** |
| Movimento p95 / p99 | 450 / 452 ms | 644 / 645 ms |
| Quadros atrasados | 0,1% de 2792 | 0,5% de 4051 |
| Quadros desenhados | 2792 | 4051 |
| Teclas medidas | 108 de 108 | 108 de 108 |

Nessa medição da **v0.5.7** (não da v0.5.8), a mola nova **encerrou o movimento 194 ms antes** (cerca de um terço a menos), com a mesma resposta
de tecla e **menos** quadro atrasado. A mola antiga mede 635 ms e 629 ms nas duas repetições, e
o A/B de wallpaper de 19/10/2026 tinha medido 634 ms: o método novo reproduz a referência antiga.

Detalhes: [2026-10-20-v0.5.7-spring-m3e.md](../benchmarks/results/2026-10-20-v0.5.7-spring-m3e.md).

### Três erros de método que custavam séries inteiras

O primeiro probe casava cada tecla com o quadro de `IntendedVsync` posterior a ela. Isso é
errado: o quadro que reage a uma tecla nasce do vsync **anterior** a ela e só se completa
depois. Na primeira bateria do build integrado, só **9 das 36** teclas produziram uma medição
utilizável, com valores que não se sustentavam (634 ms e 840 ms contra 10–15 ms reais).

O probe corrigido procura o primeiro quadro que **terminou** depois da tecla, filtra pelo
próprio nome do keycode no log do `input` (19 CIMA, 20 BAIXO, 21 ESQUERDA, 22 DIREITA), começa
no primeiro item da grade em vez da barra superior, e trata como "sem reação visual" uma tecla
que não move nada. Com isso, os dois lados do A/B mediram **108 de 108** teclas, sem nenhuma
perdida.

## O que continua sem medição

Honestamente, e o launcher diz o mesmo na tela:

| Item | Estado |
|---|---|
| BlurHash (#8) contra o Aurora | **Não medido nesta TV.** A UI avisa antes de ativar |
| Earth at night e as cores sólidas (#7) | **Não medidos nesta TV.** A UI avisa antes de ativar |
| Preset Visual completo (mola + Aurora) na v0.5.8 | O custo exibido é o medido na v0.5.6, identificado como tal |
| Fontes em cache (#225/#370) | Ganho esperado, **não medido** nesta TV |
| Wallpaper em resolução reduzida | Candidato, não implementado |

O único número de custo que aparece na interface é o do Aurora na v0.5.6, e ele diz de qual
versão veio.

## Recuperação

- O launcher original continua recuperável: o `FallbackHome` do firmware assume o Home se o
  nosso for desabilitado.
- `scripts/tcl-restore.ps1` volta ao estado anterior dos pacotes.
- `tools/disable.sh` e `tools/disable.ps1` gravam o estado **antes** de mexer, em
  `~/.cleanyourtv/`, e o `restore.sh` reproduz esse estado. O primeiro registro vale: uma
  segunda execução da mesma lista não sobrescreve o estado original com o estado já quebrado.
- Nenhum pacote que responda ao Home é desabilitado enquanto nenhum outro puder assumir.
- Pacotes que dirigem as entradas HDMI/AV não são desabilitados por engano.

## Testes

| Suíte | Resultado |
|---|---|
| `python -m unittest discover -s tests` | **25 passando** (guard de Home, guard de HDMI/AV, exposição do ADB, registro de recuperação) |
| `scripts/test_pkg_validate.sh` | **29 passando** |
| `gradlew assembleRelease testDebugUnitTest` | **BUILD SUCCESSFUL** (grade 12 casos, tamanho do wallpaper 9 casos) |
| `bash -n tools/disable.sh`, `tools/restore.sh` | limpo |
| Parser do PowerShell em `disable.ps1`, `restore.ps1` | 0 erros |

## Créditos

- **@roky146** — PRs #2, #3, #4, #5, #9 (revisados e mesclados antes) e #6, #7, #8, #10, #11
  (integrados nesta versão).
- **@leovvay** — #225 e #370 (fontes em cache), #39 (carregamento fora da thread principal),
  #40 (ideias de build release).
- **@ppjsw** — #350 (Home guard).
- **@nexuspcs** — #317 (registro de recuperação), #318 (dependências fixadas), #319 (exposição do
  ADB).
- **@Samreay** — #289 e #246 (foco e rolagem), #280 (testes da grade).
- **@mwamodo** — #291 (nomes das entradas).
- **@anandhakrishnanaji** — #298 (um tile por app).
- **@abuhasanrumi** — #277 (wallpaper no tamanho da tela).
- **@iyts** — #342 (botão de TV), #244 (botão de fonte).
- **@yashug** — #55 (foco dentro da linha).
- **@woltapp** — decodificador BlurHash (MIT).
- **NASA, ESA, CSA, STScI, ESO** — as fotos dos fundos e dos hashes.

Detalhes: [NOTICE.md](../NOTICE.md) e [launcher/NOTICE.md](../launcher/NOTICE.md).
