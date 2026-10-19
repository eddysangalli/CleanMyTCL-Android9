#!/usr/bin/env python3
"""Check the relative links of the markdown files against the repository.

Every [](path) link to a local file must exist in the working tree; every
http(s) link to our own repository must exist in the published branch. Anything
that does not resolve is reported.
"""
import os
import re
import sys

LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
SKIP_PREFIX = ("http://", "https://", "mailto:", "#", "<")

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
files = []
for base, dirs, names in os.walk(root):
    dirs[:] = [d for d in dirs if d not in (".git", "build", ".gradle", "medicoes",
                                            "private", "etapa2", "node_modules")]
    for n in names:
        if n.endswith(".md"):
            files.append(os.path.join(base, n))

bad = []
checked = 0
# The verbatim copy of the upstream README links to files that live in the upstream
# repository at that commit. It is kept unmodified for provenance, so its relative
# links are expected not to resolve here.
ARCHIVE = {"docs/upstream/README-tv-vibehack-9ef40c1.md"}
for path in files:
    rel = os.path.relpath(path, root).replace("\\", "/")
    if rel in ARCHIVE:
        continue
    with open(path, encoding="utf-8") as fh:
        text = fh.read()
    for target in LINK.findall(text):
        t = target.strip()
        if t.startswith(SKIP_PREFIX):
            continue
        t = t.split("#")[0]
        if not t:
            continue
        if t.startswith("/"):
            candidate = os.path.join(root, t.lstrip("/"))
        else:
            candidate = os.path.join(os.path.dirname(path), t)
        candidate = os.path.normpath(candidate)
        checked += 1
        if not os.path.exists(candidate):
            bad.append("%s -> %s" % (rel, t))

print("checked %d local links in %d markdown files" % (checked, len(files)))
if bad:
    print("BROKEN:")
    for b in sorted(set(bad)):
        print("  " + b)
    sys.exit(1)
print("all local links resolve")
