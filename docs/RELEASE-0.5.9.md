# CleanMyTCL-Android9 v0.5.9

**APK:** `tvhome-0.5.9-code31.apk`, 492 753 bytes, SHA-256
`1DAC16CEBF8F74DC50D1F5E8236C4B4BE3A492211DBB248E9E26B77CC2643119`.
**Instalado e validado na BeyondTV2 V660 em 21/10/2026**, versionCode 31.

## O que entra

| Mudança | Origem | Custo medido |
|---|---|---|
| **Cards HDMI/AV na Home** — nome, ícone, mostrar/ocultar, abre a entrada | `eddysangalli/cleanyourtv@a3f37b5`, adaptado | neutro |
| **Benchmarks param de apagar a acessibilidade** | correção local (reproduzido na V660) | só ferramenta |
| **Régua de quadro igual à do `bench.py`** | correção local | só ferramenta |
| **`InputKeyService` sem eventos de janela** | upstream #150 | reduz trabalho |
| **Grade em uma passada** | upstream #446 | neutro/leve ganho na cauda |
| **Retorno à Home sem PackageManager** | correção local | neutro |

## Medições (5 pares A/B, v0.5.8 × v0.5.9)

| Métrica | v0.5.8 | v0.5.9 | Δ |
|---|---:|---:|---:|
| Cold start p50 | 447 ms | 446 ms | −1 |
| Cold start máx | 589 ms | 552 ms | −37 |
| Retorno ao Home p50 | 34 ms | **30 ms** | −4 |
| Retorno p95 | 164 ms | 177 ms | +13 |
| Resposta da tecla p50 | 18 ms | 18 ms | 0 |
| Resposta p95 / p99 | 22 / 26 | 22 / 25 | 0 / −1 |
| Movimento p50 / máx | 426 / 441 | 427 / 441 | +1 / 0 |
| PSS | 31 MB | 31 MB | 0 |
| CPU ociosa (3×60 s) | 12,8–16,3 % | 15,1–15,6 % | faixas sobrepostas |
| CPU do launcher | 0 % | 0 % | 0 |

**Critério aplicado:** funcionalidade nova é aprovada se **não prejudicar**. O retorno p95 subiu
13 ms, dentro da dispersão do próprio lado A (127–174 ms); a CPU medida em 3 amostras por lado não
mostra diferença (as faixas se sobrepõem e o launcher fica em 0 %). Nada mais piorou.

## Verificação

- **38 testes** Python (segurança da acessibilidade, régua de quadro, guardas de debloat).
- **Smoke 24/24** na TV: Assistente, microfone, HDMI, Netflix, áudio, Bluetooth, sem crash/ANR.
- Cards verificados: renomear, ícone, mostrar/ocultar, abrir (`com.tcl.tv/.TVActivity`), foco.
- Acessibilidade: `KeyRemapService` habilitado, vinculado, e o remapeamento do Globoplay funciona.
- Desabilitar/reabilitar um app atualiza a grade corretamente.

## O que não foi validado

- Caminho dos cards **pelo controle físico** (segurar OK): a tela foi aberta por ADB no teste.
- Imagem real nas entradas HDMI (não há fonte ligada).
- Standby, boot a frio e estabilidade de 8 h.
- O RSS não existe em nenhum repositório; não faz parte desta versão.

## Recuperação

```bash
adb install -r medicoes/apks/tvhome-0.5.8-code30-baseline.apk
# Devolve a lista COMPLETA de servicos de acessibilidade, nao so o nosso: escrever um servico
# isolado substitui a lista inteira e derruba os servicos de outros apps.
adb shell 's=$(settings get secure enabled_accessibility_services); case "$s" in
  *br.eddy.tvhome/com.gathin.tvhome.KeyRemapService*) ;;
  null|"") settings put secure enabled_accessibility_services br.eddy.tvhome/com.gathin.tvhome.KeyRemapService ;;
  *) settings put secure enabled_accessibility_services "$s:br.eddy.tvhome/com.gathin.tvhome.KeyRemapService" ;;
  esac'
adb shell settings put secure accessibility_enabled 1
# Confira: a lista tem de conter o KeyRemapService E tudo o que ja estava la.
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys accessibility | grep -A2 services
```

Os scripts de medicao fazem isso sozinhos (`tv_acc_save` / `tv_acc_restore`, com recuperacao
pos-SIGKILL em `tv_acc_recover`). Se a lista foi perdida por um teste, rodar qualquer script
(`scripts/tcl-smoke.sh`, por exemplo) devolve o estado antes de medir.

A Home original continua recuperável pela opção de recuperação da própria Home (Home 3× em 10 s)
ou por `scripts/tcl-restore.ps1`.
