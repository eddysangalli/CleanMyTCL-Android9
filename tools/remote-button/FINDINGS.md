# Botão dedicado do controle — o que foi provado, e o que não

Data: 2026-11-17. TV: TCL BeyondTV2, Android TV 9, `armeabi-v7a`, SELinux **Enforcing**.

## Veredito

O caminho `EVIOCSKEYCODE` **funciona como chamada mas não produz remapeamento** no device
que aceita o ioctl. O teste **definitivo** (apertar o botão do controle e ver se sai
`EV_KEY`) **não pôde ser feito**: nenhum controle físico estava conectado durante toda a
sessão.

Portanto: o registro antigo do projeto ("impossível sem root") **não foi confirmado nem
refutado**. O que existe agora é evidência de que a história é mais complicada.

## O que foi construído

`tools/remote-button/remap.c` — binário ARM que fala `EVIOCSKEYCODE_V2` / `EVIOCSKEYCODE`,
com três modos: `--probe` (só verifica, não altera), `--get` (lê) e escrita.

Compilação que funciona (NDK r27c):

```bash
CC=$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/armv7a-linux-androideabi26-clang.cmd
$CC -Os -o remap remap.c            # dinâmico
adb push remap /data/local/tmp/remap
```

**Duas armadilhas encontradas:**

1. **`-static` gera binário que o Android 9 recusa.** Com NDK r27 o ELF sai com
   `TLS segment is underaligned: alignment is 8, needs at least 32 for ARM Bionic`.
   Passar `-Wl,-z,max-page-size=16384` não resolve. O binário **dinâmico** roda.
2. **Git Bash converte `/data/local/tmp/...` em caminho Windows.** O `adb push` falha com
   `C:/Program Files/Git/data/local/tmp/remap`. Precisa de `export MSYS_NO_PATHCONV=1`.

## Resultados medidos

| Device | `EVIOCGKEYCODE_V2` | Escrita | Persiste? |
|---|---|---|---|
| `/dev/input/event0` (`mtkinp`) | `EINVAL` (22) | recusada | — |
| `/dev/input/event1` (`rtk_keypad`) | **OK**, devolve 0 | **OK** | **não** |
| `/dev/input/event2` (`sim-kb`) | `EINVAL` (22) | recusada | — |
| `/dev/input/event4` (`sim-TP`) | `EINVAL` (22) | recusada | — |

No `rtk_keypad`, escrever `0x000c0051 -> 183` retorna `OK (V2)`, mas reler devolve `0`, e o
mesmo acontece com scancodes que **já deveriam existir** (`0x74` do teclado de power também
lê `0`). Ou seja: nesse device a leitura do keymap não reflete a tabela real, e a escrita
aparenta ser aceita e descartada.

**Interpretação que os dados sustentam:** este kernel expõe `EVIOCSKEYCODE_V2` mas o device
`rtk_keypad` não tem keymap editável utilizável por esse caminho. Isso é consistente com o
repositório de referência estar certo sobre o *princípio* (o botão não gera `EV_KEY` por
falta de keylayout) e possivelmente errado sobre o *mecanismo* sobreviver nesta firmware.

**O que ainda pode mudar a conclusão:** o ioctl foi testado em devices internos
(`rtk_keypad`, `mtkinp`), **não** no nó do controle. O controle BT cria o próprio
`/dev/input/eventN` com `Vendor 0416 / Product 0301`, e pode ter tabela de keymap diferente
— inclusive ser aceito onde os internos não são. O teste correto é nesse nó.

## O teste que falta (com o controle na mão)

```bash
adb shell "getevent -ip"                            # achar o nó do controle (Vendor 0416)
adb shell "getevent -lp /dev/input/eventN"          # ver se tem MSC_SCAN e o que falta
adb shell -tt "getevent -lq /dev/input/eventN"      # apertar Globoplay: ver MSC_SCAN
#   esperado pelo repo: 000c0077 (Globoplay), 000c0051 (Netflix)
adb shell "/data/local/tmp/remap --probe /dev/input/eventN"
adb shell "/data/local/tmp/remap /dev/input/eventN 0x000c0077 184"
adb shell -tt "getevent -lq /dev/input/eventN"      # apertar de novo: sai EV_KEY agora?
```

Se sair `EV_KEY`, o remapeamento é real e o próximo passo é a persistência (o mapeamento é
volátil: some no reboot e quando o BT reconecta). Se **não** sair, o caminho morre aqui e o
registro antigo do projeto fica confirmado.

## Custo de adoção, se funcionar

Precisa de um `AccessibilityService` para reagir às teclas. Nesta TV o `KeyRemapService` do
launcher já é um, e o registro do projeto indica impacto de acessibilidade ligada. Medir
`resp_p50`/`resp_p95` antes e depois com `scripts/tcl-launcher-bench.sh` — e lembrar que
**uma rodada não basta**: o desvio medido de jank nesta TV é de ~3 pontos percentuais
(ver `docs/DEBLOAT-TCL-2026-11-17.md`).
