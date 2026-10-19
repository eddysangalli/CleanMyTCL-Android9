# Screenshot recovery / Recuperação das capturas

The previous files in \`docs/assets/\` were saved as **UTF-16 text**, not binary PNG. They must be recaptured; changing links or renaming extensions cannot recover them.

**PowerShell 5.1 warning:** \`adb exec-out screencap -p > screenshot.png\` may corrupt the PNG because output redirection converts native binary output into UTF-16 text.

## Safe method (Windows 11)

1. Display the exact screen you need on the TCL and authorize ADB.
2. From the repository root:

~~~powershell
powershell -ExecutionPolicy Bypass -File scripts\tcl-capture-screenshot.ps1 -Name eddy-home -TvHost <TV_IP> -Overwrite
~~~

3. Repeat with the appropriate label only when that screen exists: \`stock-home\`, \`eddy-home\`, \`eddy-settings\`, \`eddy-hdmi\`, \`perf-dashboard\`.
4. Open every resulting PNG and verify the actual content, dimensions, and absence of personal data. Do not publish duplicated photos under different labels.
5. Commit the binary PNGs with Git (binary-safe), not a text-file editor or text upload tool. Re-enable the image embeds in both READMEs only after validating the PNGs.

The script runs \`adb shell screencap -p\` to a TV file and then \`adb pull\`. It checks the 8-byte PNG signature and prints SHA-256. It never changes TV settings or apps.

**Note:** HDMI and DRM-protected apps may appear blank/black in screenshots; do not misinterpret this as a broken launcher.

### Em português

Os PNGs anteriores foram gravados como texto UTF-16. O script acima recaptura a imagem corretamente, sem converter dados binários para texto. Confira o conteúdo e remova dados pessoais antes de publicar. Os gráficos de desempenho devem utilizar somente resultados documentados em \`benchmarks/results/\`.
