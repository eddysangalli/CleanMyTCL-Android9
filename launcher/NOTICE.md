# Créditos

Este launcher é um fork do [tv-vibehack](https://github.com/Gathin23/tv-vibehack) (licença MIT, veja `LICENSE-tv-vibehack`).

Inclui mudanças de pull requests do upstream (ainda abertos em 19/10/2026), aplicadas e medidas nesta TCL:
- #39 "Launcher: faster Home, cache app tiles off the main thread" — @leovvay
- #55 "launcher: keep D-pad focus inside a row at its ends" — @yashug
- #40 (ideias de build release/R8) — @leovvay

Portes pontuais do upstream CleanYourTV (MIT), auditados em 20/10/2026 a partir de `main` 09fe4e4:
- #289 "focusing the last visible row no longer nudges the grid up" — @Samreay
- #291 "Inputs menu shows the names from Settings > Inputs and leaves out hidden inputs" — @mwamodo
- #292 "the Settings keycode works on Home without HomeKeyService" — @Samreay (hook parcial; o código 83 de fornecedor fica opt-in em `config.xml`)
- #298 "one tile per app and label when an app declares two leanback entries" — @anandhakrishnanaji

Segunda leva de portes, auditados em 20/10/2026 a partir de `main` fd1ee0a (v2.4.0):
- #225 "Launcher: build each font weight once, tint the glow instead of repainting it" — @leovvay (merged upstream em 20/10/2026). Aqui: cada peso do Outfit virou um recurso `font/outfit_*.xml`, construído uma vez por processo, no lugar de `fontVariationSettings` em cada `TextView`.
- #370 "Launcher: use cached font weights across all layouts, avoid per-frame dock lookup" — @leovvay (merged upstream em 20/10/2026). Aqui: a amostragem de cor do ícone deixou de usar `Color.colorToHSV` por pixel.
- #350 "disable: skip the launcher that owns Home while nothing else can take it" — @ppjsw (merged upstream em 20/10/2026). Aqui: vale para `tools/disable.sh` e `tools/disable.ps1`, e o `FallbackHome` do fabricante não conta como um segundo launcher (verificado nesta TCL).

O redirecionamento do botão Globoplay e a espera de 800 ms para a sequência de abertura são uma adaptação local, testada nesta TCL.

Adaptações para a TCL BeyondTV2: pt-BR, dock e entradas da TCL, botão de voz, menus pelo controle, recentes, clima opcional (Open-Meteo) e presets de experiência (Desempenho/Equilibrado/Visual).

## Contribuições de @roky146 (revisadas e mescladas em `develop`, 20/10/2026)

- #2 "fix(smoke): make the remote, test app and mic check configurable per device" — scripts de smoke reutilizáveis sem mudar o padrão da BeyondTV2.
- #3 "fix(smoke): send the search key on the first Assistant attempt" — a tecla de busca é enviada já na primeira tentativa.
- #4 "feat(launcher): make the weather line localizable" — o clima usa o idioma do sistema, com fallback em inglês.
- #5 "feat(launcher): Spanish translation" — tradução espanhola; strings novas dos presets caem no inglês até uma próxima passada.
- #9 "fix(launcher): focus ring misplaced for the first seconds after the launcher starts" — correção inicial, complementada localmente em `f5d8cc6` para reaplicar o tamanho do anel depois do layout de cold start.

## Contribuições de @roky146 (integradas localmente em `integrate/all-prs`, 20/10/2026)

Integração local com um commit de merge por PR, para não duplicar commits. Os conflitos foram
resolvidos preservando os presets de experiência, o aviso de custo do fundo e a correção de anel
no cold start. Os PRs continuam abertos no GitHub: a decisão de mesclá-los no `develop` é do
mantenedor.

- #6 "feat(launcher): keep the Home options open while changing values" — menu de opções como painel à direita com steppers; a linha de Experiência e o aviso de custo do fundo foram mantidos.
- #7 "feat(launcher): more Home backgrounds - solid colours and Earth at night" — cores sólidas escuras e Earth at night (NASA, domínio público); o Aurora segue sendo o único fundo com custo medido.
- #8 "feat(launcher): blurred backgrounds from BlurHash strings" — três blurs de fundo, decodificador BlurHash (MIT) e `scripts/tcl-blurhash.ps1`; só o hash vai no APK.
- #10 "perf(launcher): Material 3 Expressive spring for the focus highlight" — spring com stiffness 380 e damping 0,8, mola vertical em coordenadas de conteúdo e "bounce" nas bordas da grade; os números do PR são de outra TCL e não foram transferidos.
- #11 "docs(devices): TCL L32S6500 (UnionTV, RTD284x, R41KT01 V391) profile" — perfil de aparelho; o identificador de entrada HDMI específico foi removido e o perfil segue marcado como reportado por contribuidor.

## Wallpaper `app/src/main/res/drawable-nodpi/wallpaper_iss.jpg`

[ISS-62 City lights of northeastern Europe](https://commons.wikimedia.org/wiki/File:ISS-62_City_lights_of_northeastern_Europe.jpg),
NASA, public domain. Cropped to 1920×1080 with a dark gradient added behind the header text.

## Blurred backgrounds (`BG_HASHES` in `MainActivity.java`)

4×3 [BlurHash](https://github.com/woltapp/blurhash) strings (MIT; `BlurHash.java` ports its decoder) taken
from these photos after the same crop and gradient. Only the hash is bundled, not the photo.

| Background | Photo | Author | License |
|---|---|---|---|
| Nebula blur | ["Cosmic Cliffs" in the Carina Nebula](https://commons.wikimedia.org/wiki/File:%E2%80%9CCosmic_Cliffs%E2%80%9D_in_the_Carina_Nebula_(NIRCam_and_MIRI_Composite_Image)_(weic2205b).jpg) | NASA, ESA, CSA, and STScI | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| Milky Way blur | [Starry Night at La Silla](https://commons.wikimedia.org/wiki/File:Starry_Night_at_La_Silla.jpg) | ESO/H. Dahle | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| Star trails blur | [Night sky at Paranal](https://commons.wikimedia.org/wiki/File:Night_sky_at_Paranal_(img_2524-cc).jpg) | D. Gadotti/ESO | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
