#!/usr/bin/env python3
"""Draw the static PNG charts used in the README from data that was actually measured.

Usage:
    python scripts/tcl-make-charts.py <data.json> <output-dir>

The JSON file is the single source of the numbers. It must look like this:

{
  "source": "TCL BeyondTV2 - benchmarks/results/2026-10-19-v0.5.2.md",
  "charts": [
    {
      "id": "bench-idle-cpu",
      "title": "Idle CPU: stock Home vs Eddy TV",
      "subtitle": "60 s at the Home screen; % of the 4-core capacity",
      "y_label": "% of 4-core capacity",
      "x_labels": ["Stock Home", "Eddy TV v0.5.2"],
      "series": [{"name": "Idle CPU", "values": [57.3, 14.5], "colors": ["#c0392b", "#27ae60"]}],
      "note": "Same script (tcl-bench.sh). Stock row is a historical reference, not re-run today."
    }
  ]
}

Only values present in the JSON are drawn. The script never fills in a number by itself, so a
chart cannot silently mix versions or invent data. Requires Pillow (pip install pillow).
"""

from __future__ import annotations

import json
import os
import sys
from typing import Any

from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 1280, 720
MARGIN_L, MARGIN_R, MARGIN_T, MARGIN_B = 130, 50, 120, 140
PALETTE = ["#2c7fb8", "#e67e22", "#27ae60", "#8e44ad", "#c0392b", "#16a085", "#7f8c8d", "#d35400"]
GRID = "#d9dee3"
INK = "#1b2733"
MUTED = "#5a6b7b"
FONT_CANDIDATES = [
    r"C:\Windows\Fonts\arial.ttf",
    r"C:\Windows\Fonts\segoeui.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/System/Library/Fonts/Supplemental/Arial.ttf",
]


def font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def nice_max(value: float) -> float:
    if value <= 0:
        return 1.0
    import math

    exp = math.floor(math.log10(value))
    base = 10 ** exp
    for step in (1, 1.5, 2, 2.5, 3, 4, 5, 7.5, 10):
        if value <= step * base * 1.02:
            return step * base
    return 10 * base


def fmt(value: float, unit: str = "") -> str:
    if abs(value - round(value)) < 0.05:
        text = f"{int(round(value)):,}".replace(",", " ")
    else:
        text = f"{value:,.1f}".replace(",", " ")
    return f"{text}{unit}"


def draw_bar_chart(chart: dict[str, Any], source: str, path: str) -> None:
    x_labels: list[str] = chart["x_labels"]
    series: list[dict[str, Any]] = chart["series"]
    values = [v for s in series for v in s["values"]]
    top = nice_max(max(values) if values else 1)
    y_label = chart.get("y_label", "")
    unit = chart.get("value_suffix", "")

    img = Image.new("RGB", (WIDTH, HEIGHT), "white")
    d = ImageDraw.Draw(img)
    f_title = font(30)
    f_sub = font(17)
    f_axis = font(16)
    f_tick = font(15)
    f_val = font(15)
    f_note = font(13)
    f_legend = font(15)

    title = chart["title"]
    subtitle = chart.get("subtitle", "")
    tw = d.textlength(title, font=f_title)
    d.text(((WIDTH - tw) / 2, 28), title, fill=INK, font=f_title)
    if subtitle:
        sw = d.textlength(subtitle, font=f_sub)
        d.text(((WIDTH - sw) / 2, 72), subtitle, fill=MUTED, font=f_sub)

    # legend
    legend_y = 100
    lx = MARGIN_L
    if len(series) > 1:
        for i, s in enumerate(series):
            color = s.get("colors", [None])[0] if s.get("colors") else PALETTE[i % len(PALETTE)]
            d.rounded_rectangle([lx, legend_y + 4, lx + 22, legend_y + 18], radius=3, fill=color)
            d.text((lx + 30, legend_y + 2), s["name"], fill=INK, font=f_legend)
            lx += 30 + d.textlength(s["name"], font=f_legend) + 34

    plot_l, plot_r = MARGIN_L, WIDTH - MARGIN_R
    plot_t, plot_b = MARGIN_T + 40, HEIGHT - MARGIN_B
    for i in range(6):
        y = plot_b - (plot_b - plot_t) * i / 5
        d.line([plot_l, y, plot_r, y], fill=GRID, width=1)
        d.text((plot_l - 12, y), fmt(top * i / 5, unit), fill=MUTED, font=f_tick, anchor="rm")
    d.line([plot_l, plot_b, plot_l, plot_t], fill=INK, width=2)
    d.line([plot_l, plot_b, plot_r, plot_b], fill=INK, width=2)
    if y_label:
        d.text((18, (plot_t + plot_b) / 2), y_label, fill=MUTED, font=f_axis, anchor="lm")

    group_w = (plot_r - plot_l) / max(len(x_labels), 1)
    bar_gap = 6
    bar_w = min(90, (group_w - 30) / max(len(series), 1) - bar_gap)
    for gi, label in enumerate(x_labels):
        gx = plot_l + gi * group_w + group_w / 2
        total_w = len(series) * bar_w + (len(series) - 1) * bar_gap
        start = gx - total_w / 2
        for si, s in enumerate(series):
            val = s["values"][gi]
            color = (s.get("colors") or [None])[gi] if s.get("colors") else PALETTE[si % len(PALETTE)]
            x0 = start + si * (bar_w + bar_gap)
            y0 = plot_b - (val / top) * (plot_b - plot_t)
            d.rounded_rectangle([x0, y0, x0 + bar_w, plot_b], radius=4, fill=color)
            d.text((x0 + bar_w / 2, y0 - 8), fmt(val, unit), fill=INK, font=f_val, anchor="mb")
            errors = s.get("errors")
            if errors:
                err = errors[gi]
                y1 = plot_b - (min(val + err, top) / top) * (plot_b - plot_t)
                cx = x0 + bar_w / 2
                d.line([cx, y0, cx, y1], fill=INK, width=2)
                d.line([cx - 7, y1, cx + 7, y1], fill=INK, width=2)
        lw = d.textlength(label, font=f_tick)
        d.text((gx, plot_b + 12), label, fill=INK, font=f_tick)
        if lw > group_w - 6:
            # keep long labels inside their column; the font is small enough for the current charts
            d.text((gx, plot_b + 30), "", fill=INK, font=f_tick)

    note = chart.get("note", "")
    if note:
        d.text((MARGIN_L, HEIGHT - 58), note, fill=MUTED, font=f_note)
    d.text((MARGIN_L, HEIGHT - 34), f"Source: {source}", fill=MUTED, font=f_note)
    img.save(path, "PNG", optimize=True)


