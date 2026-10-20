# Forks recentes e PRs do CleanYourTV — triagem de 20/10/2026

> **Retrato datado, não sincronização automática.** O repositório de origem muda rapidamente. Esta varredura tem como objetivo descobrir ideias para avaliação; nenhuma implementação de terceiros foi copiada, nenhum pacote TCL foi desativado e nenhuma conclusão de desempenho foi atribuída à BeyondTV2 sem teste nela.

## Alcance e método

- Original: [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv), com **cerca de 1.228 forks** no momento da consulta; o total muda durante o dia.
- A listagem de forks por API não estava disponível nesta conexão; foi utilizada a busca paginada por repositórios GitHub criados em 20/10/2026, nomes `cleanyourtv` e `tv-vibehack`, além de buscas por projetos derivados independentes.
- Foram consultadas **3 páginas de até 100 resultados** para novos repositórios com `cleanyourtv` no nome e uma página com 30 para `tv-vibehack`. **Essas páginas não representam uma enumeração completa nem garantida dos 200+ forks novos**: a busca tem critérios de relevância, contém cópias sem mudanças e pode não conter repos renomeados.
- A triagem aprofundada leu histórico/arquivos ou diffs de uma seleção de forks com commits próprios: `heysachin`, `ajithvnr2001`, `chrx3`, `Ammar-create`, `zerone-id`, `Teknoist`, `beauahill`, `wuchengji2046`, `apukale`, `Kaikhalid`, `Samreay`, `Yskm666`. Tamanho do repositório sozinho não é prova de contribuição inédita.

## Forks com trabalho próprio verificado no histórico e/ou diff

