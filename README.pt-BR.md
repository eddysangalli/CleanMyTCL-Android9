# CleanMyTCL-Android9 (português)

Projeto comunitário de otimização do Android TV 9 e launcher leve para televisores TCL, com perfis
de aparelhos testados, debloat reversível e medições de desempenho.

**Fork do [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (antes `tv-vibehack`), de Gathin, licença MIT.**
Créditos em [NOTICE.md](NOTICE.md); origem de cada parte em [docs/PROVENANCE.md](docs/PROVENANCE.md).

## Minha TV é compatível?

**Só um modelo foi validado até agora** (TCL BeyondTV2, Realtek RTD2851, firmware R851T02 V660).
Não presuma que outra TCL com Android 9 funciona igual. Veja a [matriz de compatibilidade](COMPATIBILITY.md).

## Regras do projeto

1. **Desempenho acima de tudo.** Nada que deixe o Home, a navegação ou a abertura de apps mais lentos.
   Toda mudança é medida (p50 e piores casos) contra a versão anterior.
2. **Tudo reversível.** Apps de sistema só com `pm disable-user`, um por vez, com teste e comando de volta.
   Nada de root, firmware, desinstalar de `/system`.
3. **Sem promessas sem medição.** Números vêm com método, aparelho e dados brutos.
4. **O launcher original nunca é desativado antes de o novo estar instalado e testado.**

## Começando (Windows 11)

Guia completo (em inglês, com comandos idênticos): [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md).

```powershell
$env:TCL_TV_HOST = "<ip-da-tv>"
powershell -ExecutionPolicy Bypass -File scripts\tcl-backup.ps1     # 1. foto do estado atual
bash scripts/tcl-smoke.sh                                           # 2. confere funções essenciais
bash scripts/tcl-try-disable.sh com.tcl.bi                          # 3. desativa UM pacote, testa e reverte se falhar
```

Os scripts e mensagens estão em português; a documentação principal está em inglês para alcançar
mais donos de TCL. Contribuições em português são bem-vindas.

## Personalização (ex.: "Eddy TV")

O launcher público não tem nome fixo: nome do usuário, nome do sistema, cor de destaque, dock e
modo ultraleve são configurados **na própria TV** (Opções do Início). Veja
[docs/PERSONALIZATION.md](docs/PERSONALIZATION.md).
