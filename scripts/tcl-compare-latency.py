#!/usr/bin/env python3
"""Compare two key-latency runs of scripts/tcl-key-latency.sh.

The runs are the folders the script writes under medicoes/launcher/. It prints the metrics side
by side and the difference, so a report can quote a comparison instead of two tables.

Usage:
    python scripts/tcl-compare-latency.py <run-a> <run-b> [label-a] [label-b]
"""
import re
import sys
from pathlib import Path


def read_run(folder):
    """The metrics of one run, parsed from the resumo.txt the script wrote."""
    text = (Path(folder) / "resumo.txt").read_text(encoding="utf-8")
    values = {}
    match = re.search(r"^Launcher\s+(\S+)\s+\(([^)]*)\)\s*-\s*(\S+)", text, re.M)
    if match:
        values["component"], values["version"], values["label"] = match.groups()

    def grab(pattern, name, group=1):
        found = re.search(pattern, text, re.M)
        if found:
            values[name] = found.group(group).strip()

    grab(r"Series:\s*(\d+\s*x\s*\d+\s*teclas)", "series")
    grab(r"resposta \(tecla -> 1o quadro\):\s*p50\s+(\d+)", "resp_p50")
    grab(r"resposta \(tecla -> 1o quadro\):.*?p95\s+(\d+)", "resp_p95")
    grab(r"resposta \(tecla -> 1o quadro\):.*?p99\s+(\d+)", "resp_p99")
    grab(r"resposta \(tecla -> 1o quadro\):.*?max\s+(\d+)\s*ms", "resp_max")
    grab(r"resposta \(tecla -> 1o quadro\):.*?\((\d+)\s*teclas\)", "resp_n")
    grab(r"movimento completo:\s*p50\s+(\d+)", "mov_p50")
    grab(r"movimento completo:\s*p95\s+(\d+)", "mov_p95")
    grab(r"movimento completo:\s*p99\s+(\d+)", "mov_p99")
    grab(r"movimento completo:\s*max\s+(\d+)\s*ms", "mov_max")
    grab(r"quadros atrasados:\s*([\d.]+%)\s*de\s*(\d+)", "jank_pct")
    grab(r"quadros atrasados:\s*[\d.]+%\s*de\s*(\d+)", "frames")
    grab(r"Perdidas:\s*(\d+)\s*sem reac[ãa]o visual", "lost")
    return values


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    a_folder, b_folder = sys.argv[1], sys.argv[2]
    a = read_run(a_folder)
    b = read_run(b_folder)
    a_label = sys.argv[3] if len(sys.argv) > 3 else a.get("label", Path(a_folder).name)
    b_label = sys.argv[4] if len(sys.argv) > 4 else b.get("label", Path(b_folder).name)

    rows = [
        ("Resposta p50", "resp_p50"), ("Resposta p95", "resp_p95"),
        ("Resposta p99", "resp_p99"), ("Resposta max", "resp_max"),
        ("Movimento p50", "mov_p50"), ("Movimento p95", "mov_p95"),
        ("Movimento p99", "mov_p99"), ("Movimento max", "mov_max"),
    ]
    header = f"{'Metrica':<14} {a_label:<20} {b_label:<20} diferenca"
    print(header)
    print("-" * len(header))
    for name, key in rows:
        va, vb = a.get(key), b.get(key)
        if va is None or vb is None:
            continue
        va, vb = int(va), int(vb)
        diff = vb - va
        mark = "" if va == 0 else f" ({100.0 * diff / va:+.0f}%)"
        print(f"{name:<14} {str(va) + ' ms':<20} {str(vb) + ' ms':<20} {diff:+d} ms{mark}")

    for name, key in (("Jank", "jank_pct"), ("Quadros", "frames"),
                      ("Teclas medidas", "resp_n"), ("Perdidas", "lost")):
        va, vb = a.get(key), b.get(key)
        if va is not None and vb is not None:
            print(f"{name:<14} {str(va):<20} {str(vb):<20}")

    print()
    for label, values in ((a_label, a), (b_label, b)):
        print(f"{label}: {values.get('component', '?')} ({values.get('version', '?')}), "
              f"{values.get('series', '?')}")


if __name__ == "__main__":
    main()
