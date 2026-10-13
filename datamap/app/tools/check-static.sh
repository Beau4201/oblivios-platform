#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Static rules of the Data Map app (MAP-010 wording, MAP-011 no network or export path, MAP-013 no network
# permission and no background component). A match is a failure; the patterns are deliberately broad.
# usage: check-static.sh [APP_DIR]
set -o errexit -o nounset -o pipefail
APP="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
bad=0
fail() { echo "FAIL: $1"; bad=1; }

# MAP-013: the manifest holds no network permission and no component but the one activity.
grep -qE 'android\.permission\.(INTERNET|ACCESS_NETWORK_STATE|CHANGE_NETWORK_STATE|ACCESS_WIFI_STATE)' "$APP/AndroidManifest.xml" \
  && fail "a network permission in the manifest"
grep -qE '<(service|receiver|provider)\b' "$APP/AndroidManifest.xml" && fail "a service, receiver or provider in the manifest"
[ "$(grep -c '<activity' "$APP/AndroidManifest.xml")" = "1" ] || fail "the manifest must declare exactly one activity"
grep -qE 'android:process=|android:allowBackup="true"' "$APP/AndroidManifest.xml" && fail "its own process or backup allowed"

# MAP-011: no socket, URL, share or file export in the code.
if grep -rnE 'java\.net\.|javax\.net|okhttp|HttpURLConnection|WebView|ACTION_SEND|ACTION_VIEW|createChooser|openFileOutput|FileOutputStream|getExternalFilesDir|MediaStore|Runtime\.getRuntime|ProcessBuilder|DownloadManager|ConnectivityManager|ClipboardManager' \
     "$APP/src" --include='*.java' --exclude-dir=datamap_generated | grep -v 'src/com/android/internal/datamap/'; then
  fail "a network, share, export or process API in the sources"
fi

# MAP-010: the wording rule. Two apps "could be linked"; nothing says they are.
if grep -rniE '(are|is|were|was) (linked|connected to each other|the same (person|user))|linked together|tracks you' \
     "$APP/res/values" "$APP/src" --include='*.xml' --include='*.java'; then
  fail "the wording rule is broken: nothing may say that two apps are linked"
fi
grep -q 'Only connections Atlas could see are listed. There may be more.' "$APP/res/values/strings.xml" \
  || fail "the note that a connection which is not listed is not proof of none is gone"
[ "$bad" -eq 0 ] && echo "static checks passed"
exit "$bad"
