# CleanMyTCL-Android9 v0.5.8

O launcher para TCL Android TV 9, com os presets de experiência, os fundos opcionais, a
proteção de recuperação e os scripts de medição.

**Aparelho de referência:** TCL BeyondTV2, Android TV 9 (API 28), Realtek RTD2851, firmware
R851T02 V660, 2 GB RAM, 32 bits. Launcher instalado: `br.eddy.tvhome`.

## O que muda

### Navegação e menus

- **Painel de opções à direita** (@roky146 #6). As opções do Home ficam num painel lateral; os
  valores mudam com esquerda/direita sem fechar o menu. A linha **Experiência**
  (Performance / Balanced / Visual / Custom) e o aviso de custo do fundo foram preservados.
- **Anel de foco no cold start** (@roky146 #9 + correção local). O anel fica no tamanho certo do
  tile logo no primeiro layout, em vez de largo até a primeira tecla.
- **Entradas deduplicadas por CEC** (upstream #429). Um console ou soundbar por HDMI-CEC deixa de
  virar uma segunda entrada; a porta AV não mostra mais "Conectado" sem nada pluggedado.
- **Botão de fonte do controle** (upstream #244) e **painel de fonte como activity** (upstream #411).

### Personalização

- **Mais fundos** (@roky146 #7): seis cores sólidas escuras e *Earth at night* (NASA).
- **Fundos BlurHash** (@roky146 #8): três blurs de 28 caracteres; só o hash entra no APK.
- **Mola mais curta** (@roky146 #10): spring Material 3 Expressive, medido nesta TV.

### Desempenho e estabilidade

- Fontes construídas uma vez por processo (upstream #225, #370); cor dos tiles sem HSV por pixel.
- Wallpaper decodificado no tamanho da tela (upstream #277) e grade testada em 4/5/6 colunas
  (upstream #280).
- **Home guard** (upstream #350): o script não desabilita o app que responde ao Home enquanto
  nenhum outro puder assumir, e o `FallbackHome` do fabricante não conta como substituto.
- **Pacotes de HDMI/AV protegidos** (upstream #421): um pacote que pede `TV_INPUT_HARDWARE` é a
  camada de software sob as entradas; desabilitá-lo deixa a porta mostrar "No Signal".
- **Registro de recuperação** (upstream #317): o estado é lido antes da mudança e reproduzido no
  undo, em vez de adivinhado.
- **Aviso de ADB de rede aberto** (upstream #319) e **dependências fixadas por hash** (upstream #318).

### Ferramentas

- `tools/bench.py` — latência do controle, duração do movimento, jank, cold start, volta ao Home,
  CPU ociosa e PSS, com os números bracos por tecla. Também foi enviado ao upstream
  ([PR #437](https://github.com/Gathin23/cleanyourtv/pull/437)).
- `scripts/tcl-key-latency.sh` e `scripts/tcl-compare-latency.py` — o mesmo método, em shell, com um
  comparador lado a lado.
- `tests/` — 25 testes dos scripts contra uma TV simulada, 29 de validação de nomes de pacote e 21
  de JVM.

## Medições nesta TV

| Métrica | v0.5.8 | Referência |
|---|---|---|
| Resposta da tecla p50 | 17–19 ms | v0.5.6: 17 ms |
| Movimento p50 (mola M3E) | **441 ms / 440 ms** | mola antiga: 635 ms / 629 ms |
| Movimento p95 / p99 | 450 / 452 ms | mola antiga: 644 / 645 ms |
| Jank | 0,1% | mola antiga: 0,5% |
| Cold start p50 | 466–477 ms | v0.5.2: 466 ms |
| Volta ao Home p50 | 36 ms (TV em foco) / 104 ms (de outra app) | v0.5.2: 38 ms |
| CPU ociosa do launcher | 0% de um núcleo | v0.5.2: 0% |
| PSS | 17–23 MB | v0.5.2: 18 MB |

O A/B da mola trocou **um único parâmetro** (stiffness 380 / damping 0,8 contra 260 / 0,58), com
108 teclas por lado, nas mesmas condições. A mola nova acaba o movimento **194 ms antes**, com a
mesma resposta de tecla e menos quadro atrasado.

## O que continua sem medição

Dizido na própria interface, antes de ativar:

| Item | Estado |
|---|---|
| BlurHash (#8) contra o Aurora | **Não medido nesta TV** |
| Earth at night e as cores sólidas (#7) | **Não medidos nesta TV** |
| Preset Visual completo (mola + Aurora) na v0.5.8 | O custo exibido é o medido na v0.5.6, identificado como tal |
| Fontes em cache (#225/#370) | Ganho esperado, **não medido** nesta TV |

A bateria completa de 5 repetições da v0.5.8 e o A/B do BlurHash continuam pendentes.

## Validação executada nesta versão

- Smoke test: **24/24 OK** na v0.5.8 (2026-10-20 17:08).
- Três reinícios: Home em foco, pacotes desativados mantidos, Home inalterado e smoke OK em cada
  um; a acessibilidade sobreviveu aos reinícios.
- Chromecast: a **descoberta** verificada a partir de dentro (`Done announcing`, 7 respostas a
  consultas mDNS, `Gcm.Register.Success=2`); a transmissão continua por fazer.
- `tools/secret-scan.ps1`: 0 achados.
- CI: build, testes de JVM, testes dos scripts, validação de pacotes e verificação de links em
  cada push e cada PR.

## Instalação

```bash
adb install -r app-release.apk
adb shell settings put secure enabled_accessibility_services br.eddy.tvhome/com.gathin.tvhome.KeyRemapService
adb shell settings put secure accessibility_enabled 1
```

A segunda linha é necessária: reinstalar desliga o serviço de acessibilidade.

## Recuperação

O launcher original continua recuperável (o `FallbackHome` do firmware assume o Home). O
`tools/disable.sh` grava o estado antes de mexer, em `~/.cleanyourtv/`, e o `restore.sh`
reproduz esse estado. Um registro danificado é recusado antes de chegar à TV.

## Créditos

@roky146 (PRs #2 a #11), @leovvay (#225, #370, #39, #40), @ppjsw (#350), @nexuspcs (#317, #318,
#319), @Samreay (#246, #280, #289), @mwamodo (#291), @anandhakrishnanaji (#298), @abuhasanrumi
(#277), @iyts (#342, #244), @yashug (#55), @woltapp (BlurHash), NASA/ESA/CSA/STScI/ESO (as fotos).
Detalhe em [NOTICE.md](NOTICE.md) e [launcher/NOTICE.md](launcher/NOTICE.md).
