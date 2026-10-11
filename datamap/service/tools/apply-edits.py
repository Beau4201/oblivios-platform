#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Applies a list of exact edits to a worktree: Replace OLD with NEW, where OLD must occur exactly once.

usage: apply-edits.py WORKTREE EDITS.py
EDITS.py defines EDITS = [(repo, path, old, new), ...]. Nothing is written unless every edit is valid.
"""
import pathlib, runpy, sys

wt = pathlib.Path(sys.argv[1])
edits = runpy.run_path(sys.argv[2])["EDITS"]
texts = {}
for repo, path, old, new in edits:
    f = wt / repo / path
    if f not in texts:
        texts[f] = f.read_text(encoding="utf-8")
    n = texts[f].count(old)
    if n != 1:
        sys.exit(f"REFUSED: {repo}/{path}: the old text occurs {n} times, expected exactly 1:\n{old[:200]}")
    texts[f] = texts[f].replace(old, new)
for f, t in texts.items():
    f.write_text(t, encoding="utf-8")
print(f"{len(edits)} edits applied to {len(texts)} files")
