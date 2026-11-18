#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Fetches, read-only, the files that the Data Map patches change, as they are at HEAD of their project on
# the build server (git show, not the working copy), into a disposable local tree with one git repo per
# project, and commits them as the baseline.
#   usage: BUILD_SSH=user@host fetch-baseline.sh WORKTREE_DIR
# BUILD_SSH comes from the private local server notes; it is never written into a record.
set -o errexit -o nounset -o pipefail
WT="$1"
SSH_TARGET="${BUILD_SSH:?set BUILD_SSH to the build server login (see the private local server notes)}"
TREE="${AOSP_TREE:?set AOSP_TREE}"
FILES=(
  "frameworks/base core/java/android/net/INetdEventCallback.aidl"
  "frameworks/base services/core/java/com/android/server/connectivity/NetdEventListenerService.java"
  "frameworks/base services/java/com/android/server/SystemServer.java"
  "frameworks/base core/res/AndroidManifest.xml"
  "frameworks/base core/res/res/values/strings.xml"
  "frameworks/base services/core/java/com/android/server/EventLogTags.logtags"
  "system/sepolicy private/service.te"
  "system/sepolicy private/service_contexts"
  "system/sepolicy private/seapp_contexts"
  "system/sepolicy private/system_app.te"
  "system/sepolicy private/traceur_app.te"
  "system/sepolicy build/soong/service_fuzzer_bindings.go"
  "vendor/adevtool config/mk/google_devices/common/device-common.mk"
)
mkdir -p "$WT"
declare -A HEADS
for entry in "${FILES[@]}"; do
  repo="${entry%% *}"; path="${entry#* }"
  mkdir -p "$WT/$repo/$(dirname "$path")"
  ssh -n -o BatchMode=yes "$SSH_TARGET" "git -C $TREE/$repo show HEAD:$path" > "$WT/$repo/$path"
  test -s "$WT/$repo/$path" || { echo "empty fetch: $repo $path" >&2; exit 1; }
  HEADS[$repo]="$(ssh -n -o BatchMode=yes "$SSH_TARGET" "git -C $TREE/$repo rev-parse --short=12 HEAD")"
done
for entry in "${FILES[@]}"; do
  repo="${entry%% *}"; path="${entry#* }"
  [ -d "$WT/$repo/.git" ] || git -C "$WT/$repo" init -q
  git -C "$WT/$repo" config user.name "ObliviOS Build"
  git -C "$WT/$repo" config user.email "build@oblivios.local"
  git -C "$WT/$repo" add "$path"
done
for repo in "${!HEADS[@]}"; do
  git -C "$WT/$repo" commit -q -m "baseline: files fetched from ${HEADS[$repo]} (fetched files only)"
  echo "$repo baseline from ${HEADS[$repo]}"
done
