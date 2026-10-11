#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Runs AirPodsDuckTrackerTest (ADR-127, patch 0011) on the build server's JDK against the tree's own
# junit, hamcrest, truth and guava jars, without building Bluetooth. The tracker is plain Java; the two
# Android annotations are replaced by a stub and JUnit4 for the test. Needs BUILD_HOST='<user>@<host>'
# and the directory of the authoring worktree as the first argument (default: the airpods-audio-route
# worktree of this repository). Run it after a module build so that the jars exist.
set -o errexit -o nounset -o pipefail
: "${BUILD_HOST:?set BUILD_HOST='<user>@<host>'}"
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
TREE="${1:-$HERE/.worktrees/airpods-audio-route}"
SRC="$TREE/android/app/src/com/android/bluetooth/btservice/AirPodsDuckTracker.java"
TST="$TREE/android/app/tests/unit/src/com/android/bluetooth/btservice/AirPodsDuckTrackerTest.java"
W=/srv/sovereignos/tmp-primary/p7b-duck
ssh -n -o BatchMode=yes "$BUILD_HOST" "mkdir -p $W/src/com/android/bluetooth/btservice $W/src/com/android/internal/annotations"
scp -q -o BatchMode=yes "$SRC" "$BUILD_HOST:$W/src/com/android/bluetooth/btservice/AirPodsDuckTracker.java"
sed -e '/import androidx.test.ext.junit.runners.AndroidJUnit4;/d' -e '/import androidx.test.filters.SmallTest;/d' \
    -e '/^@SmallTest$/d' -e 's/@RunWith(AndroidJUnit4.class)/@RunWith(org.junit.runners.JUnit4.class)/' "$TST" \
  | ssh -o BatchMode=yes "$BUILD_HOST" "cat > $W/src/com/android/bluetooth/btservice/AirPodsDuckTrackerTest.java"
ssh -o BatchMode=yes "$BUILD_HOST" bash -s <<'REMOTE'
set -o errexit -o nounset -o pipefail
P=/srv/sovereignos/platform-primary
W=/srv/sovereignos/tmp-primary/p7b-duck
I=$P/out-lynx/soong/.intermediates/external
J=$P/prebuilts/jdk/jdk21/linux-x86/bin
CP=$I/junit/junit/linux_glibc_common/javac/junit.jar:$I/hamcrest/hamcrest-core/hamcrest/linux_glibc_common/javac/hamcrest.jar:$I/truth/truth/android_common/javac/truth.jar:$I/guava/guava/linux_glibc_common/combined/guava.jar
cat > $W/src/com/android/internal/annotations/GuardedBy.java <<'EOF2'
package com.android.internal.annotations;
public @interface GuardedBy { String value(); }
EOF2
O=$W/out-$$   # a fresh directory per run; nothing is deleted
mkdir -p $O
$J/javac -d $O -cp "$CP" $(find $W/src -name '*.java')
$J/java -cp "$O:$CP" org.junit.runner.JUnitCore com.android.bluetooth.btservice.AirPodsDuckTrackerTest
REMOTE
