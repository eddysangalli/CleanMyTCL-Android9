# Auditoria e integração — 2026-11-17

Fork `CleanMyTCL-Android9` (v0.6.1) × upstream `Gathin23/cleanyourtv` (v3.5.0 / `upstream/main`).
TV de referência: TCL BeyondTV2, Android TV 9, Realtek RTD2851, 2 GB, armeabi-v7a, sem root.

## 1. O que foi integrado (commits `8f47c568`, `85d9648d`)

| Origem | O que entrou | Arquivo |
|---|---|---|
| #3804 | `OnGlobalFocusChangeListener` removido em `onDestroy`; `ConnectivityManager` pelo contexto da aplicação (registro e desregistro) | `MainActivity.java` |
| #3595 | `icon.setImageDrawable(null)` ao reciclar holder que passa a mostrar banner | `AppAdapter.java` |
| #3044 | `ClockRefresh`: redesenha `TextClock` na virada do minuto e ao display voltar a ligar (só < Android 12) | `ClockRefresh.java` (novo), `MainActivity`, `ClockDream` |
| #668 | Glow do descanso: `SHIFT_STEPS` 40 → 16 (menos redesenhos do gradiente) | `ClockDream.java` |
| #370 | Não procurar a view do card a cada frame | `DockDecoration.java` |
| — | `.withLayer()` nas animações de fade do descanso | `ClockDream.java` |

Divergência de ponto de partida: `merge-base` do fork com o upstream = `9ef40c1`.
Entre ele e `v3.5.0` há **822 commits tocando `launcher/`**; mais 12 entre `v3.5.0` e `upstream/main`.

## 2. Verificação feita

- **Build**: `./gradlew assembleDebug` e `test` — exit 0, sem erro.
- **Testes unitários**: **21 testes** por variante (12 `GridColumnsTest` + 9 `WallpaperSizeTest`), 0 falhas, 0 erros, 0 skipped. (Debug e Release dão 21 cada.)
- **APK**: `sha256 fb916f2bbeb231b82359747c377850f542b6b2b6f72905ac3d2b695d2ff39b7a` —
  conferido **igual** ao `base.apk` instalado na TV.
- **Instalado**: `br.eddy.tvhome` versionCode 33 / v0.6.1, `lastUpdateTime 2026-11-17 01:09`.
- **Smoke**: Home em foco, 1 Activity, 0 crash no buffer `crash`.

### Teste de retenção de memória (#3804) — o teste anterior era inválido

O manifest declara `configChanges="orientation|screenSize|keyboard|keyboardHidden|uiMode"`,
então `cmd uimode night` **não recria a Activity**. Medir PSS sob `uimode` não provava nada.

Teste válido usado: `settings put global always_finish_activities 1`, que destrói a Home
a cada saída; 8 ciclos Netflix → Home.

| Ciclo | PSS |
|---|---|
| base | 47,9 MB |
| 1 | 45,6 MB |
| 2 | 38,7 MB |
| 3–8 | 38,7 → 38,8 MB (plano) |

Confirmação de que as recriações ocorreram: **13 `am_on_create_called`** para `tvhome`
no buffer de eventos. Resultado: sem acúmulo. `always_finish_activities` foi **restaurado
para `null`** (valor original) ao fim.

## 3. Benchmarks (v0.6.1, `scripts/tcl-launcher-bench.sh`, 36 teclas / 10 voltas)

```
Controle   resposta (tecla -> 1o quadro): p50 29 | p95 44 | p99 46 | max 46 ms
           movimento completo:            p50 440 | p95 446 | p99 451 | max 451 ms
           quadros atrasados: 8.4% de 945
Abertura   a frio: p50 1111 | max 1137 ms
Volta Home p50 105 | p95 450 | max 450 ms
Parada     CPU total 14.2% | launcher 1.9% de 1 nucleo | 0.2 quadros/s
Memoria    PSS 52 MB (apos 10 voltas: 40 MB)
```

**Aviso de comparabilidade:** o único baseline no CSV é de **v0.3.3** (2026-10-19),
que não tinha RSS, leitor, cards HDMI nem presets. Comparar direto é enganoso:
aquele build media p50 14 ms de resposta e 419 ms de abertura a frio **sem** esse
conjunto de recursos. Não há, até hoje, uma medição de v0.6.0 com este mesmo roteiro
para isolar se a integração custou ou ganhou algo. **Isso está pendente.**

## 4. Pesquisa de forks do upstream (7677 forks)

Quase todos são cópias sem alteração (descrição e `pushed_at` idênticos ao upstream).
Os que fogem do padrão e foram examinados:

