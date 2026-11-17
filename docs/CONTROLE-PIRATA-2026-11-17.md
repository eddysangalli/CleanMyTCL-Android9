# Controle pirata — por que o remap não funciona, e o que existe no lugar

Data: 2026-11-17. TCL BeyondTV2, Android TV 9. O usuário **não tem mais o controle original**;
usa um controle de terceiros.

## Conclusão principal

O `EVIOCSKEYCODE` **não é o caminho** para este controle, e o motivo não é permissão: **o
controle não gera evento de kernel nenhum**.

### Evidência

Capturei `/dev/input` durante 30 segundos enquanto o usuário apertava setas, OK, Netflix e
Globoplay:

```
getevent -lt  (todos os devices)   ->  ZERO eventos
```

Testei cada um dos 7 devices individualmente (`event0`..`event6`), 10-15 s cada: **zero**.
E `dumpsys input` mostra que o último `KeyEvent` tinha **45 minutos** de idade.

Ao mesmo tempo, o botão Globoplay **funcionou**: a TV foi para
`com.globo.globotv/...AccountChooserActivity`. E Home também funcionou.

Ou seja: a tecla chega e é executada, mas **não passa por `/dev/input`**.

### Por onde ela passa

`dumpsys input` lista dois devices que não são de hardware:

```
5: virtual-search
   Location: android.os.BinderProxy@80a25a
   Identifier: bus=0x0006, vendor=0x0000, product=0x0000
6: virtual-search
   Location: android.os.BinderProxy@e546c5
```

`bus=0x0006` é **BUS_VIRTUAL**. São devices criados por software via Binder, com
`KeyLayoutFile: Generic.kl`. O processo que os alimenta está rodando:
`com.tcl.keycustomfunctionservice:input` (uid system).

### O que isso significa

O registro antigo do projeto estava **certo**, e o repo de referência
(`maicoopereira/tcl-beyondtv-buttons`) **não se aplica** a este caso. Comparação:

| O repo assume | Esta TV |
|---|---|
| O controle BT cria `/dev/input/eventN` (`Vendor 0416 / Product 0301`) | Nenhum nó do controle existe |
| Falta o keylayout, por isso não sai `EV_KEY` | Não há keycode de kernel para mapear |
| `EVIOCSKEYCODE` instala o mapeamento | `rtk_keypad` aceita o ioctl mas descarta; os outros recusam |
| A tecla chega ao Android como scancode | A tecla é injetada **acima** do kernel, já como `KeyEvent` |

Isso explica o `scanCode=0` observado no levantamento antigo: teclas injetadas por software
não têm scancode físico. **Não era bug — é a natureza do caminho.**

O `TCL_RC802VLA` que aparece pareado está `STATE_DISCONNECTED`: é fantasma, sobra do
controle original que o usuário não tem mais.

## O que o APK do serviço revela (desmontagem de strings)

APK: `/product/app/KeyCustomFunctionService/KeyCustomFunctionService.apk` (131 KB, 64 classes).
Copiado para `diagnostico-controle/apk/` (não versionado).

Classes: `KeyMulService`, `ListeningKey`, `KeyCallback`, `TKeyCommonManager`,
`KeyMultipleListener`, `FallbackHome`, `util/AppUtils`, `util/MyKeyEvent`.

**Não há arquivo de configuração externo.** Procurei assets, `res/xml`, settings globais/secure
e propriedades do sistema: nada. O mapeamento é **hardcoded no dex**.

Mapeamentos encontrados nas strings:

```
KEYCODE_BUTTON_12, start Netflix, power_on=true
KEYCODE_BUTTON_4,  start Netflix
KEYCODE_BUTTON_14, start YouTube
KEYCODE_BUTTON_3,  start Google Play
start activity com.globo.globotv/
send broadcast com.netflix.ninja.permission.NETFLIX_KEY
com.netflix.ninja.intent.action.NETFLIX_KEY
startService com.tcl.tv.service.GlobalKeyService
>com.tcl.tv/com.tcl.tv.service.GlobalKeyService keyCode is 4090
>com.tcl.tv/com.tcl.tv.service.GlobalKeyService keyCode is 4095
tcl.sys.intent.action.KEY_LONG_PRESS keyCode=
```

O serviço fala com `com.realtek.system.KeyEventManager` e `vendor.realtek.system@1.0`
(`IRtkPowerManager`) — é a camada Realtek que traduz o sinal do controle em `KeyEvent`.

## A regra que mais interessa (e assusta)

```
topPackageName: com.netflix.ninja, forbid to start all application
```

Existe uma trava no serviço: **quando o topo é o Netflix, ele proíbe iniciar qualquer outro
app**. Isso é coerente com o comportamento observado na sessão — o botão Globoplay abre
`com.globo.globotv`, mas com o Netflix em primeiro plano o serviço recusa.

Consequência prática: **o botão dedicado é reprogramável apenas trocando o APK do serviço**
(assinado pela TCL), porque o alvo está no código, não em configuração.

## Caminhos que sobram

| Caminho | Viável? | Por quê |
|---|---|---|
| `EVIOCSKEYCODE` no controle | **Não** | não há device de kernel |
| Editar `.kl` | **Não** | exigiria `/system` gravável (root) |
| Config do `keycustomfunctionservice` | **Não** | hardcoded no dex |
| Substituir o APK do serviço | **Não sem root** | assinatura TCL |
| **`AccessibilityService`** interceptando `KeyEvent` injetado | **Sim** | é o que o `KeyRemapService` já faz |
| `KeyRemapService` já existente no launcher | **Já funciona** | mas o alvo Globoplay é hardcoded e a tecla injetada não tem scancode |

**O único caminho sem root é acessibilidade** — e é o que já está implementado. O limite é que
o serviço TCL age **antes** e pode ter já consumido a tecla (como na regra do Netflix).

## Custo de usar acessibilidade (medido indiretamente)

O `KeyRemapService` do launcher é um `AccessibilityService`. Nesta sessão ele esteve
**desligado** (o `dumpsys accessibility` mostrava `services:{}`) e o botão Globoplay ainda
funcionou — porque quem trata é o serviço TCL, não o nosso.

Se for ligar acessibilidade para tentar interceptar, medir antes/depois com
`scripts/tcl-launcher-bench.sh` e **≥3 rodadas** (o desvio de jank nesta TV é ~3 pontos
percentuais — ver `docs/DEBLoat-TCL-2026-11-17.md`).

## O que faria mudar esta conclusão

Se o usuário conseguir um controle que o Android veja como device de kernel (BT padrão com
keylayout, ou USB HID), o caminho `EVIOCSKEYCODE` volta a ser possível — e o binário
`tools/remote-button/remap.c` já está pronto e testado para isso. Basta um nó `Vendor_XXXX_
Product_YYYY` aparecer em `/dev/input`.
