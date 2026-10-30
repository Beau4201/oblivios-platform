#!/usr/bin/env python3
"""Mutation check for the persona reader: every mutant must make a test assertion fail.
Usage: mutate.py   (run from anywhere; reads the sources next to this file)"""
import pathlib, shutil, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
PERSONA_FILE = ROOT.parent / "system-server" / "src" / "com" / "android" / "server" / "compartments" / "PersonaFile.java"
MUTANTS = [
    ("a link is followed", "OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW | OsConstants.O_CLOEXEC", "OsConstants.O_RDONLY | OsConstants.O_CLOEXEC"),
    ("a directory is read", "!OsConstants.S_ISREG(st.st_mode) || ", ""),
    ("any owner is accepted", " || st.st_uid != mOwnerUid", ""),
    ("a group-writable file is accepted", "(st.st_mode & 0022) != 0", "(st.st_mode & 0002) != 0"),
    ("an other-writable file is accepted", "(st.st_mode & 0022) != 0", "(st.st_mode & 0020) != 0"),
    ("the size is not limited", " || st.st_size < 0 || st.st_size > MAX_BYTES", ""),
    ("a file of exactly the limit plus one is read", "st.st_size > MAX_BYTES", "st.st_size > MAX_BYTES + 1"),
    ("the file is read at every question", "nowMs - mLoadedAtMs >= INTERVAL_MS", "true"),
    ("the file is never read again", "nowMs - mLoadedAtMs >= INTERVAL_MS", "false"),
    ("the file is read again only after two seconds", "nowMs - mLoadedAtMs >= INTERVAL_MS", "nowMs - mLoadedAtMs >= 2 * INTERVAL_MS"),
    ("the file is read again inside the second", "nowMs - mLoadedAtMs >= INTERVAL_MS", "nowMs - mLoadedAtMs >= INTERVAL_MS - 1"),
    ("the time of the read is not kept", "mLoadedAtMs = nowMs;", ""),
    # no mutant for "a short read is accepted": the strict parse of PersonaFile refuses a buffer that is not filled, so the
    # answer cannot differ
    ("the file is read once", "while (n < buf.length) {", "if (n < buf.length) {"),
    ("a parse error is not an answer of no persona", "} catch (ErrnoException | IOException | RuntimeException e) {", "} catch (ErrnoException | IOException e) {"),
    ("the descriptor is not closed", "Os.close(fd);", "if (fd == null) {\n                        Os.close(fd);\n                    }"),
    ("an unusable file removes the persona", "if (fresh != null) {\n                mFile = fresh;", "if (true) {\n                mFile = fresh;"),
    ("the answer is the place of the wrong uid", "mFile.placeOf(uid)", "mFile.placeOf(uid + 1)"),
]


def run(d):
    """Compiles the sources with the test and runs it; the compiler's result or the test's."""
    out = d / "out"
    c = subprocess.run(["javac", "-d", str(out), str(d / "PersonaFile.java"), str(d / "PersonaReader.java"), str(d / "PersonaReaderTest.java")]
                       + [str(p) for p in (d / "stubs" / "android" / "system").glob("*.java")], capture_output=True, text=True)
    if c.returncode != 0:
        return c
    return subprocess.run(["java", "-ea", "-cp", str(out), "com.android.server.compartments.PersonaReaderTest"], capture_output=True, text=True)


def fresh(d):
    shutil.copy(PERSONA_FILE, d / "PersonaFile.java")
    shutil.copy(ROOT / "src" / "PersonaReader.java", d / "PersonaReader.java")
    shutil.copy(ROOT / "test" / "PersonaReaderTest.java", d / "PersonaReaderTest.java")
    shutil.copytree(ROOT / "test" / "stubs", d / "stubs")


# The unmutated tree must pass first: with a failing baseline every mutant would count as killed and prove nothing.
with tempfile.TemporaryDirectory() as d:
    d = pathlib.Path(d)
    fresh(d)
    base = run(d)
    if base.returncode != 0:
        print("BASELINE FAILS, no mutant counts:", (base.stdout + base.stderr)[:400])
        sys.exit(2)
survived = 0
for what, old, new in MUTANTS:
    with tempfile.TemporaryDirectory() as d:
        d = pathlib.Path(d)
        fresh(d)
        f = d / "PersonaReader.java"
        text = f.read_text()
        assert text.count(old) == 1, f"{what!r}: pattern found {text.count(old)} times"
        f.write_text(text.replace(old, new))
        r = run(d)
        if r.returncode == 0:
            print("SURVIVED:", what)
            survived += 1
        elif "error:" in r.stderr and "AssertionError" not in r.stderr:
            print("INVALID MUTANT (does not compile):", what, r.stderr[:200])
            survived += 1
        else:
            print("killed:  ", what)
print(f"{len(MUTANTS) - survived} of {len(MUTANTS)} mutants killed")
sys.exit(1 if survived else 0)
