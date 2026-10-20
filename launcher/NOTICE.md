# Créditos

Este launcher é um fork do [tv-vibehack](https://github.com/Gathin23/tv-vibehack) (licença MIT, veja `LICENSE-tv-vibehack`).

Inclui mudanças de pull requests do upstream (ainda abertos em 19/10/2026), aplicadas e medidas nesta TCL:
- #39 "Launcher: faster Home, cache app tiles off the main thread" — @leovvay
- #55 "launcher: keep D-pad focus inside a row at its ends" — @yashug
- #40 (ideias de build release/R8) — @leovvay

Adaptações para a TCL BeyondTV2: pt-BR, dock e entradas da TCL, botão de voz, menus pelo controle, recentes, clima opcional (Open-Meteo) e modo ultraleve.

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
