#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Static guards of the Passwords app, read from the manifest and the sources:
#   - no network or radio permission, no network security config, allowBackup="false";
#   - every component runs in the app's own process;
#   - every Activity extends SecureActivity, which sets FLAG_SECURE, and every Compose dialog, sheet, popup or menu
#     (separate windows, which FLAG_SECURE of the activity does not cover) names SecureFlagPolicy.SecureOn;
#   - no log call (a log line is the easiest place for a secret to leave the app).
# It reads the sources and the manifest of the package. The merged manifest of a built APK (what libraries add) is not read by
# any script yet; until it is, the permission list below is an allow-list of the source manifest only (review of 2026-10-23, P-20).
# usage: check-static.sh [APP_DIR]
set -o errexit -o nounset -o pipefail
APP="$(cd "${1:-$(dirname "$0")/..}" && pwd)"
M="$APP/AndroidManifest.xml"
KT="$APP/src/main/kotlin"
bad=0
fail() { echo "FAIL: $*" >&2; bad=1; }

[[ -f "$M" ]] || { echo "no AndroidManifest.xml in $APP" >&2; exit 1; }

# Permissions that would let the app reach a network or a radio.
FORBIDDEN='INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE|NEARBY_WIFI_DEVICES|BLUETOOTH|NFC|CHANGE_WIFI_MULTICAST_STATE|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION|MANAGE_NETWORK_POLICY|CONNECTIVITY_INTERNAL'
if grep -E -n "android\.permission\.($FORBIDDEN)" "$M" >&2; then fail "the manifest names a network or radio permission"; fi
# Only the permissions on this list may be named: a deny list lets every permission nobody thought of through.
ALLOWED='USE_BIOMETRIC'
while IFS= read -r perm; do
  [[ "$perm" =~ ^android\.permission\.($ALLOWED)$ ]] || fail "the manifest names the permission $perm, which is not on the allow list ($ALLOWED)"
done < <(grep -o '<uses-permission[^>]*android:name="[^"]*"' "$M" | sed 's/.*android:name="//; s/"$//')
grep -q 'android:allowBackup="false"' "$M" || fail 'android:allowBackup="false" is missing from the manifest'
if grep -q 'networkSecurityConfig' "$M"; then fail "the manifest has a network security config"; fi
if grep -q 'usesCleartextTraffic' "$M"; then fail "the manifest says something about cleartext traffic (the app has no traffic)"; fi
if grep -E -n 'android:process=' "$M" >&2; then fail "a component runs in another process"; fi

if [[ -d "$KT" ]]; then
  # Activities.
  while IFS= read -r f; do
    if grep -E -q '\b(class|object)\s+\w+[^{]*:\s*[^{]*\b(Activity|ComponentActivity|FragmentActivity|AppCompatActivity)\b\s*\(' "$f"; then
      if [[ "$(basename "$f")" != "SecureActivity.kt" ]]; then fail "$f: an Activity that does not extend SecureActivity"; fi
    fi
  done < <(find "$KT" -name '*.kt' | sort)
  SA="$(find "$KT" -name SecureActivity.kt | head -n 1)"
  if [[ -z "$SA" ]]; then fail "SecureActivity.kt is missing"
  elif ! grep -q 'FLAG_SECURE' "$SA" || ! grep -E -q '(setFlags|addFlags)\(' "$SA"; then fail "SecureActivity does not set FLAG_SECURE"; fi

  # Separate windows in Compose: the call and the five lines after it must name the secure policy.
  while IFS= read -r hit; do
    file="${hit%%:*}"; rest="${hit#*:}"; line="${rest%%:*}"
    if ! sed -n "${line},$((line + 6))p" "$file" | grep -q 'SecureFlagPolicy\.SecureOn'; then
      fail "$file:$line: a dialog, sheet, popup or menu without SecureFlagPolicy.SecureOn"
    fi
  done < <(grep -n -E '\b(Dialog|AlertDialog|BasicAlertDialog|ModalBottomSheet|DropdownMenu|ExposedDropdownMenu|Popup)\(' -r "$KT" --include='*.kt' | grep -v -E '^\S+:[0-9]+:\s*(//|\*)' || true)

  # Framework dialogs are windows of their own too: a file that builds one must set FLAG_SECURE on it.
  while IFS= read -r f; do
    grep -q 'FLAG_SECURE' "$f" || fail "$f: builds a dialog (AlertDialog, Dialog, PopupWindow or Toast) without FLAG_SECURE in the same file"
  done < <(grep -l -E '\b(AlertDialog\.Builder|Dialog\(|PopupWindow\(|Toast\.makeText)' -r "$KT" --include='*.kt' || true)

  # Saved state is the way a secret would reach disk or another process; the screens keep theirs in memory only.
  if grep -n -E '\brememberSaveable\b|\bSavedStateHandle\b' -r "$KT" --include='*.kt' | grep -v -E ':[0-9]+:\s*(//|\*|/\*)' >&2; then
    fail "a screen saves state (rememberSaveable, SavedStateHandle or a bundle put in onSaveInstanceState)"
  fi

  if grep -n -E '\b(android\.util\.)?Log\.[dviwe]\(|\bprintln\(|\.printStackTrace\(|Timber\.' -r "$KT" --include='*.kt' >&2; then
    fail "a log or print call in the app"
  fi

  # Chips (ADR-191): a dataset value is added with a null filter (NO_FILTER). A filter taken from the value would let the
  # system match what the user typed against the stored username or password, and a keyboard could probe the value by
  # watching which chips stay.
  while IFS= read -r hit; do
    fail "$hit: a dataset value without NO_FILTER"
  done < <(grep -n -E '\.setValue\(' -r "$KT/org/oblivios/passwords/fill" --include='*.kt' | grep -v -E ':[0-9]+:\s*(//|\*|/\*)' | grep -v 'NO_FILTER' || true)
fi

# The feature switch of the chips: without it the system never asks the keyboard for them.
grep -q 'android:supportsInlineSuggestions="true"' "$APP/res/xml/pm_autofill_service.xml" || fail 'the autofill service does not declare supportsInlineSuggestions'

[[ $bad -eq 0 ]] || exit 1
echo "static checks ok"
