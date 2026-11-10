#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Tests of the guards of the Passwords app: every rule of check-resources.py, check-static.sh and check-jni-sync.sh must
# fail on a tree that breaks it, and all of them must pass on the real app. A guard that has never been seen to fail
# proves nothing. Runs on the workstation or the server; it only reads and copies files.
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
APP="$(cd "$HERE/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
fails=0

fresh() { rm -rf "$WORK/app"; cp -r "$APP" "$WORK/app"; echo "$WORK/app"; }
KT() { echo "$1/src/main/kotlin/org/oblivios/passwords"; }

# expect_fail NAME CHECK-COMMAND... : the command must exit non-zero
expect_fail() {
  local name="$1"; shift
  if "$@" >"$WORK/out" 2>&1; then
    echo "NOT CAUGHT: $name"; fails=$((fails + 1))
  else
    echo "caught:     $name"
  fi
}
expect_pass() {
  local name="$1"; shift
  if "$@" >"$WORK/out" 2>&1; then
    echo "passes:     $name"
  else
    echo "FAILS:      $name"; sed 's/^/    /' "$WORK/out"; fails=$((fails + 1))
  fi
}

RES=(python3 "$APP/tools/check-resources.py")
STA=(bash "$APP/tools/check-static.sh")
JNI=(bash "$APP/tools/check-jni-sync.sh")

expect_pass "the real app: resources" "${RES[@]}" "$APP"
expect_pass "the real app: static" "${STA[@]}" "$APP"
expect_pass "the real app: JNI sync" "${JNI[@]}" "$APP"

# --- check-resources.py ---
d="$(fresh)"; mkdir -p "$(KT "$d")/ui"
printf 'package org.oblivios.passwords.ui\nfun f() { Text("Hello world") }\n' > "$(KT "$d")/ui/Bad.kt"
expect_fail "resources: quoted user-visible string in ui/" python3 "$d/tools/check-resources.py" "$d"

d="$(fresh)"; mkdir -p "$(KT "$d")/ui"
printf 'package org.oblivios.passwords.ui\nfun f() { Text(text = "Hello world") // i18n-ok\n  val k = "state_key"\n}\n' > "$(KT "$d")/ui/Ok.kt"
expect_pass "resources: an i18n-ok line and a key without space are allowed" python3 "$d/tools/check-resources.py" "$d"

d="$(fresh)"; mkdir -p "$(KT "$d")/ui"
printf 'package org.oblivios.passwords.ui\nfun f() = R.string.pm_does_not_exist\n' > "$(KT "$d")/ui/Missing.kt"
expect_fail "resources: R.string name missing from strings.xml" python3 "$d/tools/check-resources.py" "$d"

d="$(fresh)"; printf '<vector xmlns:android="http://schemas.android.com/apk/res/android"/>\n' > "$d/res/drawable/ic_extra.xml"
expect_fail "resources: a drawable that is not in the icon list" python3 "$d/tools/check-resources.py" "$d"

d="$(fresh)"; : > "$d/res/drawable/picture.png"
expect_fail "resources: a bitmap drawable" python3 "$d/tools/check-resources.py" "$d"

d="$(fresh)"; sed -i '0,/ic_search/{/ic_search/d}' "$d/tools/icons.txt"
expect_fail "resources: an icon of the list without its drawable (list and files differ)" python3 "$d/tools/check-resources.py" "$d"

d="$(fresh)"; sed -i 's#</resources>#<string name="pm_search_hint">again</string></resources>#' "$d/res/values/strings.xml"
expect_fail "resources: duplicate string name" python3 "$d/tools/check-resources.py" "$d"

d="$(fresh)"; sed -i 's#</resources>#<string name="no_prefix">x</string></resources>#' "$d/res/values/strings.xml"
expect_fail "resources: string name without the pm_ prefix" python3 "$d/tools/check-resources.py" "$d"

# --- check-static.sh ---
for perm in INTERNET ACCESS_NETWORK_STATE ACCESS_WIFI_STATE CHANGE_NETWORK_STATE NEARBY_WIFI_DEVICES BLUETOOTH_CONNECT NFC; do
  d="$(fresh)"; sed -i "s#<application#<uses-permission android:name=\"android.permission.$perm\"/><application#" "$d/AndroidManifest.xml"
  expect_fail "static: manifest names $perm" bash "$d/tools/check-static.sh" "$d"
done

# The allow list (P-20): a permission nobody thought to put on the deny list is caught too.
for perm in READ_CONTACTS CAMERA RECORD_AUDIO READ_PHONE_STATE; do
  d="$(fresh)"; sed -i "s#<application#<uses-permission android:name=\"android.permission.$perm\"/><application#" "$d/AndroidManifest.xml"
  expect_fail "static: manifest names $perm (not on the allow list)" bash "$d/tools/check-static.sh" "$d"
done

