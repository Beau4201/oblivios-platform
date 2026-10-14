#!/usr/bin/env python3
"""Static check of patches/system-sepolicy/0001: the read rule must exclude every domain that runs
app code, the five names must carry the new type, and the type must be declared once.
Usage: check_sepolicy_patch.py [patch file]"""
import pathlib, re, sys

HERE = pathlib.Path(__file__).resolve().parent
patch = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else next(
    (HERE.parent / "patches" / "system-sepolicy").glob("0001-*.patch"))
added = [l[1:] for l in patch.read_text().splitlines() if l.startswith("+") and not l.startswith("+++")]

# app code runs in appdomain, and also in the two app zygotes (AppZygotePreload), which are not appdomain
NEED_EXCLUDED = {"appdomain", "app_zygote", "native_app_zygote"}
NAMES = {"ro.boot.cdt_hwid", "ro.boot.hardware.devcfg", "ro.boot.hardware.pcbcfg",
         "ro.boot.hw.soc.id", "ro.boot.vbmeta.device"}
errors = []

rules = [l for l in added if re.match(r"get_prop\(.*unit_boot_prop\)", l)]
if len(rules) != 1:
    errors.append(f"expected one get_prop rule for unit_boot_prop, found {len(rules)}")
else:
    m = re.match(r"get_prop\(\{\s*domain((?:\s+-[a-z_]+)+)\s*\}\s*,\s*unit_boot_prop\)", rules[0])
    if not m:
        errors.append(f"cannot parse the rule: {rules[0]!r}")
    else:
        excluded = {x.lstrip("-") for x in m.group(1).split()}
        if excluded != NEED_EXCLUDED:
            errors.append(f"excluded domains {sorted(excluded)}, expected {sorted(NEED_EXCLUDED)}")

labelled = {l.split()[0] for l in added if re.search(r"u:object_r:unit_boot_prop:s0 exact string$", l)}
if labelled != NAMES:
    errors.append(f"labelled names {sorted(labelled)}, expected {sorted(NAMES)}")
if sum(1 for l in added if l.strip() == "system_restricted_prop(unit_boot_prop)") != 1:
    errors.append("the type unit_boot_prop is not declared exactly once")

if errors:
    print("FAILED")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("ok")
