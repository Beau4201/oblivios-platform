#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# The JNI surface exists twice: the `external fun`s of VaultCore.kt and the `Java_org_freeholdos_passwords_core_VaultCore_*`
# exports of freeholdos-services/vault-core/src/jni.rs. A mismatch of names, or of the number of parameters, is an
# UnsatisfiedLinkError or a stack mix-up at run time and nothing before, so it is checked here.
# The Rust file is found at VAULT_JNI_RS or, when the services repository sits next to the platform one, beside it.
# usage: check-jni-sync.sh [APP_DIR]
set -o errexit -o nounset -o pipefail
APP="$(cd "${1:-$(dirname "$0")/..}" && pwd)"
KT="$APP/src/main/kotlin/org/freeholdos/passwords/core/VaultCore.kt"
RS="${VAULT_JNI_RS:-$APP/../../../freeholdos-services/vault-core/src/jni.rs}"
[[ -f "$KT" ]] || { echo "missing $KT" >&2; exit 1; }
[[ -f "$RS" ]] || { echo "missing $RS (set VAULT_JNI_RS)" >&2; exit 1; }

python3 - "$KT" "$RS" <<'PY'
import re, sys
kt, rs = (open(p, encoding="utf-8").read() for p in sys.argv[1:3])

def kotlin_externals(text):
    out = {}
    for m in re.finditer(r"external\s+fun\s+(\w+)\s*\(([^)]*)\)", text):
        params = [p for p in (x.strip() for x in m.group(2).split(",")) if p]
        out[m.group(1)] = len(params)
    return out

def rust_exports(text):
    out = {}
    for m in re.finditer(r"pub extern \"system\" fn Java_org_freeholdos_passwords_core_VaultCore_(\w+)<'l>\((.*?)\)\s*->", text, re.S):
        params = [p for p in re.split(r",\s*(?=\w+\s*:)", m.group(2).strip().rstrip(",")) if p.strip()]
        # the first two are the JNI environment and the receiver
        out[m.group(1)] = len(params) - 2
    return out

k, r = kotlin_externals(kt), rust_exports(rs)
bad = []
for name in sorted(set(k) | set(r)):
    if name not in k:
        bad.append(f"exported by Rust but not declared in VaultCore.kt: {name}")
    elif name not in r:
        bad.append(f"declared in VaultCore.kt but not exported by Rust: {name}")
    elif k[name] != r[name]:
        bad.append(f"{name}: {k[name]} parameter(s) in Kotlin, {r[name]} in Rust")
if bad:
    print("\n".join(bad), file=sys.stderr)
    sys.exit(1)
print(f"jni sync ok: {len(k)} functions")
PY