| Projeto | Novidade confirmada | Potencial para Eddy TV | Encaminhamento |
|---|---|---|---|
| [heysachin/cleanyourtv](https://github.com/heysachin/cleanyourtv) | Código próprio de Continue Watching associado a Nuvio, `ContinueWatchingAdapter`, redirecionamento de botões e wallpaper | Médio para TVs com Nuvio; não é API universal de Netflix/YouTube | Investigar somente como módulo opcional; requer provider/acesso ao app e auditoria de permissões, memória e privacidade |
| [chrx3/cleanyourtv](https://github.com/chrx3/cleanyourtv) | Tema Acuarela, 12 imagens, rotação local e screensaver | Alto como **referência visual**, potencial custo de memória/GPU | Não portar diretamente; considerar seleção manual de temas estáticos, benchmark obrigatório |
| [Ammar-create/cleanyourtv](https://github.com/Ammar-create/cleanyourtv) | `GlassSlider` e diálogo de configurações com acabamento visual próprio | Médio como inspiração para opções do controle | Não trocar o nosso menu sem protótipo de foco/D-pad e comparação de custo |
| [wuchengji2046/CleanTCLTV-zh](https://github.com/wuchengji2046/CleanTCLTV-zh) | Adaptação TCL chinesa que roda como app comum com redirecionamento opcional, preservando Home do fabricante | Alto para conhecimento de firmwares restritivos, **não para nossa V660** | Documentar isolamento por firmware; não adicionar redirecionamento contínuo à BeyondTV2 |
| [apukale/raider-launcher](https://github.com/apukale/raider-launcher) | Interface de clima com mapa e cache de tiles OSM | Visualmente útil, mas custo de rede/memória/CPU | Tratar como inspiração experimental, desligado no modo Desempenho |
| [zerone-id/cleanyourtv](https://github.com/zerone-id/cleanyourtv) | Temas de dock compacto, wallpapers, clima/AQI, horários de oração | Recursos contextuais especializados | Não importar globalmente; identificar módulos opt-in se surgir demanda |
| [Teknoist/tv-vibehack](https://github.com/Teknoist/tv-vibehack) | Variantes com/sem Gemini e limpeza de arquivos | Alto risco de aumentar complexidade | Investigar em projeto separado; não misturar limpeza destrutiva ou serviço de IA ao launcher ultraleve |
| [Kaikhalid/cleanyourtv](https://github.com/Kaikhalid/cleanyourtv) | Perfil TCL BeyondTV2 Android **11**, screensaver flip-clock | Mesmo nome comercial, **firmware/Android diferente** | Referência de screensaver; nunca aplicar debloat à nossa Android 9 |
| [Samreay/cleanyourtv](https://github.com/Samreay/cleanyourtv) | Animação/tema horizon e correções da barra superior | Sobrepõe recursos já existentes e PRs upstream | Conferir somente bugs não cobertos |
| [ajithvnr2001/cleanyourtv](https://github.com/ajithvnr2001/cleanyourtv) | Implementação para SPPL Android TV, reorganização e suporte LAUNCHER | Evidência de diversidade de interfaces | Testar apenas o tratamento genérico de apps sem LEANBACK |
| [beauahill/tv-vibehack](https://github.com/beauahill/tv-vibehack) | Fork criado do `benfirad` sem commits originais na branch principal no momento da consulta | Nenhum ganho novo encontrado | Classificar como cópia, não como novidade |
| [Yskm666/tv-vibehack-xmen](https://github.com/Yskm666/tv-vibehack-xmen) | Xiaomi Android 6.0.1 | Fora de Android 9 TCL | Apenas referência de compatibilidade antiga |

## PRs novos relevantes no upstream

| PR | Situação na consulta | Decisão |
|---|---|---|
| [#446 — layout de grade em uma passagem, gradientes 128×72 e menos FBOs](https://github.com/Gathin23/cleanyourtv/pull/446) | Aberto | **Prioridade alta para auditoria de código e A/B na BeyondTV2**; números da Xiaomi não são transferíveis |
| [#150 — evitar reconstruções e overdraw](https://github.com/Gathin23/cleanyourtv/pull/150) | Aberto | Comparar com `AppLoader` e cache já existentes, sem copiar a branch empilhada |
| [#437 — benchmark](https://github.com/Gathin23/cleanyourtv/pull/437) | Aberto, **autor eddysangalli** | Nossa contribuição já enviada. Acompanhar CI/comentários do mantenedor e corrigir problemas sem duplicar PR |
| [#435 — FallbackHome não é segunda Home](https://github.com/Gathin23/cleanyourtv/pull/435) | **Mesclado**, autor eddysangalli e crédito roky146 | Nossa contribuição incorporada! Manter referências e testes |
| [#436 — ajuste do anel de foco no cold start](https://github.com/Gathin23/cleanyourtv/pull/436) | **Mesclado**, autor eddysangalli e crédito roky146 | Nossa contribuição incorporada! Manter referências e testes |
| [#434 — verificar desvios após update de firmware](https://github.com/Gathin23/cleanyourtv/pull/434) | Aberto | Considerar ferramenta de **auditoria read-only** antes de correção automática |
| [#451 — TCL 60C635](https://github.com/Gathin23/cleanyourtv/pull/451) | Aberto | Perfil Realtek Android 11, diferente de V660 |
| [#402 — acesso TCL/FFALCON a Settings](https://github.com/Gathin23/cleanyourtv/pull/402) | Aberto | Avaliar fallbacks específicos sem alterar o caminho funcional da nossa TV |
| [#390 — fundo/cinematic top shelf](https://github.com/Gathin23/cleanyourtv/pull/390) | Mesclado | Recurso visual opcional; pode inspirar perfil Visual, mas não é ganho de performance |
| [#422 — Cast e setupwraith](https://github.com/Gathin23/cleanyourtv/pull/422) | Mesclado | Testar descoberta e reprodução Cast na TV; processo em execução não confirma casting |
| [#384 — Compac TV Realtek RTD285O, Android 9](https://github.com/Gathin23/cleanyourtv/pull/384) | Mesclado | Referência de hardware próximo; nunca copiar lista de pacotes indiscriminadamente |

## Próximos experimentos isolados

1. **GPU/cache (#446)**: comparar CPU, jank, PSS, RenderTarget e entrada/retorno à Home usando builds idênticos exceto o patch.
2. **Temas**: fundo preto, cores sólidas, Aurora, BlurHash e um tema estático compacto, rotulados com custo real ou "ainda não medido".
3. **Compatibilidade**: detectar apps sem categoria LEANBACK sem expor componentes internos aleatórios (issue upstream #419).
4. **Segurança**: verificar scripts de restauração e impacto de firmware sobre Cast (issue #453). Somente consulta antes de qualquer mudança.

**Regras:** Desempenho como padrão, visual opcional e reversível, nada de merge integral do upstream, sem alterar `main` ou TV por esta auditoria. Os dados de benchmarking antigos nunca devem ser atribuídos à v0.5.8.
