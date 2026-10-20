# CleanMyTCL-Android9 (português)

Projeto comunitário de otimização do Android TV 9 e launcher leve para televisores TCL, com perfis de aparelhos testados, debloat reversível e medições de desempenho.

**Fork do [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (antes `tv-vibehack`), de Gathin, licença MIT.**  
Créditos em [NOTICE.md](NOTICE.md); origem de cada parte em [docs/PROVENANCE.md](docs/PROVENANCE.md).

---

## 📸 Capturas de tela

*Capturado no aparelho real (TCL BeyondTV2, R851T02 V660). A localização do clima na captura de
opções está desfocada por privacidade; o resto são os PNGs originais.*

| Home original do Android TV | Home do Eddy TV (v0.5.4) |
|:---:|:---:|
| ![Home original](docs/assets/stock-home.png) | ![Eddy TV Home](docs/assets/eddy-home.png) |
| *Linhas patrocinadas e de recomendação, conteúdo de rede buscado a cada abertura, animação permanente a 60 fps* | *Saudação por horário, linha do clima, sem linha patrocinada, sem feed, 0 fps parado* |

| Grade de aplicativos (D-pad) | Menu do aplicativo |
|:---:|:---:|
| ![Grade de apps](docs/assets/eddy-launcher-apps.png) | ![Menu do app](docs/assets/eddy-tile-menu.png) |
| *Linhas de apps instalados; no modo ultraleve a grade salta em vez de rolar* | *Segure em um app: fixar no dock, ver informações, ocultar* |

| Opções na TV | Custo ocioso do próprio launcher |
|:---:|:---:|
| ![Opções](docs/assets/eddy-options.png) | ![Gráfico ocioso](docs/assets/charts/bench-idle-clean-launcher.png) |
| *Tudo se configura na própria TV: nome do usuário, mensagem, cor de destaque, movimento, clima, apps ocultos, restaurar* | *Mesma TV, mesma sessão, apps fechados: o Home original custa 10,5% de um núcleo e redesenha a 3,8 fps; o Eddy TV custa 0,0–0,9% e redesenha a 0,03–0,17 fps. [Método e resultados completos](benchmarks/results/2026-10-19-v0.5.2.md).* |

| Menu de entradas (HDMI / TV ao vivo) | Modo ultraleve |
|:---:|:---:|
| ![Entradas](docs/assets/eddy-inputs.png) | ![Ultraleve](docs/assets/eddy-ultralight.png) |
| *As entradas HDMI e a TV ao vivo, direto do Home: as teclas 1–3 abrem as três entradas HDMI que esta unidade lista, na mesma ordem do menu Entradas (o launcher mapeia 1–4 para as entradas que o sistema expõe; aqui não existe uma quarta entrada)* | *Movimento suave desligado: o foco salta em vez de deslizar, 1 quadro desenhado por movimento em vez de 38* |

> Cada número em [benchmarks/results/](benchmarks/results/) está marcado com a versão exata do
> launcher e as condições em que foi medido. Os gráficos são gerados a partir dos dados medidos por
> `scripts/tcl-make-charts.py`; nenhum número é digitado à mão.

> **Adicione suas capturas:** use `scripts/tcl-screencap.sh <arquivo.png>` (ou o
> `scripts\tcl-capture-screenshot.ps1`). Os dois gravam PNG binário de verdade — veja
> [docs/SCREENSHOTS.md](docs/SCREENSHOTS.md) por que o comando de uma linha não funciona — e confira
> o resultado antes de commitar.

---

## Minha TV é compatível?

**Só um modelo foi validado até agora** (TCL BeyondTV2, Realtek RTD2851, firmware R851T02 V660).  
Não presuma que outra TCL com Android 9 funciona igual. Veja a [matriz de compatibilidade](COMPATIBILITY.md).

---

## Regras do projeto

1. **Desempenho por padrão. Personalização por escolha. Transparência sobre o custo.** Uma instalação nova começa em Desempenho: navegação imediata, fundo preto e nenhum efeito contínuo. Equilibrado e Visual são presets opcionais, feitos com os mesmos componentes. Eles podem usar mais GPU/RAM e adicionar jank medido; cada um é reversível e informa o custo conhecido, ou diz que ainda não foi medido neste aparelho. Estabilidade, controle, HDMI, áudio, aplicativos e recuperação nunca são opcionais.
2. **Tudo reversível.** Apps de sistema só com `pm disable-user`, um por vez, com teste e comando de volta. Nada de root, firmware, desinstalar de `/system`.
3. **Sem promessas sem medição.** Números vêm com método, aparelho e dados brutos. Medições antigas nunca são apresentadas como resultado de um build novo.
4. **O launcher original nunca é desativado antes de o novo estar instalado e testado.**

**Estado do projeto (20/10/2026):** o código da `main` está na **v0.5.8 / versionCode 30**, mas ainda não existe uma GitHub Release/tag formal. A última instalação documentada com hash conferido na TV é a build de validação v0.5.7 (code 29); a bateria completa e o teste de estabilidade de 8 horas da v0.5.8 ainda estão pendentes. Veja [compatibilidade](COMPATIBILITY.md) e [notas da v0.5.8](docs/RELEASE-0.5.8.md).\n\n## Presets de experiência

A mesma Home tem três presets, não três launchers. As opções individuais continuam ajustáveis; mudar uma delas marca a experiência como **Personalizado**.

| Preset | Movimento | Fundo | Custo conhecido |
|---|---|---|---|
| **Desempenho** (padrão em instalação nova) | Foco imediato, sem mola | Preto | Sem decodificação de wallpaper, sem efeitos contínuos |
| **Equilibrado** | Foco suave, mola atual | Preto | Sem wallpaper; usa a mesma mola do Visual |
| **Visual** | Foco suave, mola atual | Papel de parede Aurora | Medido na TCL BeyondTV2 com a v0.5.6: +9 MB de PSS, quadros atrasados de 0,19% para 6,89%; a resposta mediana da tecla ficou em 17 ms |

O A/B da v0.5.6 e o método estão em [benchmarks/results/2026-10-19-v0.5.6-wallpaper.md](benchmarks/results/2026-10-19-v0.5.6-wallpaper.md). Esses números pertencem à v0.5.6, não a builds futuros; serão medidos novamente antes de serem atribuídos à v0.5.7. Atualizações mantêm o movimento/wallpaper atuais e mostram **Personalizado** até você escolher um preset. Detalhes: [docs/EXPERIENCE-PRESETS.md](docs/EXPERIENCE-PRESETS.md).

---

## Habilitar ADB na TCL (Android TV 9) — Guia Completo

### Método A: Rede (recomendado, sem cabo)

> Funciona na TCL BeyondTV2 e na maioria das TCL Android TV 9. Requer TV e PC na mesma rede (Wi-Fi ou cabo).

#### Passo a passo

1. **Abra as Opções do Desenvolvedor**
   - Pressione **Home** no controle → **Configurações** (engrenagem) → **Preferências do dispositivo** → **Sobre**
   - Role até **Número da compilação** e pressione **OK/Selecionar 7 vezes rápido**
   - Verá: *"Você agora é um desenvolvedor!"*

2. **Ative a Depuração de Rede**
   - Volte em **Preferências do dispositivo** → **Opções do desenvolvedor**
   - Ligue **ON**: **Depuração de rede** (alguns firmwares: "Depuração ADB" + "Depuração de rede")
   - Ligue **ON**: **Depuração USB** (necessário em alguns builds mesmo para rede)
   - **Opcional recomendado**: Ligue **ON** "Desativar tempo limite de autorização ADB" (mantém autorização persistente)

3. **Descubra o IP da TV**
   - Configurações → **Rede e Internet** → sua conexão (Wi-Fi ou Ethernet)
   - Anote o **endereço IP** (ex.: `192.168.1.50`)

4. **Conecte do Windows**
   - Abra **PowerShell** ou **Git Bash**
   - Rode:
     ```powershell
     adb connect 192.168.1.50:5555
     ```
   - **Na tela da TV**: aparece um diálogo — **"Permitir depuração USB?"** → marque **"Sempre permitir deste computador"** → **OK**

5. **Verifique**
   ```powershell
   adb devices
   # Deve mostrar: 192.168.1.50:5555  device
   ```

6. **Fixe o IP (opcional mas recomendado)**
   ```powershell
   # PowerShell
   $env:TCL_TV_HOST = "192.168.1.50"
   # Git Bash
   export TCL_TV_HOST=192.168.1.50
   ```
   > Adicione ao seu perfil do shell (`$PROFILE` ou `~/.bashrc`) para tornar permanente.

---

### Método B: USB ADB (se a rede falhar)

> Requer cabo USB-A para USB-A (ou USB-C); porta de serviço da TCL costuma ser escondida.

1. Ative **Depuração USB** nas Opções do Desenvolvedor (mesmo caminho acima)
2. Conecte TV ↔ PC via USB (tente porta de serviço ou USB 2.0)
3. No PC: `adb devices` → autorize na TV
4. Use `adb shell` diretamente (sem IP necessário)

---

### Método C: Menu de Serviço Oculto (se Opções do Desenvolvedor sumiram)

> Alguns firmwares de operadora/região escondem as Opções do Desenvolvedor.

1. Pressione: **Configurações** → **Preferências do dispositivo** → **Sobre**
2. Pressione **OK** em **Número da compilação** 7 vezes
3. Se ainda não aparecer, tente sequência no controle: **Home** ×5 → **Cima** ×3 → **Direita** ×2 → **OK** (abre menu oculto em algumas TCL)
4. Ou: Instale apps "ADB Enabler" da Play Store (precisa sideload primeiro)

---

### Solução de Problemas ADB

| Sintoma | Correção |
|---------|----------|
| `connection refused` | TV e PC na mesma sub-rede? Isolamento AP (AP isolation) desligado no roteador? |
| `unauthorized` | Aceite o prompt na TV; revogue em Opções do desenvolvedor → "Revogar autorizações de depuração USB" e tente de novo |
| `offline` | `adb kill-server && adb start-server`; reinicie a TV |
| Porta 5555 não abre | Reative depuração de rede; alguns firmwares precisam reiniciar após ativar |
| "Opções do desenvolvedor" não aparece | Método C; ou veja Configurações → Apps → Mostrar sistema → "Android TV Core Services" → ativar |

---

### Notas de Segurança

- **Depuração de rede** expõe ADB na LAN porta 5555. Use apenas em rede doméstica confiável.
- **Revogue autorizações** quando terminar: Opções do desenvolvedor → "Revogar autorizações de depuração USB"
- **Desative Depuração de rede** quando não estiver desenvolvendo ativamente
- Os scripts deste projeto exigem `$env:TCL_TV_HOST` — eles **não** têm IP fixo no código

---

## Começando (Windows 11)

Guia completo (em inglês, com comandos idênticos): [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md).

```powershell
$env:TCL_TV_HOST = "<ip-da-tv>"
powershell -ExecutionPolicy Bypass -File scripts\tcl-backup.ps1     # 1. foto do estado atual
bash scripts/tcl-smoke.sh                                           # 2. confere funções essenciais
bash scripts/tcl-try-disable.sh com.tcl.bi                          # 3. desativa UM pacote, testa e reverte se falhar
```

Os scripts e mensagens estão em português; a documentação principal está em inglês para alcançar mais donos de TCL. Contribuições em português são bem-vindas.

---

## Personalização (ex.: "Eddy TV")

O launcher público não tem nome fixo: nome do usuário, nome do sistema, cor de destaque, dock e preset de experiência são configurados **na própria TV** (Opções do Início). Veja [docs/PERSONALIZATION.md](docs/PERSONALIZATION.md) e [docs/EXPERIENCE-PRESETS.md](docs/EXPERIENCE-PRESETS.md).

---

## Relação com upstream

Este repositório mantém o histórico do CleanYourTV até o commit `9ef40c1` e faz branch a partir daí. O upstream evoluiu (v2.x, temas, serviço de tecla Home). Rastreamos como remote `upstream` e fazemos **cherry-pick** de correções após revisar conflitos, mudanças de comportamento e impacto de performance medido ([docs/BRANCHING.md](docs/BRANCHING.md)). A revisão de 20/10/2026, com os portes pequenos e o que ficou de fora, está em [docs/UPSTREAM-AUDIT-2026-10-20.md](docs/UPSTREAM-AUDIT-2026-10-20.md). Melhorias que não são específicas de TCL devem ser oferecidas de volta ao CleanYourTV.

---

## Aviso

Use por sua conta e risco. Desativar o pacote errado pode quebrar HDMI, controle remoto, voz ou a Play Store. Sempre faça backup primeiro e mude uma coisa de cada vez. Não afiliado à TCL ou Google.

Licença: MIT ([LICENSE](LICENSE)). Componentes de terceiros: [NOTICE.md](NOTICE.md).
