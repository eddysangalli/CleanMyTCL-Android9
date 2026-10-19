# Feito com CleanMyTCL-Android9

Launchers, ports e add-ons que pessoas construíram em cima deste repo, ou com seus scripts, que vivem em seus próprios repositórios. O launcher em `launcher/` fica pequeno de propósito; esta página é onde as ideias maiores vão, com as pessoas que as fizeram.

> **Nada aqui é revisado ou mantido por este repo.** Leia o README do projeto, veja em qual TV foi construído e trate do jeito que você trataria qualquer app que você faz sideload. Cada linha linka pro issue tracker do próprio projeto.

---

## Projetos derivados

| Projeto | O que é | Construído em | Por |
|---|---|---|---|
| *(adicione o seu aqui)* | | | |

---

## Adicione o seu

Abra um pull request que adiciona uma linha. As regras são curtas:

- É open source, e o link vai para um repositório ou branch que as pessoas podem ler e compilar.
- Diz em qual TV ou plataforma foi construído e testado (modelo + firmware).
- Não coleta dados nem faz "phone home" além do que diz no seu próprio README.
- É seu, ou você tem a bênção do autor pra listar.

Um screenshot na descrição do PR é apreciado e será linkado da linha. Se o projeto é um fork deste repo, diga o que muda; se é algo novo feito a partir dos nossos scripts/docs, diga também — esses são os mais legais de ler.

---

## Ideias de projetos que caberiam no Showcase

- **Launcher temático** (ex.: visual estilo webOS, Fire TV, Apple TV) mantendo performance
- **Port para outra arquitetura** (ex.: TCL com chipset Amlogic, ou Android 11/12/13)
- **Add-on de screensaver** (relógio ambiente, aurora, fotos) que não roda quando Home não está visível
- **Ferramenta de backup/restore gráfica** (GUI no PC ou app na TV)
- **Integração Home Assistant / MQTT** para controle da TV via automação residencial
- **Gerenciador de perfis de debloat** (GUI para escolher perfis "conservador", "agressivo", "mínimo")
- **Launcher com dock estilo macOS** (mantendo ultralight mode)
- **App complementar no celular** para configurar o launcher remoto (via ADB over network)
- **Tema "Eddy TV" empacotado** (cores, ícones, saudações) como instalador separado
- **Script de migração** de CleanYourTV genérico → CleanMyTCL-Android9

---

## Nosso launcher de referência (para comparação)

| Feature | CleanMyTCL Launcher (em `launcher/`) |
|---|---|
| Linguagem | Java 17 |
| UI | RecyclerView + DynamicAnimation |
| RAM alvo | ≤ 2 GB (1 GB zRAM) |
| Rede | Zero |
| Background services | Zero |
| D-pad | Determinístico, testado em bordas |
| Configuração | Toda na TV (Opções do Início) |
| Personalização | Nome, saudação, cor, dock, HDMI keys, ultralight |
| Benchmarks | Medidos e publicados |

Se o seu projeto adiciona **qualquer** uma destas: background service, polling, rede, blur, vídeo, animação contínua — ele **não** entra no launcher de referência, mas **pode** ir no Showcase.

---

## Como citar este projeto

Se você fez um fork ou usou nossos scripts/debloat como base:

```markdown
Baseado em [CleanMyTCL-Android9](https://github.com/eddysangalli/CleanMyTCL-Android9) —
otimização Android TV 9 para TCL (fork de [CleanYourTV](https://github.com/Gathin23/cleanyourtv)).
```

---

## Links úteis

- [Matriz de compatibilidade](COMPATIBILITY.md) — veja onde seu projeto foi testado
- [Perfis de dispositivos](devices/) — debloat lists por modelo
- [Metodologia de benchmark](benchmarks/METHODOLOGY.md) — como medimos performance
- [Scripts de diagnóstico](scripts/) — tcl-audit, tcl-smoke, tcl-bench
- [Recuperação](docs/RECOVERY.md) — como voltar atrás se algo quebrar