#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Writes the colours of the design reference page as Android resources, so that the app uses the page's values and
not a copy typed by hand (ADR-160). Nothing is guessed: a value that the page does not hold stops the script.

usage: extract-tokens.py DESIGN.html RES_DIR
Reads, for the dark and the light appearance:
  - the colour roles of `.dev,.spec{...}` and `.light .dev,.light .spec{...}`;
  - the colours of the globe in `setPalette()`;
  - the few colours the page sets in single rules (the map background, shadows, scrims, the button text);
  - the colours of the flat country map in `renderMini()` and of a marker in `render()`.
and writes RES_DIR/values/colors.xml (light) and RES_DIR/values-night/colors.xml (dark), and
RES_DIR/values/bools.xml and RES_DIR/values-night/bools.xml (whether the night globe has city lights).
"""
import pathlib
import re
import sys

ROLES = ["bg", "card", "cardrow", "bd", "div", "on", "onv", "ol", "acc", "accsel", "pill", "pillbd", "info", "infobd",
         "nav", "navbd", "handle", "btn", "btnbd", "tile", "tilebd", "sw", "swoff", "err", "lbl", "lblbd", "shield",
         "ondev", "glass", "segtrack", "segthumb"]
GLOBE = ["tex", "coastGlow", "o0", "o1", "land", "coast", "grid", "glow", "rim", "shade", "halo", "txt", "pri", "white"]
MINI = ["bg", "fill_hit", "fill", "stroke_hit", "stroke", "pin", "pin_halo", "label"]
COLOUR = r"#[0-9A-Fa-f]{3,8}\b|rgba?\([^)]*\)"


def fail(message):
    raise SystemExit("extract-tokens: " + message)


def rule(css, selector):
    m = re.search(r"(?:^|[}/])\s*" + re.escape(selector) + r"\s*\{([^{}]*)\}", css)
    if not m:
        fail("no rule " + selector)
    return m.group(1)


def colours(text):
    return re.findall(COLOUR, text)


def android(value):
    value = value.strip()
    if value.startswith("#"):
        h = value[1:].upper()
        if len(h) == 3:
            h = "".join(c * 2 for c in h)
        if len(h) != 6:
            fail("colour not understood: " + value)
        return "#FF" + h
    m = re.fullmatch(r"rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([0-9.]+))?\)", value)
    if not m:
        fail("colour not understood: " + value)
    r, g, b = (int(m.group(i)) for i in (1, 2, 3))
    a = round(float(m.group(4) if m.group(4) is not None else 1) * 255)
    return "#%02X%02X%02X%02X" % (a, r, g, b)


def triple(value):
    """'70,110,175' (the texture colour: the alpha is set when it is drawn) as an opaque colour."""
    r, g, b = (int(v) for v in value.split(","))
    return "#%02X%02X%02X" % (r, g, b)


def variables(css, selector):
    block = rule(css, selector)
    values = dict(re.findall(r"--([a-z0-9]+):\s*([^;]+);", block))
    missing = [n for n in ROLES + ["sheet"] if n not in values]
    if missing:
        fail(selector + ": missing " + str(missing))
    return values


def palette(js):
    m = re.search(r"function setPalette\(light\)\{\s*C=light\?\{(.*?)\}\s*:\{(.*?)\}\s*;?\s*\}", js, re.S)
    if not m:
        fail("no setPalette")
    out = []
    for body in (m.group(1), m.group(2)):   # light first, then dark
        d = dict(re.findall(r"(\w+):'([^']*)'", body))
        d.update({k: v for k, v in re.findall(r"(\w+):(\d)\b", body)})
        missing = [k for k in GLOBE + ["city"] if k not in d]
        if missing:
            fail("setPalette: missing " + str(missing))
        out.append(d)
    return out[0], out[1]


def ternaries(js, function):
    i = js.index("function " + function)
    j = js.find("\nfunction ", i + 10)
    body = js[i:j if j > 0 else len(js)]
    return re.findall(r"\bl\?'([^']*)':'([^']*)'", body)


def build(page):
    src = page.read_text(encoding="utf-8")
    css = re.search(r"<style[^>]*>(.*?)</style>", src, re.S).group(1)
    js = src[src.index("<script>"):]
    dark_vars = variables(css, ".dev,.spec")
    light_vars = variables(css, ".light .dev,.light .spec")
    gl, gd = palette(js)
    mini = ternaries(js, "renderMini")
    if len(mini) != len(MINI):
        fail("renderMini: %d colour pairs, %d expected" % (len(mini), len(MINI)))
    core = ternaries(js, "render")
    if not core:
        fail("render: no marker colour")
    out = {}
    for name, vars_, g, light in (("dark", dark_vars, gd, False), ("light", light_vars, gl, True)):
        c = {}
        for role in ROLES:
            c[role] = vars_[role]
        top, bottom = re.match(r"linear-gradient\(\s*([^,]+),\s*([^)]+)\)", vars_["sheet"]).groups()
        c["sheet_top"], c["sheet_bottom"] = top, bottom
        for k in GLOBE:
            c["g_" + k] = triple(g[k]) if k == "tex" else g[k]
        k = 1 if not light else 0   # a ternary is l ? light : dark
        pick = (lambda pair: pair[0]) if light else (lambda pair: pair[1])
        for key, pair in zip(MINI, mini):
            c["mini_" + key] = pick(pair)
        c["marker_core"] = pick(core[0])
        tab = colours(rule(css, ".light #tabMap" if light else "#tabMap"))
        c["map_glow"] = tab[0]
        c["map_edge"] = tab[1] if light else vars_["bg"]
        c["btn_text"] = colours(rule(css, ".light .btn" if light else ".btn"))[0]
        c["info_text"] = colours(rule(css, ".light .info"))[0] if light else vars_["onv"]
        c["scrim_sheet"] = colours(rule(css, "#fsheet .scrim"))[0]
        c["scrim_dialog"] = colours(rule(css, "#dlg"))[0]
        c["shadow_card"] = colours(rule(css, ".light .lbl" if light else ".lbl"))[0]
        c["shadow_sheet"] = colours(rule(css, ".light #sheet" if light else "#sheet"))[0]
        c["shadow_bar"] = colours(rule(css, ".light .bnav" if light else ".bnav"))[0]
        c["shadow_menu"] = colours(rule(css, "#menu"))[0]
        c["shadow_tile_big"] = colours(rule(css, ".id .big"))[0]
        c["shadow_tile"] = colours(rule(css, ".light .cot"))[0] if light else "rgba(0,0,0,0)"
        thumb = colours(rule(css, ".sw2 i"))
        c["thumb"], c["shadow_thumb"] = thumb[0], thumb[1]
        c["city"] = g["city"]
        out[name] = c
    return out


def write(path, c, comment):
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- SPDX-License-Identifier: Apache-2.0 -->",
             "<!-- %s Generated by tools/extract-tokens.py from the design reference page. -->" % comment, "<resources>"]
    for name in sorted(k for k in c if k != "city"):
        lines.append('    <color name="at_%s">%s</color>' % (name.lower(), android(c[name])))
    lines.append("</resources>")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_bool(path, c, comment):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('<?xml version="1.0" encoding="utf-8"?>\n<!-- SPDX-License-Identifier: Apache-2.0 -->\n'
                    "<!-- %s Generated by tools/extract-tokens.py from the design reference page. -->\n<resources>\n"
                    '    <bool name="at_city_lights">%s</bool>\n</resources>\n'
                    % (comment, "true" if c["city"] == "1" else "false"), encoding="utf-8")


def main():
    page, res = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    out = build(page)
    write(res / "values-night/colors.xml", out["dark"], "Dark colours.")
    write(res / "values/colors.xml", out["light"], "Light colours.")
    write_bool(res / "values-night/bools.xml", out["dark"], "Dark.")
    write_bool(res / "values/bools.xml", out["light"], "Light.")
    print("wrote", res / "values/colors.xml", "and", res / "values-night/colors.xml")


if __name__ == "__main__":
    main()
