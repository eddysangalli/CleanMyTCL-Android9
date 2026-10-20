# Revisão dos PRs de @roky146 — 20/10/2026

Escopo: PRs abertos contra `develop`, revisados, integrados e testados localmente. Nenhum release ou
tag foi publicado; `main` permanece em v0.5.6 (`94e4890`).

Integração local: branch `integrate/all-prs`, com um commit de merge por PR (`--no-ff`), para que
cada PR continue rastreável e nenhum commit seja duplicado. A branch ainda **não** foi publicada
como `develop` nem para o repositório upstream: a decisão de mesclar no upstream e de encerrar os
PRs cabe ao mantenedor.

## Mesclados em `develop` (já publicados)

| PR | Título | Merge commit | Validação |
|---|---|---|---|
| #2 | `fix(smoke): make the remote, test app and mic check configurable per device` | `8563348` | Revisão do diff; scripts com valores padrão da BeyondTV2 preservados. |
| #3 | `fix(smoke): send the search key on the first Assistant attempt` | `fe7e146` | Revisão do diff; mudança pequena no fluxo de busca. |
| #4 | `feat(launcher): make the weather line localizable` | `a2e9642` | `assembleRelease` passou; clima em pt-BR continua funcional na TV. |
| #9 | `fix(launcher): focus ring misplaced for the first seconds after the launcher starts` | `21a9826` | `assembleRelease` passou; corrigido em seguida no commit local `f5d8cc6`, porque o anel ainda podia ficar no tamanho de projeto após um cold start. |
| #5 | `feat(launcher): Spanish translation` | `82ed833` | `assembleRelease` passou. As strings novas dos presets ainda não têm tradução e caem no inglês. |

Os agradecimentos foram registrados nas revisões dos PRs mesclados e em comentários nos PRs
integrados. Créditos em [`launcher/NOTICE.md`](../launcher/NOTICE.md).

## Integrados localmente em `integrate/all-prs` (ainda não publicados)

Os cinco PRs abaixo foram integrados **com resolução semântica dos conflitos**, não aceitação
cega. As decisões de projeto do CleanMyTCL (presets de experiência, aviso de custo do fundo,
fundo preto no modo ultralight, correção de anel no cold start) foram preservadas.

| PR | Merge local | O que foi aceito | O que foi ajustado na integração |
|---|---|---|---|
| #6 | `28225ca` | Menu de opções como painel à direita, com steppers que mantêm o menu aberto. | A linha **Experiência** (Performance/Balanced/Visual/Personalizado) e o caminho de aviso de custo do fundo foram mantidos: o PR havia sido escrito antes dos presets. O item de fundo usa o `stepWallpaper` local, que abre o diálogo de custo antes de aplicar. |
| #7 | `571c2da` | Fundos extras: cores sólidas escuras, Aurora, Earth at night (NASA, domínio público). | Os ids de fundo passaram a ser a referência salva (`background=black`, …), com a flag booleana antiga mantida só como fallback de migração. O Aurora continua sendo o único fundo com custo medido; os demais são declarados **não medidos** nesta TV. |
| #8 | `84e34e4` | `BlurHash.java` (decodificador MIT), três blurs de fundo, `scripts/tcl-blurhash.ps1` e o guia em `docs/PERSONALIZATION.md`. | O `stepWallpaper` local foi estendido: um BlurHash também abre o diálogo de aviso, e trocar de Aurora para um blur não medido volta a avisar. Sem assets de imagem no APK: só o hash. |
| #10 | `5355f15` | Spring Material 3 Expressive (stiffness 380, damping 0.8), mola vertical em coordenadas de conteúdo do grid e "bounce" sutil nas bordas da grade. | A correção local de anel no cold start (`refitGlass`, `f5d8cc6`) foi preservada e continua ao lado da mola nova. O `nudgeGlass` é ignorado no modo sem movimento. Os números do PR são de outra TCL (UnionTV) e **não** foram transferidos. |
| #11 | `fe687ff` | Perfil de dispositivo TCL L32S6500 (UnionTV, RTD284x, R41KT01 V391) com debloat de 4 pacotes e smoke documentado. | O **ID de entrada HDMI específico do aparelho** (`HW1413744128`) foi removido do texto e trocado por "HDMI 1", para não vazar um identificador local e não afirmar compatibilidade não testada. O perfil segue marcado como "reportado por um contribuidor, não reproduzido pelos mantenedores". |

Observação sobre as dependências: #11 usa os scripts tornados configuráveis por #2; #8 e #7
dependem do painel de opções de #6; #10 mexe na mesma região de código de `MainActivity` que a
correção de anel local. A ordem de integração foi #6 → #7 → #8 → #10 → #11, resolvendo os
conflitos a cada passo.

## Build de validação

- `integrate/all-prs` após os cinco merges e a integração da branch de auditoria
  (`audit/upstream-2026-10-20`).
- Versão `0.5.7`, `versionCode 29`, APK SHA-256
  `AAE067C60E8D1E0D90162BAA9DFB1DC87C2CFDDDC2ACB7CACB764D5A6FC7A712`
  (cópia local em `medicoes/apks/tvhome-0.5.7-code29.apk`).
- Instalado na BeyondTV2 em 20/10/2026 11:38; `base.apk` puxado da TV e conferido com o mesmo
  SHA-256. Home aberto a frio, anel de foco no tamanho correto do tile, serviço de acessibilidade
  reabilitado (`br.eddy.tvhome/com.gathin.tvhome.KeyRemapService`), sem erro no logcat.
- `assembleRelease` com lint fatal passou em todos os passos.
- `tests/test_home_guard.py`: 6 casos passando com uma `tools/tv.py` simulada.

## O que ainda não foi medido

- A bateria completa v0.5.6 vs v0.5.7 (5 repetições) continua em curso; só há dados de uma
  execução por condição até o momento.
- O A/B de #10 (spring novo) e de #8 (BlurHash) ainda não tem repetição suficiente. Os números do
  PR #10 são de outro aparelho e não são reaproveitados.
- O preset Visual na v0.5.7 continua sem medição própria; os custos exibidos na UI são os medidos
  na v0.5.6.
