#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Runs one of the server-side tools of the Passwords app from the workstation: copies the app directory to a scratch
# directory on the build server (rsync, nothing deleted outside it) and runs the tool there, niced.
#   PWM_BUILD_SSH   user@host of the build server (kept out of the repository on purpose)
#   remote.sh host-checks [TEST_CLASS...]   |   remote.sh typecheck
# Temporary files go to $WORK/tmp, and the JVMs write no perf-data file: the server's /tmp is a 241 MB volume of its own, it was
# full on 2026-10-23, and a JVM then dies with SIGBUS on its mmap'd hsperfdata file.
set -o errexit -o nounset -o pipefail
: "${PWM_BUILD_SSH:?set PWM_BUILD_SSH=user@host}"
HERE="${PWM_SRC:-$(cd "$(dirname "$0")/.." && pwd)}"
WORK="${PWM_WORK:-/srv/freeholdos/scratch/pwapp}"
cmd="$1"; shift
ssh -o BatchMode=yes "$PWM_BUILD_SSH" "mkdir -p $WORK"
rsync -rlpt --delete --exclude 'tests/test-checks.sh' -e "ssh -o BatchMode=yes" "$HERE"/ "$PWM_BUILD_SSH:$WORK/app/"
case "$cmd" in
  host-checks) ssh -o BatchMode=yes "$PWM_BUILD_SSH" "mkdir -p $WORK/tmp && TMPDIR=$WORK/tmp JAVA_TOOL_OPTIONS=-XX:-UsePerfData nice -n 19 bash $WORK/app/tools/run-host-checks.sh $WORK/app $*" ;;
  typecheck)   ssh -o BatchMode=yes "$PWM_BUILD_SSH" "mkdir -p $WORK/tmp && cd $WORK && TMPDIR=$WORK/tmp JAVA_TOOL_OPTIONS=-XX:-UsePerfData nice -n 19 bash app/tools/typecheck-on-server.sh $WORK" ;;
  *) echo "usage: remote.sh host-checks|typecheck" >&2; exit 2 ;;
esac
