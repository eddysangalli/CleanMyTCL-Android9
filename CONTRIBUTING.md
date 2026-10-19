# Contributing to CleanMyTCL-Android9

Obrigado por ajudar mais TVs TCL a ficarem rápidas e limpas. Três tipos de contribuição são especialmente bem-vindos.

---

## 1. Adicione o seu modelo de TCL

Cada firmware TCL traz apps diferentes. Se você rodou a auditoria (ou fez à mão) numa TCL que não está em `devices/`, adicione:

1. Crie `devices/tcl-<modelo>-<chipset>-<firmware>/debloat.txt`. Copie o formato de `devices/tcl-beyondtv2-rtd2851-r851t02-v660/debloat.txt`: um pacote por linha, agrupados com comentário explicando o grupo, e comentário final em itens não-óbvios ("reativar para atualização de firmware", "necessário para HDMI-CEC", etc.).
2. No cabeçalho, coloque: modelo exato (ex.: `TCL 55P635`), versão Android, build do firmware (`getprop ro.build.fingerprint`), chipset (`getprop ro.board.platform`), RAM, arquitetura. `tools/tv.py --probe` (do upstream) imprime os três; nossos scripts usam `$env:TCL_TV_HOST`.
3. Liste **apenas pacotes que você realmente desativou e conviveu por alguns dias**. Anote o que quebrou e o que reativou.
4. Adicione uma linha na tabela de `devices/README.md` e em `COMPATIBILITY.md`.
5. Se o app de TV ao vivo ou o launcher original tiver nome de pacote diferente, anote no cabeçalho; `scripts/tcl-restore.ps1` e `scripts/tcl-launcher-install.sh` aceitam como argumento.

> **Nunca liste pacotes para *desinstalar* (`pm uninstall`).** Tudo aqui usa `pm disable-user --user 0`, que é reversível nas próprias configurações da TV se as ferramentas sumirem.

---

## 2. Melhore o launcher (Eddy TV / CleanMyTCL Launcher)

O launcher vive em `launcher/` e é deliberadamente pequeno: Java puro, AndroidX RecyclerView + DynamicAnimation, sem Kotlin, sem Compose, sem rede. Alvo: TVs com 2 GB RAM (1 GB zRAM), então:

- **Sem blur, sem backgrounds de vídeo, sem polling, sem services em background.**
- Tudo que depende de TV específica vai em `app/src/main/res/values/config.xml`, não no código.
- Navegação por D-pad deve ser determinística. Teste Cima/Baixo/Esquerda/Direita de **toda borda**.
- Valide em TV real, não só emulador. O jeito mais fácil: injeção de tecla remota + screenshot:
  ```bash
  adb shell input keyevent KEYCODE_DPAD_DOWN
  adb exec-out screencap -p > screenshot.png
  ```
  Inclua o screenshot no PR.

**Build:**
```bash
cd launcher
# JDK 17 + Android SDK (platform 34, build-tools 34.0.0)
./gradlew assembleRelease
# ou use o script do upstream:
TV_HOST=<ip> ../tools/tv.py --install-launcher
```

**Ideias que cabem:** screensaver idle com relógio/cor ambiente, icon packs por TV para apps sem banner, tela de settings para editar dock na TV, localização (já temos PT-BR/EN).
**Ideias que NÃO cabem:** linhas de recomendação, feeds de conteúdo, anúncios, telemetria, animações contínuas, dependências de rede.

---

## 3. Melhore os scripts e ferramentas

Nossos scripts estão em `scripts/` (PowerShell + Bash). Se seu assistente travou em algum passo, a correção geralmente vira uma frase a mais no script, não um script novo.

- `scripts/tcl-common.ps1` / `scripts/tcl-lib.sh`: helpers compartilhados (IP, ADB, cores, logs).
- `scripts/tcl-audit.sh`: diagnóstico somente-leitura (CPU, alarms, versões).
- `scripts/tcl-smoke.sh`: teste de fumaça (HDMI, Netflix, voz, reboot).
- `scripts/tcl-try-disable.sh`: desativa UM pacote, roda smoke, reverte se falhar.
- `scripts/tcl-bench.sh`: benchmark repetido com estatísticas.
- `scripts/tcl-backup.ps1` / `tcl-restore.ps1`: snapshot e restauração.

Mantenha-os brand-neutral para TCL; conhecimento específico de modelo vai em `devices/<perfil>/`.

---

## 4. Mostre o que você construiu

Fez seu próprio launcher, um port pra outra plataforma, ou um add-on grande/pessoal demais pro launcher compartilhado? Coloque no seu repositório e adicione uma linha no [`SHOWCASE.md`](SHOWCASE.md). Essa página é o lugar para launchers alternativos, forks com visual diferente, e ferramentas em torno do launcher; este repo mantém **um launcher de referência pequeno** e aponta para o resto.

**O que fica neste repo:** listas de debloat por modelo, correções e features pequenas/opt-in pro launcher de referência, melhorias nos scripts.
**O que vai no SHOWCASE:** um segundo launcher, um restyle, um port de plataforma, qualquer coisa com seus próprios services em background ou acesso à rede.

---

## Pull Requests

- Um tópico por PR. Pequeno é bom.
- Diga em qual TV testou (modelo + firmware).
- Nenhuma dependência nova no launcher sem discutir numa issue antes.
- Seja gentil nos reviews. A maioria dos contribuidores aqui está fazendo isso pra TV dos pais.

Ao contribuir, você concorda que seu trabalho é lançado sob a licença MIT do repo.

---

## Checklist rápido antes de abrir PR

- [ ] Testou na TV real (não só emulador)
- [ ] Rodou `scripts/tcl-smoke.sh` e passou
- [ ] Rodou `scripts/tcl-bench.sh` (3+ repetições) e não houve regressão de p95
- [ ] Screenshots incluídas (antes/depois para UI, gráfico para perf)
- [ ] `tools/secret-scan.ps1` passa (zero segredos)
- [ ] Documentação atualizada se mudou comportamento visível
- [ ] Commits atômicos, mensagens claras (Conventional Commits opcional)

---

## Como rodar os testes localmente

```powershell
# PowerShell
$env:TCL_TV_HOST = "<ip-da-tv>"
powershell -ExecutionPolicy Bypass -File scripts\tcl-backup.ps1
bash scripts/tcl-smoke.sh
bash scripts/tcl-bench.sh baseline 5
```

```bash
# Git Bash
export TCL_TV_HOST=<ip-da-tv>
./scripts/tcl-backup.ps1
./scripts/tcl-smoke.sh
./scripts/tcl-bench.sh baseline 5
```