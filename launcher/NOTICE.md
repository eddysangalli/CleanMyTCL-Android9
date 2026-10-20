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

O redirecionamento do botão Globoplay e a espera de 800 ms para a sequência de abertura são uma adaptação local, testada nesta TCL.

Adaptações para a TCL BeyondTV2: pt-BR, dock e entradas da TCL, botão de voz, menus pelo controle, recentes, clima opcional (Open-Meteo) e presets de experiência (Desempenho/Equilibrado/Visual).

## Contribuições de @roky146 (revisadas e mescladas em `develop`, 20/10/2026)

- #2 "fix(smoke): make the remote, test app and mic check configurable per device" — scripts de smoke reutilizáveis sem mudar o padrão da BeyondTV2.
- #3 "fix(smoke): send the search key on the first Assistant attempt" — a tecla de busca é enviada já na primeira tentativa.
- #4 "feat(launcher): make the weather line localizable" — o clima usa o idioma do sistema, com fallback em inglês.
- #5 "feat(launcher): Spanish translation" — tradução espanhola; strings novas dos presets caem no inglês até uma próxima passada.
- #9 "fix(launcher): focus ring misplaced for the first seconds after the launcher starts" — correção inicial, complementada localmente em `f5d8cc6` para reaplicar o tamanho do anel depois do layout de cold start.

## Wallpaper `app/src/main/res/drawable-nodpi/wallpaper_iss.jpg`

[ISS-62 City lights of northeastern Europe](https://commons.wikimedia.org/wiki/File:ISS-62_City_lights_of_northeastern_Europe.jpg),
NASA, public domain. Cropped to 1920×1080 with a dark gradient added behind the header text.
