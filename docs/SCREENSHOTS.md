# Screenshots / Capturas de tela

## The problem this page exists to solve

The images in `docs/assets/` were originally saved as **UTF-16 text**, not binary PNG. Changing
the links or renaming the extension cannot recover them: the file is not a PNG any more.

**PowerShell 5.1 warning:** `adb exec-out screencap -p > screenshot.png` may corrupt the PNG,
because output redirection turns native binary output into text. The PNG signature byte `0x89`
is lost — it becomes `U+FFFD` — so the file is corrupt even though it "looks" like an image in a
text editor. PowerShell 7's `>` is safe; 5.1's is not.

## Safe method (Windows 11)

```powershell
# from the repository root
powershell -ExecutionPolicy Bypass -File scripts\tcl-capture-screenshot.ps1 -Name eddy-home -TvHost <TV_IP> -Overwrite
```

The script:

- wakes the screen first (`KEYCODE_WAKEUP`) — right after standby, `screencap` returns **nothing
  at all**, which is why a capture can come back empty;
- captures with `adb exec-out screencap -p` and writes the raw bytes with .NET's
  `StandardOutput.BaseStream`, so no text conversion happens;
- checks the 8-byte PNG signature (`89 50 4e 47 0d 0a 1a 0a`) and deletes the file if it does not
  match;
- prints the SHA-256 so you can tell two captures apart.

It never changes TV settings or apps.

> **`adb shell screencap -p /path/on/tv` followed by `adb pull` does not work on this TV.**
> It writes a 0-byte file on the TCL BeyondTV2 / R851T02 V660 firmware. `exec-out` over the pipe
> is the method that works there.

## Labels

| Label | What it shows |
|---|---|
| `stock-home` | the original Google TV Home, with its recommendation rows |
| `eddy-home` | the Eddy TV home: greeting, weather line, top bar, dock |
| `eddy-launcher-apps` | the app grid, scrolled |
| `eddy-tile-menu` | the menu of a focused tile (pin to dock, app info) |
| `eddy-options` | the launcher's own options panel, opened from the TV |
| `eddy-hdmi` | the HDMI path (may come out black on some inputs) |
| `perf-dashboard` | a performance snapshot (may come out black on some DRM surfaces) |

## Before you commit an image

1. **Open it** and check the content is what the label says.
2. **Check the two captures are not the same picture** under different names. Compare the
   SHA-256 the script prints, or the file size.
3. **Look for personal data** in the picture: your name, your city, your account, a photo.
   The `eddy-options` capture shows the configured name and the weather location — either blur it
   or use a capture where those are not visible.
4. Commit it as a **binary** file with Git, not through a text editor or a text-only upload.

### Em português

Os PNGs anteriores foram gravados como texto UTF-16 e estavam corrompidos. O script acima
recaptura a imagem corretamente, sem converter dados binários para texto, e confere a assinatura
PNG antes de gravar. Confira o conteúdo de cada imagem, evite publicar a mesma foto com nomes
diferentes e revise se há dados pessoais (o painel de opções mostra o nome configurado e a
cidade do clima). Os gráficos de desempenho devem usar somente resultados documentados em
`benchmarks/results/`.
