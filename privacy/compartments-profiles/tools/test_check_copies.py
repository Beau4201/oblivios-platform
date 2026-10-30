#!/usr/bin/env python3
"""Test of check-copies.py: a patch with equal copies passes, a changed copy and a copy that is missing a line fail."""
import importlib.util, pathlib, tempfile

spec = importlib.util.spec_from_file_location("cc", pathlib.Path(__file__).with_name("check-copies.py"))
cc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cc)

SRC = "// SPDX\npackage com.android.server.compartments;\n\nfinal class PersonaFile {\n    int x = 1;\n}\n"


def patch_for(path, text):
    body = "".join("+" + l + "\n" for l in text.rstrip("\n").split("\n"))
    n = len(text.rstrip("\n").split("\n"))
    return f"From abc Mon Sep 17 00:00:00 2001\n\n---\ndiff --git a/{path} b/{path}\nnew file mode 100644\nindex 0000000..1111111\n--- /dev/null\n+++ b/{path}\n@@ -0,0 +1,{n} @@\n{body}-- \n2.0\n"


def run(copy_text, extra=""):
    with tempfile.TemporaryDirectory() as d:
        root = pathlib.Path(d)
        for rel in cc.SOURCES.values():
            (root / rel).parent.mkdir(parents=True, exist_ok=True)
            (root / rel).write_text(SRC)
        (root / "patches" / "x").mkdir(parents=True)
        (root / "patches" / "x" / "0001-a.patch").write_text(patch_for("src/p/PersonaFile.java", copy_text) + extra)
        return cc.check(root)


good = SRC.replace("com.android.server.compartments", "com.android.phone")
assert run(good) == ([], 1), "a copy with another package line is equal"
assert run(SRC) == ([], 1), "a copy with the same package line is equal"
bad, n = run(good.replace("x = 1", "x = 2"))
assert n == 1 and len(bad) == 1, "a changed copy must fail"
bad, n = run(good.replace("    int x = 1;\n", ""))
assert len(bad) == 1, "a copy with a missing line must fail"
bad, n = run(good + "// more\n")
assert len(bad) == 1, "a copy with an extra line must fail"
# a modified file (no 'new file mode') is not a copy
assert run(good, extra="diff --git a/Other.java b/Other.java\nindex 1..2 100644\n--- a/Other.java\n+++ b/Other.java\n@@ -1 +1 @@\n-a\n+b\n") == ([], 1)
# a later patch of the same folder that changes the copy: the copy is what the creating patch holds after it
def fix(old, new, path="src/p/PersonaFile.java"):
    return (f"From def Mon Sep 17 00:00:00 2001\n\n---\ndiff --git a/{path} b/{path}\nindex 1111111..2222222 100644\n"
            f"--- a/{path}\n+++ b/{path}\n@@ -5 +5 @@\n-{old}\n+{new}\n-- \n2.43.0\n\n")


def run_later(source, later, folder="x"):
    with tempfile.TemporaryDirectory() as d:
        root = pathlib.Path(d)
        for rel in cc.SOURCES.values():
            (root / rel).parent.mkdir(parents=True, exist_ok=True)
            (root / rel).write_text(source)
        (root / "patches" / "x").mkdir(parents=True)
        (root / "patches" / "x" / "0001-a.patch").write_text(patch_for("src/p/PersonaFile.java", good))
        if folder != "x":
            (root / "patches" / folder).mkdir(parents=True)
        (root / "patches" / folder / "0002-b.patch").write_text(later)
        return cc.check(root)


changed_source = SRC.replace("x = 1", "x = 2")
assert run_later(changed_source, fix("    int x = 1;", "    int x = 2;")) == ([], 1), "a later patch brings the copy to the new source"
bad, n = run_later(SRC, fix("    int x = 1;", "    int x = 2;"))
assert n == 1 and len(bad) == 1, "a later patch that moves the copy away from the source must fail"
bad, n = run_later(changed_source, fix("    int x = 7;", "    int x = 2;"))
assert len(bad) == 1 and "does not apply" in bad[0], "a hunk that does not apply to the copy must fail"
bad, n = run_later(changed_source, fix("    int x = 1;", "    int x = 2;"), folder="y")
assert n == 1 and len(bad) == 1, "a patch of another folder does not change the copy"
assert run_later(changed_source, fix("    int x = 1;", "    int x = 2;", "src/p/Other.java"))[0], "a patch of another file does not change the copy"
print("ok")
