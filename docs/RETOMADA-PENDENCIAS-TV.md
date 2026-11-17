# Retomada — pendências que dependem da TV estar na rede

Estado em 2026-11-17 ~12:30: a TV **saiu da rede**. Ping sweep da sub-rede acha apenas
`192.168.1.10`, `.13`, `.30` (este PC) e `.254` (gateway). `192.168.1.8` responde
"Host de destino inacessível", a porta 5555 está fechada e não há entrada ARP para a TV.
Não é queda de ADB: o aparelho não está alcançável por IP.

Antes de sair, tudo o que não dependia dela foi feito (ver `docs/AUDITORIA-2026-11-17.md`).

## Ordem exata para retomar

### 1. Reconectar
```bash
adb connect 192.168.1.8:5555
adb devices -l
```
Se o IP mudou, varrer:
```bash
for i in $(seq 1 254); do (ping -n 1 -w 250 192.168.1.$i >/dev/null 2>&1 && echo "ALIVE 192.168.1.$i") & done; wait
```
Se a porta 5555 estiver fechada mas o ping funcionar, a "Depuração por rede" caiu:
Config > Sistema > Opções do desenvolvedor > Depuração por rede (precisa do controle).
Se o IP mudou, vale fixar em IP estático ou reserva DHCP para não repetir isso.

### 2. Confirmar que o v0.6.2 continua instalado
```bash
adb shell "dumpsys package br.eddy.tvhome | grep -E 'versionCode|versionName'"
# esperado: versionCode=34 versionName=0.6.2
```
O APK de referência tem `sha256 98ec2743d502d272240d5efb2c1dd896214cd93282b699d8103f7dfb7da99e36`.

### 3. Debloat TCL — um por vez, smoke depois de cada
Candidatos do fork `Rhythmd7/cleanyourtv-nepal`, em ordem de menor risco. **Antes de cada
um**, confirmar que existe: `adb shell "pm list packages | grep <pkg>"`.

| Ordem | Pacote | Por quê |
|---|---|---|
| 1 | `com.tcl.esticker` | adesivo de demo de loja |
| 2 | `com.tcl.notereminder` | widget de notas/lembrete |
| 3 | `com.tcl.messagebox` | mensagens/promos empurrados |
| 4 | `com.tcl.waterfall.overseas` | feed web TCL |
| 5 | `com.tcl.guard` | TGuard tracking / memory cleaner |

**NUNCA** desabilitar, sem estudo separado: `com.tcl.tv.tclhome_master` (é uma Home) e
`com.tcl.overseasappshow` (é ele que religa os outros no boot).

Molde do teste, para cada pacote:
```bash
adb shell "pm disable-user --user 0 <pkg>"
sleep 5
adb shell "dumpsys window | grep -m1 mCurrentFocus"      # Home continua em foco?
adb shell "logcat -b crash -d | grep -c tvhome"           # 0?
adb shell "dmesg | tail -20"                              # a tela liga mesmo?
adb shell "cat /proc/meminfo | grep -E 'MemFree|MemAvailable'"
```
Reverter um pacote: `adb shell "pm enable --user 0 <pkg>"`.
Parar ao primeiro sinal de anomalia, conforme a regra do projeto.

### 4. Medir o ganho do debloat
Rodar `scripts/tcl-launcher-bench.sh v0.6.2-debloat 36 10` e comparar com a linha
`v0.6.2` do `medicoes/launcher.csv`. Se qualquer métrica piorar, reverter o pacote.

### 5. Botão do controle (o item de maior valor)
Pré-requisito: **controle físico conectado** (o pareamento BT cai junto com a queda de rede).

a) Descobrir quais devices existem:
```bash
adb shell "getevent -ip"        # procurar Vendor 0416 / Product 0301
```
b) Capturar o scancode real do botão Globoplay:
```bash
adb shell -tt "getevent -lq /dev/input/eventN"   # apertar o botão e ver MSC_SCAN
```
O repo `maicoopereira/tcl-beyondtv-buttons` afirma `000c0077` para Globoplay e
`000c0051` para Netflix. **Confirmar**, não assumir.

c) Provar que o ioctl funciona, antes de escrever qualquer app. Sem NDK/zig nesta máquina,
o caminho mais rápido é `sendevent`/`getevent` do próprio Android não servem para
`EVIOCSKEYCODE`; é preciso um binário. Instalar um toolchain:
```bash
# opção A: NDK
sdkmanager --install "ndk;27.0.12077973"
# opção B: zig (gera musl estático, como o repo recomenda)
```
Compilar `native/remap.c` para `arm-linux-musleabi`, empurrar e testar:
```bash
adb push remap /data/local/tmp/remap && adb shell chmod 755 /data/local/tmp/remap
adb shell "/data/local/tmp/remap /dev/input/eventN 0x000c0077 399"
adb shell -tt "getevent -lq /dev/input/eventN"   # apertar: agora deve sair EV_KEY
```
Premissa já verificada nesta TV: `adb shell id` dá `groups=...,1004(input),...` e
`/dev/input/event*` é `crw-rw---- root input`. SELinux **não** foi checado (`getenforce`).

d) Só depois disso vale o `AccessibilityService`. E há um custo que já conhecemos: o
`KeyRemapService` do nosso launcher é um serviço de acessibilidade, e o registro do projeto
diz que deixar acessibilidade ligada tem impacto. Medir antes e depois.

## O que já está fechado (não refazer)

- v0.6.2 compilado, instalado antes da TV sair, hash conferido.
- Ports `ea658e1d` e `#2908` commitados em `bacb87e9`, doc em `db94fdee`.
- `origin/main` = `db94fdee`, CI **os dois jobs success** (só warnings de versão de action).
- Teste de retenção após recreate: plano, sem acúmulo.
- `always_finish_activities` restaurado para `null`.
