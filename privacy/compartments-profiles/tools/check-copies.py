#!/usr/bin/env python3
"""Fails when a copy of a shared source file inside a patch differs from the source of this package.

The persona reader code exists once in this package and as a copy in each tree that cannot call the others (the system
server, the Telephony app, the Wi-Fi code, the Bluetooth app): the copies travel inside the patches as new files. A copy is
equal to its source when it is the same text after the package line is normalised. Usage: check-copies.py [package-dir]"""
import pathlib, re, sys

SOURCES = {
    "PersonaFile.java": "system-server/src/com/android/server/compartments/PersonaFile.java",
    "CellSubset.java": "system-server/src/com/android/server/compartments/CellSubset.java",
    "PersonaReader.java": "persona-client/src/PersonaReader.java",
    "PersonaProcess.java": "persona-client/framework/PersonaProcess.java",
    "PersonaCells.java": "persona-client/telephony/PersonaCells.java",
}
PACKAGE = re.compile(r"^package [\w.]+;$", re.M)


def normalise(text):
    return PACKAGE.sub("package X;", text, count=1)


def added_files(patch_text):
    """{path: text} of the files that a patch (or several in one file) creates."""
    out = {}
    for block in re.split(r"(?m)^diff --git ", patch_text)[1:]:
        if "\nnew file mode" not in block.split("\n@@", 1)[0]:
            continue
        path = re.match(r"a/(\S+) b/", block).group(1)
        body = block.split("\n@@", 1)[1].split("\n", 1)[1]
        lines = [l[1:] for l in body.split("\n") if l.startswith("+")]
        out[path] = "\n".join(lines) + "\n"
    return out


def check(root):
    """The list of copies that differ from their source, and the number of copies compared."""
    bad, compared = [], 0
    for patch in sorted((root / "patches").rglob("*.patch")):
        for path, text in added_files(patch.read_text(encoding="utf-8")).items():
            name = path.rsplit("/", 1)[-1]
            if name not in SOURCES:
                continue
            compared += 1
            if normalise(text) != normalise((root / SOURCES[name]).read_text(encoding="utf-8")):
                bad.append(f"{patch.name}: {path}")
    return bad, compared


if __name__ == "__main__":
    root = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path(__file__).resolve().parent.parent
    bad, compared = check(root)
    for b in bad:
        print("DIFFERS FROM ITS SOURCE:", b)
    print(f"{compared} copies compared, {len(bad)} differ")
    sys.exit(1 if bad else 0)
