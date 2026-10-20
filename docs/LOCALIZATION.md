# Translating the launcher

Every text the launcher shows lives in `launcher/app/src/main/res/values*/strings.xml`. The code
never types user-visible text itself: the release build fails (lint `HardcodedText`,
`SetTextI18n`) if a layout or `setText()` does.

| Folder | Language | Status |
|---|---|---|
| `values/` | English (default, used when the TV's language has no folder) | complete |
| `values-pt-rBR/` | Portuguese (Brazil) | complete |
| `values-de/` | German | partial — missing strings fall back to English |

## Adding a language

1. Copy `values/strings.xml` to `values-<code>/strings.xml`, where `<code>` is the
   [Android language qualifier](https://developer.android.com/guide/topics/resources/providing-resources#LocaleQualifier):
   `es` (all Spanish), `es-rMX` (Mexico only), `fr`, `it`…
2. Translate the text between the tags. Do **not** change the `name="…"` attributes, the
   `%1$s` / `%1$d` placeholders, or the `\'` escapes.
3. Remove any entry marked `translatable="false"` (package names, not text).
4. Keep it short: the date and the pills share one line on a 1080p screen. `date_format` is a
   [`SimpleDateFormat`](https://developer.android.com/reference/java/text/SimpleDateFormat) pattern;
   use a short one if the long one pushes the Inputs pill (see the pt-BR comment).
5. The weather conditions (`wx_*`) are upper case on purpose. City names come from Open-Meteo in
   the TV's language automatically.
6. Build, install, set the TV to that language (Settings → Device Preferences → Language) and check
   the Home, the options menu, a tile menu, the inputs menu and the clock screensaver. Include a
   screenshot in the PR (`scripts/tcl-screencap.sh`).

A partial file is fine: Android shows English for any string the file does not have.