| Repo | Situação | Veredito |
|---|---|---|
| `Rhythmd7/cleanyourtv-nepal` | debloat é `tcl-beyondtv-android-11`; pacotes **existem e são TCL** | **Parcialmente útil** — ver §5 |
| `maicoopereira/tcl-beyondtv-buttons` | BeyondTV **e** BeyondTV2, mesmo build `AP08`, mesmo controle `TCL_RC802VLA` | **Achado forte — ver §5** |
| `robinlennox/tcl-debloat` | debloat TCL | revisar seletivamente; risco de ampliar lista de pacotes desabilitados |
| `SpacelyDigital/liftoff-tcl-debloater` | debloat reversível | idem |
| `JulianDerDritte/cleanyourtv` | lista circular, tema Sony BRAVIA | não aplica a esta TV |
| `tomcoolpxl/cool-shield-launcher`, `glazkov/atvOS` | renomeações | sem valor técnico |

## 5. Correção de um registro nosso: o botão Globoplay **pode** ser remapeado

O registro do projeto (`memoria: tcl-tv-optimization-project`) afirma que o remapeamento
do botão Globoplay é **impossível sem root**, com base em: key 4097 é injetada por
software (`scanCode=0`, sem evento em `/dev/input`) e o alvo é hardcoded em
`com.tcl.keycustomfunctionservice`.

O repositório `maicoopereira/tcl-beyondtv-buttons` (Apache-2.0, testado explicitamente em
`TCL/BeyondTV2/BeyondTV:9/PPR1.180610.011/AP08` — **o mesmo build da nossa TV**) descreve
o caminho oposto e mais fundamental: o controle envia `EV_MSC/MSC_SCAN 000c0051` e **não**
emite `EV_KEY`, porque falta `/system/usr/keylayout/Vendor_0416_Product_0301.kl`. Eles não
editam o `.kl` (que exigiria root); usam o ioctl **`EVIOCSKEYCODE_V2`** em `/dev/input/event*`.

Premissa **já verificada nesta TV** nesta sessão:

```
uid=2000(shell) gid=2000(shell) groups=2000(shell),1004(input),...
```

Ou seja: o `shell` está no grupo `input` (1004) e `/dev/input/event*` é `crw-rw---- root input`.
A abertura em `O_RDWR` é, portanto, possível em princípio.

O que **ainda não foi verificado** (honestidade sobre o alcance deste documento):

- Nenhum controle físico estava conectado durante a auditoria — `getevent -ip` listou apenas
  `virtual-search`, `sim-TP`, `sim-mouse`, `sim-kb`, `mtkinp`, `rtk_keypad`. Não há device
  `Vendor 0416 / Product 0301` para testar o ioctl.
- Portanto **não sabemos** se o firmware desta TV aceita `EVIOCSKEYCODE_V2` nem se o
  scancode real é `000c0051`. Falta o teste empírico com o controle em mãos.
- O repo também não foi compilado aqui: não há NDK/zig instalado nesta máquina para gerar
  um binário ARM estático (`armeabi-v7a`).

Consequência prática: o registro antigo deve ser reaberto como **"não confirmado"**, não
como "impossível". O caminho `EVIOCSKEYCODE` tem premissa verificada e build-alvo idêntico.

## 5b. Debloat TCL do fork nepal — candidatos NÃO testados

O fork apesar do nome é Android 11, mas os **nomes de pacote são TCL** e alguns não estão
na nossa lista atual. Da lista `2-promos.txt`, ainda não desabilitados aqui:

| Pacote | O que o fork afirma | Risco |
|---|---|---|
| `com.tcl.guard` | "TGuard usage tracking / memory cleaner" | Baixo-médio: limpa memória; pode ser o que o usuário chama de "otimizador" |
| `com.tcl.messagebox` | mensagens e promos | Baixo |
| `com.tcl.waterfall.overseas` | feed web TCL | Baixo |
| `com.tcl.esticker` | adesivo de demo de loja | Baixo |
| `com.tcl.notereminder` | notas/lembrete | Baixo |
| `com.tcl.tv.tclhome_master` | Home promocional alternativa da TCL | ⚠️ **alto** — é uma Home; desabilitar pode reproduzir o problema que o `tvlauncher` causou |
| `com.tcl.overseasappshow` | "re-enables MagiConnect, notereminder, tclhome_master on boot" | ⚠️ alto — é ele que religa os outros |

Do `1-telemetry.txt`: `com.tcl.bi` **já está desabilitado** aqui. `com.google.android.feedback`,
`com.google.android.partnersetup`, `com.google.android.onetimeinitializer` e
`android.autoinstalls.config.tcl.device` **não foram confirmados como presentes** nesta TV
(a verificação foi interrompida por falhas do ambiente), então não entram em nenhuma lista.

Regra do projeto aplicável: **um `pm disable-user` por vez**, smoke test depois, parar no
primeiro sinal de anomalia. Nada acima foi aplicado.

## 6. Pendências reais

1. **Baseline v0.6.0 com o mesmo roteiro de benchmark** — sem ele, não é possível afirmar
   ganho ou regressão da integração. (Regra do projeto: medir, não supor.)
2. **Teste `EVIOCSKEYCODE` com o controle físico**, com o binário ARM compilado.
3. **Itens de upstream ainda em aberto** — auditoria automatizada iniciada; resultado
   consolidado pendente neste arquivo.
4. **CI do commit `85d9648d`** — não foi confirmado como aprovado.
