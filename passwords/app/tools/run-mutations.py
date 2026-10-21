#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of the pure Kotlin of the Passwords app (model: freeholdos-services/vault-core/tools/run-mutations.py).

Copies the app directory to a temporary directory, applies ONE mutation at a time (an exact, unique string replaced by
another) and runs `remote.sh host-checks` on the copy. A mutation is killed when the host checks fail for it. One that
does not compile proves nothing and is not counted as killed. A mutation whose text is not found exactly once is an error
of this list, not a surviving mutant.

    PWM_BUILD_SSH=user@host tools/run-mutations.py [--check-applies] [NAME_FILTER]

Left out on purpose: the cap in passphraseThrottleMs (`minOf(consecutiveFailures - 4, 20)` to a larger bound gives the
same values after the min with five minutes), the `<= 3` of the same function (`< 3` also gives 0 for three failures, because
1000 shifted left by 63 is 0), the reset of lastPassphraseFailureMs on a passphrase unlock (the reset count already ends
the wait), and the `lastPassphraseMs == 0` test of stateAtStart (a vault that never
had a passphrase unlock is already `Restart`, because the boot count is -1).
"""
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
P = "src/main/kotlin/org/freeholdos/passwords/lock/LockPolicy.kt"
S = "src/main/kotlin/org/freeholdos/passwords/lock/LockState.kt"

# (name, file, old, new)
MUTATIONS = [
    # constants
    ("lock: background minute shortened", P, "const val BACKGROUND_LOCK_MS = 60_000L", "const val BACKGROUND_LOCK_MS = 59_000L"),
    ("lock: background minute lengthened", P, "const val BACKGROUND_LOCK_MS = 60_000L", "const val BACKGROUND_LOCK_MS = 61_000L"),
    ("lock: six days instead of seven", P, "const val PASSPHRASE_MAX_AGE_MS = 7L * 24", "const val PASSPHRASE_MAX_AGE_MS = 6L * 24"),
    ("lock: eight days instead of seven", P, "const val PASSPHRASE_MAX_AGE_MS = 7L * 24", "const val PASSPHRASE_MAX_AGE_MS = 8L * 24"),
    ("lock: four biometric failures", P, "const val MAX_BIOMETRIC_FAILURES = 5", "const val MAX_BIOMETRIC_FAILURES = 4"),
    ("lock: six biometric failures", P, "const val MAX_BIOMETRIC_FAILURES = 5", "const val MAX_BIOMETRIC_FAILURES = 6"),
    ("lock: throttle cap raised", P, "const val MAX_THROTTLE_MS = 300_000L", "const val MAX_THROTTLE_MS = 301_000L"),
    # throttle
    ("throttle: delay from the fifth failure", P, "if (consecutiveFailures <= 3) return 0L", "if (consecutiveFailures <= 4) return 0L"),
    ("throttle: first step is 2 s", P, "1_000L shl minOf(consecutiveFailures - 4, 20)", "2_000L shl minOf(consecutiveFailures - 4, 20)"),
    ("throttle: steps start one failure late", P, "1_000L shl minOf(consecutiveFailures - 4, 20)", "1_000L shl minOf(consecutiveFailures - 3, 20)"),
    ("throttle: no cap", P, "return minOf(MAX_THROTTLE_MS, 1_000L shl minOf(consecutiveFailures - 4, 20))", "return 1_000L shl minOf(consecutiveFailures - 4, 20)"),
    # stateAtStart
    ("state: a lost secret ignored", P, "if (!env.hardwareSecretPresent()) return LockState.SecretLost", "if (false) return LockState.SecretLost"),
    ("state: a restart ignored", P, "if (store.bootCount != env.bootCount()) return LockState.NeedsPassphrase(Reason.Restart)", "if (false) return LockState.NeedsPassphrase(Reason.Restart)"),
    ("state: changed biometrics ignored", P, "if (!env.biometricsValid()) return LockState.NeedsPassphrase(Reason.BiometricsChanged)", "if (false) return LockState.NeedsPassphrase(Reason.BiometricsChanged)"),
    ("state: five failures ignored", P, "if (store.biometricFailures >= MAX_BIOMETRIC_FAILURES) return LockState.NeedsPassphrase(Reason.FiveFailures)", "if (false) return LockState.NeedsPassphrase(Reason.FiveFailures)"),
    ("state: failure count (>)", P, "if (store.biometricFailures >= MAX_BIOMETRIC_FAILURES) return LockState.NeedsPassphrase(Reason.FiveFailures)", "if (store.biometricFailures > MAX_BIOMETRIC_FAILURES) return LockState.NeedsPassphrase(Reason.FiveFailures)"),
    ("state: seven days ignored", P, "if (store.lastPassphraseMs == 0L || age < 0 || age >= PASSPHRASE_MAX_AGE_MS) return LockState.NeedsPassphrase(Reason.Expired)", "if (store.lastPassphraseMs == 0L || age < 0) return LockState.NeedsPassphrase(Reason.Expired)"),
    ("state: seven days (>)", P, "age >= PASSPHRASE_MAX_AGE_MS", "age > PASSPHRASE_MAX_AGE_MS"),
    ("state: a clock set back extends the days", P, "store.lastPassphraseMs == 0L || age < 0 || age >=", "store.lastPassphraseMs == 0L || age >="),
    ("state: a lost secret after a restart", P, "        if (!env.hardwareSecretPresent()) return LockState.SecretLost\n        if (store.bootCount != env.bootCount()) return LockState.NeedsPassphrase(Reason.Restart)\n", "        if (store.bootCount != env.bootCount()) return LockState.NeedsPassphrase(Reason.Restart)\n        if (!env.hardwareSecretPresent()) return LockState.SecretLost\n"),
    ("state: five failures before changed biometrics", P, "        if (!env.biometricsValid()) return LockState.NeedsPassphrase(Reason.BiometricsChanged)\n        if (store.biometricFailures >= MAX_BIOMETRIC_FAILURES) return LockState.NeedsPassphrase(Reason.FiveFailures)\n", "        if (store.biometricFailures >= MAX_BIOMETRIC_FAILURES) return LockState.NeedsPassphrase(Reason.FiveFailures)\n        if (!env.biometricsValid()) return LockState.NeedsPassphrase(Reason.BiometricsChanged)\n"),
    ("state: changed biometrics before a restart", P, "        if (store.bootCount != env.bootCount()) return LockState.NeedsPassphrase(Reason.Restart)\n        if (!env.biometricsValid()) return LockState.NeedsPassphrase(Reason.BiometricsChanged)\n", "        if (!env.biometricsValid()) return LockState.NeedsPassphrase(Reason.BiometricsChanged)\n        if (store.bootCount != env.bootCount()) return LockState.NeedsPassphrase(Reason.Restart)\n"),
    # biometric failures
    ("failed: not counted", P, "        store.biometricFailures++\n        return if", "        return if"),
    ("failed: the fifth is not reported", P, "return if (store.biometricFailures >= MAX_BIOMETRIC_FAILURES) stateAtStart() else null", "return null"),
    ("failed: reported one early", P, "return if (store.biometricFailures >= MAX_BIOMETRIC_FAILURES) stateAtStart() else null", "return if (store.biometricFailures >= MAX_BIOMETRIC_FAILURES - 1) stateAtStart() else null"),
    ("failed: always reported", P, "return if (store.biometricFailures >= MAX_BIOMETRIC_FAILURES) stateAtStart() else null", "return stateAtStart()"),
    # unlocked
    ("unlocked: failures not cleared", P, "        backgroundedAtMs = if (inForeground) -1L else clock.elapsedMs()\n        store.biometricFailures = 0\n", "        backgroundedAtMs = if (inForeground) -1L else clock.elapsedMs()\n"),
    ("unlocked: timer not cleared", P, "backgroundedAtMs = if (inForeground) -1L else clock.elapsedMs()", "backgroundedAtMs = backgroundedAtMs"),
    ("unlocked: from the background never starts the minute", P, "backgroundedAtMs = if (inForeground) -1L else clock.elapsedMs()", "backgroundedAtMs = -1L"),
    ("unlocked: the foreground flag inverted", P, "backgroundedAtMs = if (inForeground) -1L else clock.elapsedMs()", "backgroundedAtMs = if (inForeground) clock.elapsedMs() else -1L"),
    ("unlocked: every unlock renews the days", P, "        if (viaPassphrase) {\n            store.lastPassphraseMs = clock.nowMs()\n            store.bootCount = env.bootCount()\n            store.passphraseFailures = 0\n            store.lastPassphraseFailureMs = 0L\n        }\n", "        store.lastPassphraseMs = clock.nowMs()\n        store.bootCount = env.bootCount()\n        store.passphraseFailures = 0\n        store.lastPassphraseFailureMs = 0L\n"),
    ("unlocked: no unlock renews the days", P, "        if (viaPassphrase) {", "        if (false) {"),
    ("unlocked: the passphrase time is not stored", P, "            store.lastPassphraseMs = clock.nowMs()\n", ""),
    ("unlocked: the boot count is not stored", P, "            store.bootCount = env.bootCount()\n", ""),
    ("unlocked: passphrase failures kept", P, "            store.passphraseFailures = 0\n", ""),
    # passphrase failures and wait
    ("passphrase failed: not counted", P, "        store.passphraseFailures++\n", ""),
    ("passphrase failed: time not stored", P, "        store.lastPassphraseFailureMs = clock.nowMs()\n    }\n\n    /** Milliseconds", "    }\n\n    /** Milliseconds"),
    ("wait: not reduced by the time passed", P, "return if (since < 0) delay else maxOf(0L, delay - since)", "return delay"),
    ("wait: can be negative", P, "return if (since < 0) delay else maxOf(0L, delay - since)", "return if (since < 0) delay else delay - since"),
    ("wait: grows with the time passed", P, "return if (since < 0) delay else maxOf(0L, delay - since)", "return if (since < 0) delay else maxOf(0L, delay + since)"),
    ("wait: a clock set back shortens it", P, "return if (since < 0) delay else maxOf(0L, delay - since)", "return if (since < 0) 0L else maxOf(0L, delay - since)"),
    # background
    ("background: not remembered", P, "    fun onBackgrounded() {\n        backgroundedAtMs = clock.elapsedMs()\n    }", "    fun onBackgrounded() {\n    }"),
    ("background: minute (>)", P, "clock.elapsedMs() - backgroundedAtMs >= BACKGROUND_LOCK_MS", "clock.elapsedMs() - backgroundedAtMs > BACKGROUND_LOCK_MS"),
    ("background: wall clock instead of the monotonic one", P, "clock.elapsedMs() - backgroundedAtMs >= BACKGROUND_LOCK_MS", "clock.nowMs() - backgroundedAtMs >= BACKGROUND_LOCK_MS"),
    ("foreground: the timer is not cleared", P, "        val due = backgroundDue()\n        backgroundedAtMs = -1L\n", "        val due = backgroundDue()\n"),
    ("foreground: never locks", P, "return if (due) stateAtStart() else null", "return null"),
    ("foreground: always locks", P, "return if (due) stateAtStart() else null", "return stateAtStart()"),
    ("tick: never locks", P, "        if (!backgroundDue()) return null\n        backgroundedAtMs = -1L\n        return stateAtStart()", "        return null"),
    ("tick: fires again and again", P, "        if (!backgroundDue()) return null\n        backgroundedAtMs = -1L\n        return stateAtStart()", "        if (!backgroundDue()) return null\n        return stateAtStart()"),
    ("device lock: the timer is not cleared", P, "    fun onDeviceLocked(): LockState {\n        backgroundedAtMs = -1L\n", "    fun onDeviceLocked(): LockState {\n"),
    ("device lock: does not report the state", P, "    fun onDeviceLocked(): LockState {\n        backgroundedAtMs = -1L\n        return stateAtStart()", "    fun onDeviceLocked(): LockState {\n        backgroundedAtMs = -1L\n        return LockState.Locked"),
]


def killed(output: str, rc: int) -> str:
    if rc == 0:
        return "survived"
    if "COMPILE ERROR" in output:
        return "does-not-compile"
    if "AssertionError" in output or "FAILED" in output:
        return "killed"
    return "inconclusive"


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    only_check = "--check-applies" in sys.argv
    flt = args[0] if args else ""
    muts = [m for m in MUTATIONS if flt in m[0]]
    work = tempfile.mkdtemp(prefix="pwm-mutations-")
    copy = os.path.join(work, "app")
    shutil.copytree(ROOT, copy, ignore=shutil.ignore_patterns("__pycache__"))
    bad = [n for n, f, old, new in muts if open(os.path.join(copy, f)).read().count(old) != 1]
    for n in bad:
        print(f"DOES NOT APPLY exactly once: {n}")
    if bad:
        return 3
    if only_check:
        print(f"all {len(muts)} mutations apply")
        shutil.rmtree(work, ignore_errors=True)
        return 0
    env = dict(os.environ, PWM_SRC=copy, PWM_WORK="/srv/freeholdos/scratch/pwapp-mut")
    remote = os.path.join(ROOT, "tools", "remote.sh")
    base = subprocess.run(["bash", remote, "host-checks"], env=env, capture_output=True, text=True)
    if base.returncode != 0:
        print("the unmutated checks do not pass; nothing to measure")
        print(base.stdout[-2000:], base.stderr[-2000:])
        return 4
    results = []
    for name, f, old, new in muts:
        path = os.path.join(copy, f)
        orig = open(path).read()
        open(path, "w").write(orig.replace(old, new))
        try:
            r = subprocess.run(["bash", remote, "host-checks"], env=env, capture_output=True, text=True, timeout=1800)
            verdict = killed(r.stdout + r.stderr, r.returncode)
            detail = next((l for l in (r.stdout + r.stderr).splitlines() if "FAILED:" in l or "AssertionError" in l), "")[:110]
        except subprocess.TimeoutExpired:
            verdict, detail = "timeout", ""
        finally:
            open(path, "w").write(orig)
        results.append((name, verdict))
        print(f"{verdict:17} {name}  {detail}", flush=True)
    n_killed = sum(1 for _, v in results if v == "killed")
    print(f"{n_killed} of {len(results)} mutations killed")
    shutil.rmtree(work, ignore_errors=True)
    return 0 if n_killed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
