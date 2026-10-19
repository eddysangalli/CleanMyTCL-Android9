# CleanMyTCL-Android9 (português)

Projeto comunitário de otimização do Android TV 9 e launcher leve para televisores TCL, com perfis de aparelhos testados, debloat reversível e medições de desempenho.

**Fork do [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (antes `tv-vibehack`), de Gathin, licença MIT.**  
Créditos em [NOTICE.md](NOTICE.md); origem de cada parte em [docs/PROVENANCE.md](docs/PROVENANCE.md).

---

## 📸 Capturas de tela

*Capturado no aparelho real (TCL BeyondTV2, R851T02 V660) via `adb exec-out screencap`.*

| Home Original TCL | Home do Eddy TV (v0.5) |
|:---:|:---:|
| ![Home Original TCL](https://raw.githubusercontent.com/eddysangalli/CleanMyTCL-Android9/develop/docs/assets/stock-home.png) | ![Eddy TV Home](https://raw.githubusercontent.com/eddysangalli/CleanMyTCL-Android9/develop/docs/assets/eddy-home.png) |
| *Home original TCL: linha patrocinada, grid denso, sem acesso direto ao HDMI* | *Saudação por horário, dock configurável, sem anúncios, sem feed* |

| Grade de aplicativos (D-pad) | Configurações na TV |
|:---:|:---:|
| ![Grade de apps](https://raw.githubusercontent.com/eddysangalli/CleanMyTCL-Android9/develop/docs/assets/eddy-launcher-apps.png) | ![Configurações](https://raw.githubusercontent.com/eddysangalli/CleanMyTCL-Android9/develop/docs/assets/eddy-settings.png) |
| *Linhas de apps instalados; modo ultraleve dispensa a animação de rolagem* | *Nome do usuário, nome do sistema, cor de destaque, dock e atalhos editados na TV* |

| Instantâneo de desempenho | Gráfico de benchmark |
|:---:|:---:|
| ![Desempenho](https://raw.githubusercontent.com/eddysangalli/CleanMyTCL-Android9/develop/docs/assets/perf-dashboard.png) | ![Benchmarks](https://quickchart.io/chart?c=%7Btype%3A%27bar%27%2Cdata%3A%7Blabels%3A%5B%22Boot%20frio%22%2C%22Standby%22%2C%22Home%22%2C%22D-pad%22%2C%22Netflix%22%2C%22YouTube%22%2C%22Globoplay%22%2C%22Spotify%22%2C%22CPU%20idle%22%2C%22PSS%22%2C%22RAM%20livre%22%2C%22zRAM%22%5D%2Cdatasets%3A%5B%7Bdata%3A%5B92%2C3.2%2C0.8%2C0.04%2C87%2C12%2C15%2C8%2C1.2%2C42%2C520%2C280%5D%2CbackgroundColor%3A%5B%22%23e74c3c%22%2C%22%23e67e22%22%2C%22%23f39c12%22%2C%22%2327ae60%22%2C%22%23e74c3c%22%2C%22%233498db%22%2C%22%239b59b6%22%2C%22%231abc9c%22%2C%22%2327ae60%22%2C%22%2334495e%22%2C%22%232c3e50%22%2C%22%237f8c8d%22%5D%7D%5D%7D%2Coptions%3A%7BindexAxis%3A%27y%27%2Cplugins%3A%7Blegend%3A%7Bdisplay%3Afalse%7D%2Ctitle%3A%7Bdisplay%3Atrue%2Ctext%3A%22Benchmarks%20TCL%20BeyondTV2%22%7D%7D%2Cscales%3A%7Bx%3A%7BbeginAtZero%3Atrue%7D%7D%7D%7D) |
| *`dumpsys cpuinfo` + `/proc/meminfo` no aparelho* | *Valores medidos; veja [benchmarks/results/](benchmarks/results/) para o método e a versão exata de cada número* |

> ⚠️ **O gráfico mistura medições de setups diferentes.** As linhas do launcher foram medidas na
> **v0.5.2** (o build instalado no aparelho); as linhas de abertura de apps no Home **original**.
> Cada número em [benchmarks/results/tcl-beyondtv2-v660.md](benchmarks/results/tcl-beyondtv2-v660.md)
> está marcado com a versão e o setup exatos de onde veio — nenhum número é reaproveitado entre versões.

> **Adicione suas capturas:** Coloque PNGs em `docs/assets/` e referencie com URL
> `https://raw.githubusercontent.com/eddysangalli/CleanMyTCL-Android9/<branch>/docs/assets/<arquivo>`.
> Veja [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md) para capturar via `adb exec-out screencap`.

---

## Minha TV é compatível?

**Só um modelo foi validado até agora** (TCL BeyondTV2, Realtek RTD2851, firmware R851T02 V660).  
Não presuma que outra TCL com Android 9 funciona igual. Veja a [matriz de compatibilidade](COMPATIBILITY.md).

---

## Regras do projeto

1. **Desempenho acima de tudo.** Nada que deixe o Home, a navegação ou a abertura de apps mais lentos. Toda mudança é medida (p50 e piores casos) contra a versão anterior.
2. **Tudo reversível.** Apps de sistema só com `pm disable-user`, um por vez, com teste e comando de volta. Nada de root, firmware, desinstalar de `/system`.
3. **Sem promessas sem medição.** Números vêm com método, aparelho e dados brutos.
4. **O launcher original nunca é desativado antes de o novo estar instalado e testado.**

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

O launcher público não tem nome fixo: nome do usuário, nome do sistema, cor de destaque, dock e modo ultraleve são configurados **na própria TV** (Opções do Início). Veja [docs/PERSONALIZATION.md](docs/PERSONALIZATION.md).

---

## Relação com upstream

Este repositório mantém o histórico do CleanYourTV até o commit `9ef40c1` e faz branch a partir daí. O upstream evoluiu (v2.x, temas, serviço de tecla Home). Rastreamos como remote `upstream` e fazemos **cherry-pick** de correções após revisar conflitos, mudanças de comportamento e impacto de performance medido ([docs/BRANCHING.md](docs/BRANCHING.md)). Melhorias que não são específicas de TCL devem ser oferecidas de volta ao CleanYourTV.

---

## Aviso

Use por sua conta e risco. Desativar o pacote errado pode quebrar HDMI, controle remoto, voz ou a Play Store. Sempre faça backup primeiro e mude uma coisa de cada vez. Não afiliado à TCL ou Google.

Licença: MIT ([LICENSE](LICENSE)). Componentes de terceiros: [NOTICE.md](NOTICE.md).