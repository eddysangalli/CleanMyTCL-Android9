# Revisão dos PRs de @roky146 — 20/10/2026

Escopo: PRs abertos contra `develop`, revisados e testados localmente. Nenhum release ou tag foi
publicado; `main` permanece em v0.5.6 (`94e4890`).

## Mesclados em `develop`

| PR | Título | Merge commit | Validação |
|---|---|---|---|
| #2 | `fix(smoke): make the remote, test app and mic check configurable per device` | `8563348` | Revisão do diff; scripts com valores padrão da BeyondTV2 preservados. |
| #3 | `fix(smoke): send the search key on the first Assistant attempt` | `fe7e146` | Revisão do diff; mudança pequena no fluxo de busca. |
| #4 | `feat(launcher): make the weather line localizable` | `a2e9642` | `assembleRelease` passou; clima em pt-BR continua funcional na TV. |
| #9 | `fix(launcher): focus ring misplaced for the first seconds after the launcher starts` | `21a9826` | `assembleRelease` passou; corrigido em seguida no commit local `f5d8cc6`, porque o anel ainda podia ficar no tamanho de projeto após um cold start. |
| #5 | `feat(launcher): Spanish translation` | `82ed833` | `assembleRelease` passou. As strings novas dos presets ainda não têm tradução e caem no inglês. |

Os agradecimentos foram registrados nas revisões dos PRs mesclados e em comentários nos PRs retidos.
Créditos em [`launcher/NOTICE.md`](../launcher/NOTICE.md).

## Retidos (sem aprovação ou merge)

| PR | Motivo |
|---|---|
| #6 | O menu com steppers foi escrito antes dos presets Performance/Balanced/Visual e do aviso de custo do wallpaper; mesclar como está removeria a linha Experiência e o caminho de custo. Precisa de rebase/integração. |
| #7 | Fundos adicionais são bem-vindos como opção visual, mas precisam entrar como escolha reversível, integrada aos presets, com custo medido ou declarado como não medido. |
| #8 | O BlurHash não tem prova de que reduza o jank do wallpaper Aurora nesta TV; falta o A/B de 5 repetições. |
| #10 | Os números vieram de outra TCL UnionTV e de um protocolo diferente; o spring atual fica até o A/B nesta TV. Pode virar opção de Balanced/Visual depois. |
| #11 | Perfil/documentação depende do #2 e das medições do #10; também contém um ID de entrada HDMI específico do aparelho que deve ser removido antes de publicar. |

## Build de validação

- `develop` em `f5d8cc6` (depois dos merges e do ajuste local do anel).
- Versão `0.5.7`, `versionCode 28`, APK SHA-256
  `ACDF90EC631FB0FCBAECD012C371C8ABCBF9B5E2117EC00006C34943A30FAA5B`.
- Instalado na BeyondTV2; Home aberto a frio, anel de foco alinhado em 2 s e 5 s, clima em pt-BR
  visível, serviço de teclas preservado.
- `assembleRelease` com lint fatal passou após #4, #9 e #5.

## O que ainda não foi medido

- A bateria completa v0.5.6 vs v0.5.7 (5 repetições) continua pendente de uma TV ociosa e do
  protocolo controlado.
- O A/B de #10/#8 continua pendente; os números do PR #10 não são transferíveis.
- O preset Visual na v0.5.7 continua sem medição própria; os custos exibidos na UI são os medidos
  na v0.5.6.
