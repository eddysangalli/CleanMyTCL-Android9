# CleanMyTCL-Android9 v0.6.0

**APK:** `tvhome-0.6.0-code32.apk`, 516 697 bytes, SHA-256
`76A14A02192B6B74D4B0E66F56842F9D5E4FF054F39C44EA2FD2131740B196E4`.
**Instalado e validado na BeyondTV2 V660 em 21/10/2026**, versionCode 32.

## O que entra

| Mudança | Origem | Custo medido |
|---|---|---|
| **Linha de notícias (RSS) na Home**, com foto, manchetes e leitor embutido | port do repositório geral (`46fccd0`), adaptado | +6,1 MB de PSS e +73 ms de abertura **quando ligada**; jank igual ao desligado |
| **Leitor de artigos** com página de erro legível e sem barra de cookies | correção local | — |
| **Feed da Folha removido** (parado em 2015) e cache versionado | correção local | — |
| **#585**: largar os tiles quando a memória aperta e a Home está oculta | upstream #585, adaptado | não exercitável por ADB nesta firmware (ver abaixo) |

A linha de notícias é **opt-in e desligada por padrão**. Quem não liga não paga nada: sem request, sem
view, sem desenho — só uma leitura de preferência no resume.

## Medições na V660 (mesmo APK, só a preferência muda)

| Métrica | Desligada | Ligada |
|---|---:|---:|
| Quadros atrasados em regime (5+ corridas) | 0,0–0,8 % | **0,0–2,5 %** (mediana ~1 %) |
| Views | 104 | 105 |
| PSS (`TOTAL PSS` do `tcl-view-count`) | 28,3 MB | 34,4 MB (**+6,1 MB**) |
| Cold start (p50 de 5) | 440 ms | 513 ms (**+73 ms**) |

O jank começou em **83,6 %** no primeiro port. As sete causas e o método estão em
[`benchmarks/results/2026-10-21-rss-row.md`](../benchmarks/results/2026-10-21-rss-row.md); as duas
mais caras eram um gradiente full-bleed rasterizado em software (42 pontos percentuais) e um
`canvas.clipPath` que jogava o banner inteiro para rasterização de CPU (~8 pontos).

**Critério aplicado:** recurso visual opt-in, reversível e com custo declarado, como manda a regra do
projeto. O que se paga é memória e abertura, não fluidez.

## O que foi verificado

- **43 testes** Python (`python -m unittest discover -s tests`) OK.
- **Smoke 24/24** na TV, sem crash/ANR.
- Acessibilidade: `KeyRemapService` habilitado e vinculado depois de cada instalação; teclas
  respondendo (foco andou pelo dock).
- Linha de notícias: aparece com manchete atual e foto, gira sozinha, abre o artigo no leitor (WebView),
  Voltar volta para a Home.
- Página de erro do leitor: exercitada com um link `http://` (a build de teste exportou a activity por
  um ciclo, depois voltou para `exported="false"`), e a mensagem explica o bloqueio em português.
- Barra de cookies: o Leouve monta o `consent-box` depois do `onPageFinished`, por isso a ocultação
  passou a escrever no DOM em vez de injetar `<style>` (que o CSP do site descarta). Confirmado na TV.
- Cache: ao remover uma fonte do catálogo, a versão do cache sobe e o arquivo antigo é apagado — senão
  a linha sumia até o próximo ciclo de 10 min.

## O que não foi validado

- **#585** (largar tiles sob pressão): o `am send-trim-memory` recusa níveis de fundo nesta firmware
  mesmo com a Home atrás — o processo fica em `curProcState=2` por causa do serviço de acessibilidade.
  O gancho só roda sob pressão real do sistema, que não é reproduzível sob demanda. Ele não age com a
  Home visível (`started`), então não pode apagar a tela de quem está olhando.
- Caminho dos cards HDMI **pelo controle físico**.
- Imagem real nas entradas HDMI, standby, boot a frio e estabilidade de 8 h.
- Retorno à Home (Settings → Home) com a linha ligada.

## Recuperação

```bash
adb install -r tvhome-0.5.9-code31.apk          # release v0.5.9
# Devolve a lista COMPLETA de servicos de acessibilidade, nao so o nosso: escrever um servico
# isolado substitui a lista inteira e derruba os servicos de outros apps.
adb shell 's=$(settings get secure enabled_accessibility_services); case "$s" in
  *br.eddy.tvhome/com.gathin.tvhome.KeyRemapService*) ;;
  null|"") settings put secure enabled_accessibility_services br.eddy.tvhome/com.gathin.tvhome.KeyRemapService ;;
  *) settings put secure enabled_accessibility_services "$s:br.eddy.tvhome/com.gathin.tvhome.KeyRemapService" ;;
esac'
```
