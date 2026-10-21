#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Tests of build-pack.py on a fixture shaped like Simple Icons. Run: python3 -I tests/test_build_pack.py"""
import io
import json
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL = os.path.join(os.path.dirname(HERE), "build-pack.py")
sys.path.insert(0, os.path.dirname(HERE))
import importlib.util

spec = importlib.util.spec_from_file_location("build_pack", TOOL)
bp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bp)

SVG = '<svg role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>{t}</title><path d="{d}"/></svg>'


def fixture(root, icons):
    os.makedirs(os.path.join(root, "data"))
    os.makedirs(os.path.join(root, "icons"))
    json.dump([{k: v for k, v in i.items() if k != "svg"} for i in icons], open(os.path.join(root, "data", "simple-icons.json"), "w"))
    for i in icons:
        slug = i.get("slug") or bp.title_to_slug(i["title"])
        if "svg" in i:
            open(os.path.join(root, "icons", slug + ".svg"), "w").write(i["svg"])
        else:
            open(os.path.join(root, "icons", slug + ".svg"), "w").write(SVG.format(t=i["title"], d="M0 0h24v24H0z"))


ICONS = [
    {"title": "GitHub", "hex": "181717", "source": "https://github.com/logos"},
    {"title": "Codeberg", "hex": "2185D0", "source": "https://codeberg.org"},
    {"title": "BBC", "hex": "000000", "source": "https://www.bbc.co.uk/"},
    {"title": "GitHub Pages Copy", "slug": "githubpagescopy", "hex": "222222", "source": "https://pages.github.com/x"},
    {"title": "Broken", "hex": "000000", "source": "https://broken.example", "svg": "<svg><path d='a'/><path d='b'/></svg>"},
    {"title": "No Site", "hex": "000000", "source": ""},
]


def run(overrides=None):
    with tempfile.TemporaryDirectory() as t:
        fixture(t, ICONS)
        log = io.StringIO()
        icons, domains = bp.build(t, overrides or {}, log)
        return icons, domains, log.getvalue()


def test_registrable_domain():
    assert bp.registrable("www.github.com") == "github.com"
    assert bp.registrable("a.b.c.example.org") == "example.org"
    assert bp.registrable("www.bbc.co.uk") == "bbc.co.uk"
    assert bp.registrable("192.0.2.1") is None and bp.registrable("localhost") is None and bp.registrable("") is None


def test_title_to_slug():
    assert bp.title_to_slug("GitHub") == "github"
    assert bp.title_to_slug("C++") == "cplusplus"
    assert bp.title_to_slug("Node.js") == "nodedotjs"
    assert bp.title_to_slug("AT&T") == "atandt"
    assert bp.title_to_slug("Pokémon") == "pokemon"


def test_domain_map_has_no_duplicate_domains():
    icons, domains, log = run()
    assert len(domains) == len(set(domains)), "a domain twice"
    assert domains["github.com"] == "github", "the first by title keeps the domain"
    assert "githubpagescopy" in icons and "githubpagescopy" not in domains.values()
    assert "github.com: github kept, githubpagescopy ignored" in log


def test_every_mapped_icon_exists_in_the_pack():
    icons, domains, _ = run({"example.net": "does-not-exist"})
    assert "example.net" not in domains
    assert all(slug in icons for slug in domains.values())


def test_overrides_win_over_derived_rows():
    icons, domains, _ = run({"github.com": "codeberg", "bbc.co.uk": "-", "added.example": "github"})
    assert domains["github.com"] == "codeberg"
    assert "bbc.co.uk" not in domains
    assert domains["added.example"] == "github"


def test_unusable_icons_are_skipped_and_reported():
    icons, domains, log = run()
    assert "broken" not in icons and "skipped Broken: not a single path" in log
    assert "nosite" in icons and not any(v == "nosite" for v in domains.values())


def test_the_cli_writes_the_two_files_and_the_coverage():
    with tempfile.TemporaryDirectory() as t:
        src, out = os.path.join(t, "src"), os.path.join(t, "out")
        fixture(src, ICONS)
        top = os.path.join(t, "top.txt")
        open(top, "w").write("github.com\ncodeberg.org\nexample.com\n")
        r = subprocess.run([sys.executable, "-I", TOOL, "--simple-icons", src, "--out", out, "--top", top], capture_output=True, text=True)
        assert r.returncode == 0, r.stderr
        assert "coverage" in r.stdout and "(66%)" in r.stdout, r.stdout
        rows = open(os.path.join(out, "domains.tsv")).read().splitlines()
        assert rows == sorted(rows) and all(len(x.split("\t")) == 2 for x in rows)
        assert all(len(x.split("\t")) == 3 for x in open(os.path.join(out, "icons.tsv")).read().splitlines())


if __name__ == "__main__":
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for t in tests:
        t()
        print("ok", t.__name__)
    print(f"{len(tests)} passed")