def draw_hbar_chart(chart: dict[str, Any], source: str, path: str) -> None:
    labels: list[str] = chart["labels"]
    values: list[float] = chart["values"]
    colors = chart.get("colors", PALETTE)
    unit = chart.get("value_suffix", "")
    top = nice_max(max(values) if values else 1)

    img = Image.new("RGB", (WIDTH, HEIGHT), "white")
    d = ImageDraw.Draw(img)
    f_title = font(30)
    f_sub = font(17)
    f_axis = font(16)
    f_tick = font(15)
    f_val = font(15)
    f_note = font(13)

    title = chart["title"]
    tw = d.textlength(title, font=f_title)
    d.text(((WIDTH - tw) / 2, 28), title, fill=INK, font=f_title)
    subtitle = chart.get("subtitle", "")
    if subtitle:
        sw = d.textlength(subtitle, font=f_sub)
        d.text(((WIDTH - sw) / 2, 72), subtitle, fill=MUTED, font=f_sub)

    plot_l, plot_r = 280, WIDTH - 120
    plot_t, plot_b = 140, HEIGHT - 110
    rows = len(labels)
    row_h = (plot_b - plot_t) / max(rows, 1)
    bar_h = min(44, row_h * 0.6)
    for i in range(6):
        x = plot_l + (plot_r - plot_l) * i / 5
        d.line([x, plot_t, x, plot_b], fill=GRID, width=1)
        d.text((x, plot_b + 8), fmt(top * i / 5, unit), fill=MUTED, font=f_tick, anchor="ma")
    d.text(((plot_l + plot_r) / 2, HEIGHT - 78), chart.get("x_label", ""), fill=MUTED, font=f_axis, anchor="ma")

    for i, label in enumerate(labels):
        y = plot_t + i * row_h + row_h / 2
        d.text((plot_l - 14, y), label, fill=INK, font=f_tick, anchor="rm")
        val = values[i]
        x1 = plot_l + (val / top) * (plot_r - plot_l)
        color = colors[i % len(colors)]
        d.rounded_rectangle([plot_l, y - bar_h / 2, x1, y + bar_h / 2], radius=4, fill=color)
        d.text((x1 + 10, y), fmt(val, unit), fill=INK, font=f_val, anchor="lm")

    note = chart.get("note", "")
    if note:
        d.text((60, HEIGHT - 56), note, fill=MUTED, font=f_note)
    d.text((60, HEIGHT - 32), f"Source: {source}", fill=MUTED, font=f_note)
    img.save(path, "PNG", optimize=True)


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    data_path, out_dir = sys.argv[1], sys.argv[2]
    with open(data_path, encoding="utf-8-sig") as fh:
        data = json.load(fh)
    os.makedirs(out_dir, exist_ok=True)
    source = data.get("source", "")
    made = []
    for chart in data["charts"]:
        path = os.path.join(out_dir, chart["id"] + ".png")
        if chart.get("type") == "hbar":
            draw_hbar_chart(chart, source, path)
        else:
            draw_bar_chart(chart, source, path)
        with open(path, "rb") as fh:
            sig = fh.read(8)
        assert sig == b"\x89PNG\r\n\x1a\n", f"not a PNG: {path}"
        made.append(path)
    print("charts written:")
    for path in made:
        print("  " + path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