d="$(fresh)"; sed -i 's#android:allowBackup="false"#android:allowBackup="true"#' "$d/AndroidManifest.xml"
expect_fail "static: allowBackup true" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; sed -i 's#android:allowBackup="false"##' "$d/AndroidManifest.xml"
expect_fail "static: allowBackup missing" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; sed -i 's#android:name="org.oblivios.passwords.MainActivity"#android:name="org.oblivios.passwords.MainActivity" android:allowTaskReparenting="true" android:process=":x"#' "$d/AndroidManifest.xml"
expect_fail "static: a component in another process" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; sed -i 's#<application#<application android:networkSecurityConfig="@xml/net"#' "$d/AndroidManifest.xml"
expect_fail "static: a network security config" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; printf 'package org.oblivios.passwords\nclass Other : ComponentActivity()\n' > "$(KT "$d")/Other.kt"
expect_fail "static: an Activity that is not a SecureActivity" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; sed -i 's#FLAG_SECURE#FLAG_KEEP_SCREEN_ON#g' "$(KT "$d")/SecureActivity.kt"
expect_fail "static: SecureActivity without FLAG_SECURE" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; mkdir -p "$(KT "$d")/ui"
printf 'package org.oblivios.passwords.ui\nfun f() {\n  AlertDialog(onDismissRequest = {}, confirmButton = {})\n}\n' > "$(KT "$d")/ui/D.kt"
expect_fail "static: a Compose dialog without a secure policy" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; mkdir -p "$(KT "$d")/ui"
printf 'package org.oblivios.passwords.ui\nfun f() {\n  AlertDialog(onDismissRequest = {}, confirmButton = {}, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn))\n}\n' > "$(KT "$d")/ui/D.kt"
expect_pass "static: a Compose dialog with SecureOn" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; printf 'package org.oblivios.passwords\nfun f(a: android.app.Activity) { android.app.AlertDialog.Builder(a).create().show() }\n' > "$(KT "$d")/Dlg.kt"
expect_fail "static: a framework dialog without FLAG_SECURE" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; printf 'package org.oblivios.passwords\nfun f(a: android.app.Activity) { val d = android.app.AlertDialog.Builder(a).create(); d.window?.setFlags(8192, 8192) // FLAG_SECURE\n d.show() }\n' > "$(KT "$d")/Dlg.kt"
expect_pass "static: a framework dialog that sets FLAG_SECURE" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; mkdir -p "$(KT "$d")/ui"; printf 'package org.oblivios.passwords.ui\nfun f() { val s = rememberSaveable { 1 } }\n' > "$(KT "$d")/ui/Saved.kt"
expect_fail "static: rememberSaveable in a screen" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; printf 'package org.oblivios.passwords\nfun f() { android.util.Log.d("tag", "x") }\n' > "$(KT "$d")/Logs.kt"
expect_fail "static: a log call in the app" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; printf 'package org.oblivios.passwords.fill\nfun f(b: android.service.autofill.Dataset.Builder, id: android.view.autofill.AutofillId) { b.setValue(id, null) }\n' > "$(KT "$d")/fill/NoFilter.kt"
expect_fail "static: a dataset value without NO_FILTER" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; printf 'package org.oblivios.passwords.fill\nfun f(b: android.service.autofill.Dataset.Builder, id: android.view.autofill.AutofillId) {\n  // b.setValue(id, null)\n  b.setValue(id, null, NO_FILTER, null)\n}\n' > "$(KT "$d")/fill/WithFilter.kt"
expect_pass "static: a dataset value with NO_FILTER and a commented call" bash "$d/tools/check-static.sh" "$d"

d="$(fresh)"; sed -i 's#supportsInlineSuggestions="true"#supportsInlineSuggestions="false"#' "$d/res/xml/pm_autofill_service.xml"
expect_fail "static: the service does not declare inline suggestions" bash "$d/tools/check-static.sh" "$d"

# --- check-jni-sync.sh ---
d="$(fresh)"; sed -i '/external fun strength/d' "$(KT "$d")/core/VaultCore.kt"
expect_fail "jni: a Rust export without its external fun" bash "$d/tools/check-jni-sync.sh" "$d"

d="$(fresh)"; sed -i 's#external fun strength(password: ByteArray): Int#external fun strength(password: ByteArray): Int\n    external fun extra(x: Int): Int#' "$(KT "$d")/core/VaultCore.kt"
expect_fail "jni: an external fun without its Rust export" bash "$d/tools/check-jni-sync.sh" "$d"

d="$(fresh)"; sed -i 's#external fun markFilled(handle: Long, id: ByteArray, nowMs: Long)#external fun markFilled(handle: Long, id: ByteArray)#' "$(KT "$d")/core/VaultCore.kt"
expect_fail "jni: a different number of parameters" bash "$d/tools/check-jni-sync.sh" "$d"

if [[ $fails -ne 0 ]]; then echo "$fails check(s) failed"; exit 1; fi
echo "all guard tests passed"
