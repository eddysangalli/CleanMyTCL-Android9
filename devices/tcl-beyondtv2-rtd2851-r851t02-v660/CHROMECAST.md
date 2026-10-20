# Chromecast na TCL BeyondTV2 — verificado por dentro, não só pelo processo

Data: 20/10/2026. Aparelho: TCL BeyondTV2, Android TV 9 (API 28), firmware R851T02 V660,
2 GB RAM, 32 bits, ethernet. Launcher instalado: `br.eddy.tvhome` 0.5.8 (versionCode 30), que
**não toca em nenhum pacote do Google**.

## O que a issue [#453](https://github.com/Gathin23/cleanyourtv/issues/453) reporta

Depois do debloat, o Chromecast deixou de aparecer na rede. O autor descobriu que o processo
receptor estava vivo e que **reiniciar o processo** resolvia.

O ponto levantado aqui está certo e é a lição certain: **o processo existir não prova que a TV
seja descoberta**. Um `pm list packages` ou um `ps | grep mediashell` dizem apenas que o
processo arrancou.

## O que verificámos nesta TV

O `mediashell` foi parado à força e voltou, e o que se vê no `logcat` é o que interessa:

```
I cast_shell: [metrics_stat_logger] Gcm.Register.Success=2
I cast_shell: [device_info_impl.cc(1066)] Primary IP address changed: netif=eth0, new=<the TV's address>
I cast_shell: [mdns_app_filter.cc(1922)] MdnsAppFilter: Done announcing.
I cast_shell: [mdns_app_filter.cc(3358)] MdnsAppFilter: responses sent in 60 seconds: 7
I cast_shell: [mdns_app_filter.cc(3550)] Mdns cast queries recently sent: queries=4
```

As três linhas que provam a descoberta:

| Linha | O que significa |
|---|---|
| `MdnsAppFilter: Done announcing` | a TV está a anunciar-se na rede |
| `Mdns cast queries recently sent: queries=4` | alguém perguntou por receptores e a TV atendeu |
| `MdnsAppFilter: responses sent in 60 seconds: 7` | a TV respondeu a 7 dessas perguntas |

E o registo do próprio dispositivo: `Gcm.Register.Success=2`, com o `WargService` (o serviço que
regista a TV como receptor) ativo e o `CastReceiverService` a responder.

## O que isto significa para um smoke test

O nosso `scripts/tcl-smoke.sh` verificava que o processo do `mediashell` existia. Isso é
**insuficiente**, e a issue #453 mostra por quê: o processo pode estar vivo com o Chromecast
invisível.

A verificação que vale é sobre a **descoberta**, não sobre o processo:

```bash
# A TV anuncia-se? (procura por "Done announcing" depois de um arranque do serviço)
adb logcat -d -s cast_shell | grep -E 'Done announcing|responses sent'

# O serviço que regista a TV como receptor está de pé?
adb shell dumpsys activity service com.google.android.apps.mediashell | grep WargService
```

## Limites deste teste

- **Não reproduziu-se a falha**: o Chromecast nunca deixou de ser descoberto nesta TV, porque
  nenhum dos pacotes do Google foi desabilitado (a nossa lista de debloat tem dois pacotes, ambos
  da TCL). A issue #453 falava de uma lista maior.
- **Não houve transmissão de vídeo**: o que se verificou é a **descoberta** — a TV aparece para
  outros aparelhos e responde a perguntas. Fazer streaming de um celular exige um aparelho na
  mesma rede e continua por fazer.
- **O reinício do processo não foi testado como remendo**, porque não houve o problema. O que se
  fez foi reiniciar o `mediashell` e ver que a announcing voltava — o que confirma que o serviço
  sabe recompor-se sozinho, e não que o reinício seja a resposta.

## Contributions de volta ao upstream

Este texto é a evidência que a issue [#453](https://github.com/Gathin23/cleanyourtv/issues/453)
pede, medida numa TCL Android TV 9. Se o mantenedor quiser, o smoke test do upstream pode
verificar a **descoberta** (`Done announcing` / `responses sent`) em vez da mera existência do
processo, que é o que a issue mostra ser enganoso.
