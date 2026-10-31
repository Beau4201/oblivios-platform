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
    "ICompartmentsPolicy.aidl": "../exit/app/src/com/android/internal/compartments/ICompartmentsPolicy.aidl",
    "CompartmentsAppPolicy.aidl": "../exit/app/src/com/android/internal/compartments/CompartmentsAppPolicy.aidl",
    "CompartmentsPlace.aidl": "../exit/app/src/com/android/internal/compartments/CompartmentsPlace.aidl",
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


def changed_files(patch_text):
    """{path: hunks} of the files that a patch modifies (not creates): the hunks are lists of (start, [lines with their marker])."""
    out = {}
    for block in re.split(r"(?m)^diff --git ", patch_text)[1:]:
        head = block.split("\n@@", 1)[0]
        if "\nnew file mode" in head or "\n@@" not in block:
            continue
        path = re.match(r"a/(\S+) b/", block).group(1)
        hunks = []
        for h in re.split(r"(?m)^@@ ", block)[1:]:
            header, _, body = h.partition("\n")
            body = body.split("\n-- \n")[0]  # the signature that git format-patch ends a patch with
            start = int(re.match(r"-(\d+)", header).group(1))
            lines = [l for l in body.split("\n") if l[:1] in (" ", "+", "-")]
            hunks.append((start, lines))
        out[path] = hunks
    return out


def apply_hunks(text, hunks):
    """The text after the hunks of a later patch, or None when a context or removed line is not where the hunk says."""
    old = text.split("\n")
    out, pos = [], 0
    for start, lines in hunks:
        begin = max(start - 1, 0)
        out += old[pos:begin]
        pos = begin
        for l in lines:
            if l[0] == "+":
                out.append(l[1:])
                continue
            if pos >= len(old) or old[pos] != l[1:]:
                return None
            if l[0] == " ":
                out.append(old[pos])
            pos += 1
    return "\n".join(out + old[pos:])


def check(root):
    """The list of copies that differ from their source, and the number of copies compared. A copy is what the patch that
    creates it holds after the later patches of the same repository folder that change it."""
    bad, compared = [], 0
    for folder in sorted(p for p in (root / "patches").iterdir() if p.is_dir()):
        copies = {}  # path -> [creating patch name, text]
        for patch in sorted(folder.glob("*.patch")):
            patch_text = patch.read_text(encoding="utf-8")
            for path, text in added_files(patch_text).items():
                if path.rsplit("/", 1)[-1] in SOURCES:
                    copies[path] = [patch.name, text]
            for path, hunks in changed_files(patch_text).items():
                if path in copies:
                    after = apply_hunks(copies[path][1], hunks)
                    if after is None:
                        bad.append(f"{patch.name}: {path} (does not apply to the copy)")
                        del copies[path]
                    else:
                        copies[path][1] = after
        for path, (name, text) in copies.items():
            compared += 1
            if normalise(text) != normalise((root / SOURCES[path.rsplit("/", 1)[-1]]).read_text(encoding="utf-8")):
                bad.append(f"{name}: {path}")
    return bad, compared


if __name__ == "__main__":
    root = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path(__file__).resolve().parent.parent
    bad, compared = check(root)
    for b in bad:
        print("DIFFERS FROM ITS SOURCE:", b)
    print(f"{compared} copies compared, {len(bad)} differ")
    sys.exit(1 if bad else 0)
