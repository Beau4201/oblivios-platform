#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of the pure Kotlin of the Passwords app (model: freeholdos-services/vault-core/tools/run-mutations.py).

Copies the app directory to a temporary directory, applies ONE mutation at a time (an exact, unique string replaced by
another) and runs `remote.sh host-checks` on the copy. A mutation is killed when the host checks fail for it. One that
does not compile proves nothing and is not counted as killed. A mutation whose text is not found exactly once is an error
of this list, not a surviving mutant.

    PWM_BUILD_SSH=user@host tools/run-mutations.py [--check-applies] [NAME_FILTER]

Left out on purpose: the bracket test of LogoPack.pathFor (a bracketed host has colons or characters outside the host alphabet, which are refused anyway); the `length < 1` clause of CreatedVault.parse (the size check before it already refuses a payload with an empty file); the cap in passphraseThrottleMs (`minOf(consecutiveFailures - 4, 20)` to a larger bound gives the
same values after the min with five minutes), the `<= 3` of the same function (`< 3` also gives 0 for three failures, because
1000 shifted left by 63 is 0), the reset of lastPassphraseFailureMs on a passphrase unlock (the reset count already ends
the wait), and the `lastPassphraseMs == 0` test of stateAtStart (a vault that never
had a passphrase unlock is already `Restart`, because the boot count is -1), and `held = null` in PendingSaveSlot.expire (an expired
login that stays in `held` is never handed out, because `take` tests the deadline again and answers null; the wipe is what counts), and the `if (pkg !in BUILT_IN)` of OriginPolicy.untrust (the store never holds the built-in browser, because
`trust` refuses it, and `trusted` does not read the store for it).
"""
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K = "src/main/kotlin/org/freeholdos/passwords/"
P = K + "lock/LockPolicy.kt"
F = K + "fill/FieldClassifier.kt"
L = K + "fill/FillPlanner.kt"
W = K + "core/Wire.kt"
Y = K + "core/Reply.kt"
O = K + "ui/LogoPack.kt"
Q = K + "fill/PendingSave.kt"
T = K + "core/SiteText.kt"
OR = K + "fill/Origin.kt"
S = "src/main/kotlin/org/freeholdos/passwords/lock/LockState.kt"
C = K + "fill/ChipPolicy.kt"
CG = K + "core/ClipGuard.kt"

# (name, file, old, new)
MUTATIONS = [
    # PWM-014: the repeated notification of our own copy must not stop the clear
    ("clip: the window is shortened to nothing", CG, "const val OWN_WINDOW_MS = 1_000L", "const val OWN_WINDOW_MS = 0L"),
    ("clip: the window is far too long", CG, "const val OWN_WINDOW_MS = 1_000L", "const val OWN_WINDOW_MS = 60_000L"),
    ("clip: the window ends one millisecond late", CG, "if (nowMs - setAtMs >= OWN_WINDOW_MS)", "if (nowMs - setAtMs > OWN_WINDOW_MS)"),
    ("clip: a change inside the window counts", CG, "if (nowMs - setAtMs >= OWN_WINDOW_MS)", "if (nowMs - setAtMs < OWN_WINDOW_MS)"),
    ("clip: a late change is forgotten", CG, "changedByOthers = true", "changedByOthers = false"),
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
    ("unlocked: failures not cleared", P, "        onAuthenticated()\n        store.biometricFailures = 0\n", "        onAuthenticated()\n"),
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
    ("tick: never locks", P, "        if (!backgroundDue()) return null\n        backgroundedAtMs = -1L\n        authenticatedAtMs = -1L\n        return stateAtStart()", "        return null"),
    ("tick: fires again and again", P, "        if (!backgroundDue()) return null\n        backgroundedAtMs = -1L\n        authenticatedAtMs = -1L\n        return stateAtStart()", "        if (!backgroundDue()) return null\n        authenticatedAtMs = -1L\n        return stateAtStart()"),
    ("device lock: the timer is not cleared", P, "    fun onDeviceLocked(): LockState {\n        backgroundedAtMs = -1L\n", "    fun onDeviceLocked(): LockState {\n"),
    ("device lock: does not report the state", P, "    fun onDeviceLocked(): LockState {\n        backgroundedAtMs = -1L\n        authenticatedAtMs = -1L\n        return stateAtStart()", "    fun onDeviceLocked(): LockState {\n        backgroundedAtMs = -1L\n        authenticatedAtMs = -1L\n        return LockState.Locked"),
    # ADR-215: the confirmation grace
    ("grace: shortened", P, "const val CONFIRM_GRACE_MS = 60_000L", "const val CONFIRM_GRACE_MS = 59_000L"),
    ("grace: lengthened", P, "const val CONFIRM_GRACE_MS = 60_000L", "const val CONFIRM_GRACE_MS = 61_000L"),
    ("grace: ends one millisecond late", P, "clock.elapsedMs() - authenticatedAtMs < CONFIRM_GRACE_MS", "clock.elapsedMs() - authenticatedAtMs <= CONFIRM_GRACE_MS"),
    ("grace: never", P, "fun confirmedRecently() = authenticatedAtMs >= 0 && ", "fun confirmedRecently() = false && "),
    ("grace: an untouched policy is inside it", P, "fun confirmedRecently() = authenticatedAtMs >= 0 && ", "fun confirmedRecently() = "),
    ("grace: an unlock does not start it", P, "        onAuthenticated()\n        store.biometricFailures = 0\n", "        store.biometricFailures = 0\n"),
    ("grace: a confirmation does not start it", P, "    fun onAuthenticated() {\n        authenticatedAtMs = clock.elapsedMs()\n", "    fun onAuthenticated() {\n"),
    ("grace: the wall clock instead of the monotonic one", P, "authenticatedAtMs = clock.elapsedMs()", "authenticatedAtMs = clock.nowMs()"),
    ("grace: the device lock keeps it", P, "        backgroundedAtMs = -1L\n        authenticatedAtMs = -1L\n        return stateAtStart()\n    }\n\n    private fun backgroundDue", "        backgroundedAtMs = -1L\n        return stateAtStart()\n    }\n\n    private fun backgroundDue"),
    ("grace: the tick lock keeps it", P, "        backgroundedAtMs = -1L\n        authenticatedAtMs = -1L\n        return stateAtStart()\n    }\n\n    /** The vault was deleted", "        backgroundedAtMs = -1L\n        return stateAtStart()\n    }\n\n    /** The vault was deleted"),
    ("grace: the lock on return keeps it", P, "        if (due) authenticatedAtMs = -1L\n", ""),
    ("grace: a return before the minute ends it", P, "        if (due) authenticatedAtMs = -1L\n", "        authenticatedAtMs = -1L\n"),
    ("grace: forgetting the vault keeps it", P, "    fun forget() {\n        backgroundedAtMs = -1L\n        authenticatedAtMs = -1L\n", "    fun forget() {\n        backgroundedAtMs = -1L\n"),

    # FieldClassifier.kt
    ("classify: username hints ignored", F, "h in USERNAME_HINTS -> return FieldRole.Username", "h in USERNAME_HINTS -> return FieldRole.Other"),
    ("classify: password hints ignored", F, "h in PASSWORD_HINTS -> return FieldRole.Password", "h in PASSWORD_HINTS -> return FieldRole.Other"),
    ("classify: credit hints no longer block", F, 'h.startsWith("credit") || h.startsWith("cc-")', 'h.startsWith("cc-")'),
    ("classify: search hint no longer blocks", F, 'setOf("search", "phone", "creditcardnumber"', 'setOf("phone", "creditcardnumber"'),
    ("classify: phone hint no longer blocks", F, 'setOf("search", "phone", "creditcardnumber"', 'setOf("search", "creditcardnumber"'),
    ("classify: hints compared with case", F, "it.lowercase().split(' ', '\\t')", "it.split(' ', '\\t')"),
    ("classify: camel case not split", F, 'val spaced = id.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")', "val spaced = id"),
    ("classify: text password variation 0x80", F, "variation in setOf(0x80, 0x90, 0xe0)", "variation in setOf(0x90, 0xe0)"),
    ("classify: visible password variation 0x90", F, "variation in setOf(0x80, 0x90, 0xe0)", "variation in setOf(0x80, 0xe0)"),
    ("classify: web password variation 0xe0", F, "variation in setOf(0x80, 0x90, 0xe0)", "variation in setOf(0x80, 0x90)"),
    ("classify: number password variation", F, "klass == InputTypes.CLASS_NUMBER && variation == 0x10", "klass == InputTypes.CLASS_NUMBER && variation == 0x11"),
    ("classify: the class of a password type ignored", F, "(klass == InputTypes.CLASS_TEXT && variation in setOf(0x80, 0x90, 0xe0))", "(variation in setOf(0x80, 0x90, 0xe0))"),
    ("classify: password types ignored", F, "if (passwordType) return FieldRole.Password", "if (false) return FieldRole.Password"),
    ("classify: search words ignored", F, 'if (words.any { it in NOT_LOGIN_TOKENS && !(it == "address" && ("email" in words || "mail" in words)) }) return FieldRole.Other', "if (false) return FieldRole.Other"),
    ("classify: search words before password types", F, "    if (passwordType) return FieldRole.Password\n    // `email_address` is a login field; `shipping_address` is not.\n    if (words.any { it in NOT_LOGIN_TOKENS && !(it == \"address\" && (\"email\" in words || \"mail\" in words)) }) return FieldRole.Other\n", "    if (words.any { it in NOT_LOGIN_TOKENS && !(it == \"address\" && (\"email\" in words || \"mail\" in words)) }) return FieldRole.Other\n    if (passwordType) return FieldRole.Password\n"),
    ("classify: email type 0x20 ignored", F, "variation in setOf(0x20, 0xd0)", "variation in setOf(0xd0)"),
    ("classify: web email type ignored", F, "variation in setOf(0x20, 0xd0)", "variation in setOf(0x20)"),
    ("classify: the hint current-password ignored", F, '"current-password", "new-password")', '"new-password")'),
    ("classify: the hint new-password ignored", F, '"newpassword", "current-password", "new-password")', '"newpassword", "current-password")'),
    ("classify: the hint email ignored", F, 'setOf("username", "emailaddress", "email")', 'setOf("username", "emailaddress")'),
    ("web: the input type of the node is replaced", F, "if (inputType != 0) return inputType", "if (false) return inputType"),
    ("web: the HTML password type ignored", F, '"password" -> InputTypes.TEXT_WEB_PASSWORD', '"password" -> inputType'),
    ("web: the HTML email type ignored", F, '"email" -> InputTypes.TEXT_WEB_EMAIL', '"email" -> inputType'),
    ("web: the HTML type compared with case", F, "when (htmlType?.lowercase())", "when (htmlType)"),
    ("web: the id entry of the node ignored", F, "if (!idEntry.isNullOrEmpty()) return idEntry", "if (false) return idEntry"),
    ("web: an empty id entry is kept", F, "if (!idEntry.isNullOrEmpty()) return idEntry", "if (idEntry != null) return idEntry"),
    ("web: the HTML name ignored", F, "listOfNotNull(htmlId, htmlName).map", "listOfNotNull(htmlId).map"),
    ("web: the HTML id ignored", F, "listOfNotNull(htmlId, htmlName).map", "listOfNotNull(htmlName).map"),
    ("web: empty id words are kept", F, "filter { it.isNotEmpty() }.joinToString", "joinToString"),
    ("web: a long id is not cut", F, ".map { it.take(MAX_WEB_ATTRIBUTE) }", ""),
    ("web: the cut is far too long", F, "private const val MAX_WEB_ATTRIBUTE = 256", "private const val MAX_WEB_ATTRIBUTE = 100000"),
    ("web: the HTML type key is misspelt", F, 'html["type"]', 'html["typ"]'),
    ("web: the HTML id key is misspelt", F, 'html["id"]', 'html["ids"]'),
    ("web: the HTML name key is misspelt", F, 'html["name"]', 'html["names"]'),
    ("classify: the hint cc- does not block", F, 'h.startsWith("cc-") || ', ""),
    ("classify: the hint tel does not block", F, 'h.startsWith("tel") || ', ""),
    ("classify: the hint address- does not block", F, 'h.startsWith("address-") ||', "false ||"),
    ("classify: the hint one-time-code does not block", F, ', "one-time-code"', ""),
    ("classify: the hint postal-code does not block", F, ', "postal-code"', ""),
    ("classify: the hint street-address does not block", F, ', "street-address"', ""),
    ("classify: a hint is not split into tokens", F, "hints.flatMap { it.lowercase().split(' ', '\\t').filter(String::isNotEmpty) }", "hints.map { it.lowercase() }"),
    ("classify: a hint is not split at a tab", F, "split(' ', '\\t')", "split(' ')"),
    ("classify: address always vetoes", F, '&& !(it == "address" && ("email" in words || "mail" in words))', ""),
    ("classify: address never vetoes", F, '("email" in words || "mail" in words)', "true"),
    ("classify: a mail word does not free address", F, ' || "mail" in words', ""),
    ("classify: email types ignored", F, "if (emailType) return FieldRole.Username", "if (false) return FieldRole.Username"),
    ("classify: user words before password words", F, "        words.any { it in PASSWORD_TOKENS } -> FieldRole.Password\n        words.any { it in USER_TOKENS } -> FieldRole.Username\n", "        words.any { it in USER_TOKENS } -> FieldRole.Username\n        words.any { it in PASSWORD_TOKENS } -> FieldRole.Password\n"),
    ("classify: q is not a search word", F, '"search", "query", "q", "find"', '"search", "query", "find"'),
    ("classify: uid is not a user word", F, '"account", "accountid", "uid")', '"account", "accountid")'),
    ("classify: passcode is not a password word", F, '"passphrase", "passcode")', '"passphrase")'),
    # FillPlanner.kt
    ("plan: a username-only form gets nothing", L, "if (users.isEmpty() && passwords.isEmpty()) return FillPlan.None", "if (users.isEmpty() || passwords.isEmpty()) return FillPlan.None"),
    ("plan: locked and open swapped", L, "if (state !is LockState.Unlocked) {", "if (state is LockState.Unlocked) {"),
    ("plan: locked offers authentication without an account", L, "return if (lookup.hasAccount(webDomain, packageName)) FillPlan.Authenticate(", "return if (true) FillPlan.Authenticate("),
    ("plan: authentication covers every field", L, "FillPlan.Authenticate(fields.filter { it.role != FieldRole.Other }.map { it.key })", "FillPlan.Authenticate(fields.map { it.key })"),
    ("plan: open without accounts offers an empty list", L, "    if (accounts.isEmpty()) return FillPlan.None\n", ""),
    ("plan: a blank username gets an empty label", L, "it.username.ifEmpty { null }", "it.username"),
    ("plan: username fields are password fields", L, "val users = fields.filter { it.role == FieldRole.Username }", "val users = fields.filter { it.role == FieldRole.Password }"),
    ("save: the first password is taken", L, "values.lastOrNull { it.first == FieldRole.Password && it.second.isNotEmpty() }", "values.firstOrNull { it.first == FieldRole.Password && it.second.isNotEmpty() }"),
    ("save: an empty password counts", L, "it.first == FieldRole.Password && it.second.isNotEmpty() }", "it.first == FieldRole.Password }"),
    ("save: the last username is taken", L, "values.firstOrNull { it.first == FieldRole.Username }", "values.lastOrNull { it.first == FieldRole.Username }"),
    ("save: wipe clears nothing", L, "fun wipe() = password.fill('\\u0000')", "fun wipe() = Unit"),
    ("save: toString shows the password", L, 'override fun toString() = "SaveCandidate(username ${username.length} chars, password hidden)"', 'override fun toString() = "SaveCandidate($username, ${String(password)})"'),
    # Wire.kt
    ("wire: field limit one lower", W, "const val MAX_FIELD = 64 * 1024", "const val MAX_FIELD = 64 * 1024 - 1"),
    ("wire: field limit one higher", W, "const val MAX_FIELD = 64 * 1024", "const val MAX_FIELD = 64 * 1024 + 1"),
    ("wire: field limit not checked", W, "            if (len > MAX_FIELD) return null\n", ""),
    ("wire: list limit one lower", W, "private const val MAX_LIST = 256", "private const val MAX_LIST = 255"),
    ("wire: list limit one higher", W, "private const val MAX_LIST = 256", "private const val MAX_LIST = 257"),
    ("wire: history limit one lower", W, "private const val MAX_HISTORY = 10", "private const val MAX_HISTORY = 9"),
    ("wire: history limit one higher", W, "private const val MAX_HISTORY = 10", "private const val MAX_HISTORY = 11"),
    ("wire: history limit not checked", W, "        if (n > MAX_HISTORY) return null\n", ""),
    ("wire: an unknown last-fill flag in an entry", W, "else -> return null }\n        val n = r.u8() ?: return null", "else -> null }\n        val n = r.u8() ?: return null"),
    ("wire: an unknown last-fill flag in a summary", W, "else -> return null }\n            out.add(Summary(", "else -> null }\n            out.add(Summary("),
    ("wire: trailing bytes after an entry", W, "        if (!r.finished()) return null\n        return Entry(", "        return Entry("),
    ("wire: trailing bytes after a list", W, "return if (r.finished()) out else null", "return out"),
    ("wire: trailing bytes after a checkup", W, "return if (r.finished()) Checkup(reused, weak) else null", "return Checkup(reused, weak)"),
    ("wire: reason numbers swapped", W, "TOO_SHORT(0), COMMON(1), PATTERN(2);", "TOO_SHORT(1), COMMON(0), PATTERN(2);"),
    ("wire: malformed UTF-8 replaced", W, ".onMalformedInput(CodingErrorAction.REPORT)", ".onMalformedInput(CodingErrorAction.REPLACE)"),
    ("wire: equality ignores the domains", W, "domains == other.domains && ", ""),
    ("wire: equality ignores the notes", W, "notes == other.notes && ", ""),
    ("wire: equality ignores the history", W, "editedMs == other.editedMs && lastFilledMs == other.lastFilledMs && history == other.history", "editedMs == other.editedMs && lastFilledMs == other.lastFilledMs"),
    ("wire: equality ignores the last fill", W, "editedMs == other.editedMs && lastFilledMs == other.lastFilledMs && history == other.history", "editedMs == other.editedMs && history == other.history"),
    ("wire: equality ignores the password", W, "password.contentEquals(other.password) && notes == other.notes", "notes == other.notes"),
    ("wire: toString shows the password", W, 'override fun toString() = "Entry($title, ${username.length} chars of username, password hidden)"', 'override fun toString() = "Entry($title, ${String(password)})"'),
    ("wire: wipe leaves the password", W, "    fun wipe() {\n        password.fill('\\u0000')\n        history.forEach(HistoryItem::wipe)\n    }", "    fun wipe() {\n        history.forEach(HistoryItem::wipe)\n    }"),
    ("wire: wipe leaves the history", W, "    fun wipe() {\n        password.fill('\\u0000')\n        history.forEach(HistoryItem::wipe)\n    }", "    fun wipe() {\n        password.fill('\\u0000')\n    }"),
    # Reply.kt
    ("reply: unsigned status", Y, "Reply(raw[0].toInt() and 0xff, raw)", "Reply(raw[0].toInt(), raw)"),
    ("reply: an error carries a payload", Y, "fun payload(): ByteArray = if (ok) raw.copyOfRange(1, raw.size) else ByteArray(0)", "fun payload(): ByteArray = raw.copyOfRange(1, raw.size)"),
    ("reply: internal code", Y, "const val INTERNAL = 255", "const val INTERNAL = 254"),
    ("reply: a payload longer than its length is accepted", Y, "length != (payload.size - 4 - KEY_BYTES).toLong()", "length > (payload.size - 4 - KEY_BYTES).toLong()"),
    ("reply: a payload shorter than its length is accepted", Y, "length != (payload.size - 4 - KEY_BYTES).toLong()", "length < (payload.size - 4 - KEY_BYTES).toLong()"),
    ("reply: zero is a handle", Y, "value > 0 -> HandleOrCode(value, Code.OK)", "value >= 0 -> HandleOrCode(value, Code.OK)"),
    ("reply: a negative number is not an error", Y, "value < 0 -> HandleOrCode(0, (-value).toInt())", "value < -1000 -> HandleOrCode(0, (-value).toInt())"),
    # LogoPack.kt
    ("logo: a port is kept", O, "            1 -> h = h.substringBefore(':')", "            1 -> Unit"),
    ("logo: a trailing dot is kept", O, '        h = h.removeSuffix(".")\n', ""),
    ("logo: the bare suffix is looked up", O, "for (i in 0..labels.size - 2) {", "for (i in 0..labels.size - 1) {"),
    ("logo: an empty label is allowed", O, "if (labels.size < 2 || labels.any { it.isEmpty() }) return null", "if (labels.size < 2) return null"),
    ("logo: case matters", O, "var h = host.lowercase()", "var h = host"),
    ("logo: domain rows of three columns accepted", O, "if (c.size == 2 && c[0].isNotEmpty() && c[1].isNotEmpty() && !line.startsWith(\"#\"))", "if (c.size >= 2 && c[0].isNotEmpty() && c[1].isNotEmpty() && !line.startsWith(\"#\"))"),
    ("logo: no limit on the length of a name", O, "if (h.length > MAX_HOST) return null", ""),
    ("logo: the limit is 254", O, "if (h.length > MAX_HOST) return null", "if (h.length > MAX_HOST + 1) return null"),
    ("logo: the limit is 252", O, "if (h.length > MAX_HOST) return null", "if (h.length >= MAX_HOST) return null"),
    # Reply.kt wipe
    ("reply: wipe does nothing", Y, "fun wipe() = raw.fill(0)", "fun wipe() = Unit"),
    ("reply: wipe leaves the first byte", Y, "fun wipe() = raw.fill(0)", "fun wipe() = raw.fill(0, 1)"),
    # SiteText.kt
    ("site: a user name is accepted", T, "it == '@' ||", ""),
    ("site: a port is accepted", T, "|| it == ':' ||", "|| "),
    ("site: white space is accepted", T, "it.isWhitespace() || ", ""),
    ("site: a leading dot is accepted", T, 'if (t.startsWith(".") || t.endsWith(".") || t.contains("..")) return null', 'if (t.endsWith(".") || t.contains("..")) return null'),
    ("site: a trailing dot is accepted", T, 'if (t.startsWith(".") || t.endsWith(".") || t.contains("..")) return null', 'if (t.startsWith(".") || t.contains("..")) return null'),
    ("site: an empty label is accepted", T, 'if (t.startsWith(".") || t.endsWith(".") || t.contains("..")) return null', 'if (t.startsWith(".") || t.endsWith(".")) return null'),
    ("site: the length limit is gone", T, "if (t.length > MAX_HOST) return null", ""),
    ("site: the path is kept", T, "if (cut >= 0) t = t.substring(0, cut)", ""),
    ("site: the query is not a cut", T, "it == '/' || it == '?' || it == '#'", "it == '/' || it == '#'"),
    ("site: the fragment is not a cut", T, "it == '/' || it == '?' || it == '#'", "it == '/' || it == '?'"),
    ("site: the scheme is kept", T, "t = t.substring(scheme.length)", ""),
    ("site: the case is kept", T, "        t = t.lowercase()\n", ""),
    ("site: an edit drops the other sites", T, "existing.firstOrNull() == host -> existing", "existing.firstOrNull() == host -> listOf(host)"),
    ("site: an edit keeps a duplicate", T, "listOf(host) + rest.filter { it != host }", "listOf(host) + rest"),
    ("site: an emptied site keeps the first", T, "host.isEmpty() -> rest", "host.isEmpty() -> existing"),
    # ChipPolicy.kt
    ("chips: a second keyboard is trusted", C, 'setOf("com.android.inputmethod.latin")', 'setOf("com.android.inputmethod.latin", "com.example.keyboard")'),
    ("chips: any keyboard", C, "hostPackage != null && hostPackage in TRUSTED_KEYBOARDS && specCount > 0", "hostPackage != null && specCount > 0"),
    ("chips: a package that starts like a trusted one", C, "hostPackage in TRUSTED_KEYBOARDS", "TRUSTED_KEYBOARDS.any { hostPackage.startsWith(it) }"),
    ("chips: no host passes", C, "hostPackage != null && hostPackage in TRUSTED_KEYBOARDS", "(hostPackage == null || hostPackage in TRUSTED_KEYBOARDS)"),
    ("chips: no style passes", C, "specCount > 0 && maxCount > 0", "specCount >= 0 && maxCount > 0"),
    ("chips: no chip wanted passes", C, "specCount > 0 && maxCount > 0", "specCount > 0 && maxCount >= 0"),
    ("chips: the style index runs past the list", C, "minOf(datasetIndex, specCount - 1)", "minOf(datasetIndex, specCount)"),
    ("chips: always the first style", C, "minOf(datasetIndex, specCount - 1)", "0"),
    ("chips: the dataset index is the style index", C, "minOf(datasetIndex, specCount - 1)", "datasetIndex"),
    ("chips: the cap is ignored", C, "minOf(datasetCount, maxCount)", "datasetCount"),
    ("chips: the cap is the count", C, "minOf(datasetCount, maxCount)", "maxCount"),
    ("chips: the key drops leading zeros", C, '"%02x".format(it)', '"%x".format(it)'),
    ("chips: the key is upper case", C, '"%02x".format(it)', '"%02X".format(it)'),
    ("chips: a manual request is limited", C, "        if (manual) return plan\n", ""),
    ("chips: the global switch is ignored", C, "if (!suggestionsOn) return FillPlan.None", "if (false) return FillPlan.None"),
    ("chips: the generic entry is dropped", C, "if (plan !is FillPlan.Datasets) return plan", "if (plan !is FillPlan.Datasets) return FillPlan.None"),
    ("chips: only the switched-off logins are kept", C, "entryKey(it.entryId) !in chipOff", "entryKey(it.entryId) in chipOff"),
    ("chips: every login off still offers an empty list", C, "if (kept.isEmpty()) FillPlan.None else FillPlan.Datasets(kept)", "FillPlan.Datasets(kept)"),
    ("chips: the switches of the logins are ignored", C, "val kept = plan.items.filter { entryKey(it.entryId) !in chipOff }", "val kept = plan.items"),
    # PendingSave.kt
    ("pending: the scheduled wipe is not scheduled", Q, "        schedule(ttlMs + 1) { expire(p) }\n", ""),
    ("pending: the scheduled wipe is early", Q, "schedule(ttlMs + 1) { expire(p) }", "schedule(ttlMs - 1) { expire(p) }"),
    ("pending: the scheduled wipe ignores the deadline", Q, "if (held === p && now() > p.deadline) {", "if (held === p) {"),
    ("pending: the scheduled wipe hits any login", Q, "if (held === p && now() > p.deadline) {", "if (now() > p.deadline) {"),
    ("pending: the scheduled wipe does not wipe", Q, "            p.candidate.wipe()\n            held = null\n        }\n    }\n", "            held = null\n        }\n    }\n"),
    # Origin.kt
    ("origin: Vanadium is trusted without being a system app", OR, "if (pkg in BUILT_IN && facts.isSystemApp(pkg)) return true", "if (pkg in BUILT_IN) return true"),
    ("origin: any system app is trusted", OR, "if (pkg in BUILT_IN && facts.isSystemApp(pkg)) return true", "if (facts.isSystemApp(pkg)) return true"),
    ("origin: Vanadium is not trusted", OR, "if (pkg in BUILT_IN && facts.isSystemApp(pkg)) return true", ""),
    ("origin: a stored browser is trusted whatever its signer", OR, "return stored == facts.signerDigest(pkg)", "return true"),
    ("origin: a browser that is not stored is trusted", OR, "val stored = store.digestOf(pkg) ?: return false", "val stored = store.digestOf(pkg) ?: return true"),
    ("origin: a missing signer matches a stored digest of null", OR, "return stored == facts.signerDigest(pkg)", "return facts.signerDigest(pkg) == null || stored == facts.signerDigest(pkg)"),
    ("origin: a trusted browser's site is not believed", OR, "trusted(pkg) -> if (claimedDomain != null) Origin.Site(claimedDomain) else Origin.None", "trusted(pkg) -> Origin.None"),
    ("origin: a trusted browser with no site is its package", OR, "trusted(pkg) -> if (claimedDomain != null) Origin.Site(claimedDomain) else Origin.None", "trusted(pkg) -> if (claimedDomain != null) Origin.Site(claimedDomain) else Origin.App(pkg)"),
    ("origin: an untrusted browser's claim is believed", OR, "facts.isBrowser(pkg) -> Origin.None", "facts.isBrowser(pkg) && claimedDomain != null -> Origin.Site(claimedDomain)\n        facts.isBrowser(pkg) -> Origin.None"),
    ("origin: an untrusted browser is its package", OR, "facts.isBrowser(pkg) -> Origin.None", "facts.isBrowser(pkg) -> Origin.App(pkg)"),
    ("origin: an app's claim is believed", OR, "claimedDomain != null -> Origin.None", "claimedDomain != null -> Origin.Site(claimedDomain!!)"),
    ("origin: an app with web content is its package", OR, "        claimedDomain != null -> Origin.None\n        else -> Origin.App(pkg)", "        else -> Origin.App(pkg)"),
    ("origin: an app is nothing", OR, "else -> Origin.App(pkg)", "else -> Origin.None"),
    ("origin: a system app is an app", OR, "        facts.isSystemApp(pkg) -> Origin.None\n", ""),
    ("origin: only the system apps are an app", OR, "facts.isSystemApp(pkg) -> Origin.None", "!facts.isSystemApp(pkg) -> Origin.None"),
    # FillPlanner.kt, offersSave
    ("save: a form with no password is offered", L, "if (hasAccount || fields.none { it.role == FieldRole.Password }) return false", "if (hasAccount) return false"),
    ("save: a site with an account is offered", L, "if (hasAccount || fields.none { it.role == FieldRole.Password }) return false", "if (fields.none { it.role == FieldRole.Password }) return false"),
    ("save: only an app with an account is refused", L, "if (hasAccount || fields.none { it.role == FieldRole.Password }) return false", "if ((hasAccount && webDomain == null) || fields.none { it.role == FieldRole.Password }) return false"),
    ("save: a lone password in an app is offered", L, "return webDomain != null || fields.any { it.role == FieldRole.Username }", "return true"),
    ("save: a site's password step is not offered", L, "return webDomain != null || fields.any { it.role == FieldRole.Username }", "return fields.any { it.role == FieldRole.Username }"),
    ("save: an app form is never offered", L, "return webDomain != null || fields.any { it.role == FieldRole.Username }", "return webDomain != null"),
    ("origin: trust stores nothing", OR, "store.put(pkg, digest)", ""),
    ("origin: trust without a digest", OR, "val digest = facts.signerDigest(pkg) ?: return false", "val digest = facts.signerDigest(pkg) ?: \"\""),
    ("origin: the built-in browser can be trusted again", OR, "if (pkg in BUILT_IN) return false\n        val digest", "val digest"),
    ("origin: untrust removes nothing", OR, "if (pkg !in BUILT_IN) store.remove(pkg)", ""),
    ("origin: the package name of Vanadium", OR, 'const val VANADIUM = "app.vanadium.browser"', 'const val VANADIUM = "app.vanadium.browse"'),
    # PendingSave.kt
    ("pending: the time to live is ignored", Q, "if (now() > h.deadline) {", "if (false) {"),
    ("pending: the deadline is one early", Q, "if (now() > h.deadline) {", "if (now() >= h.deadline) {"),
    ("pending: the deadline is one late", Q, "if (now() > h.deadline) {", "if (now() > h.deadline + 1) {"),
    ("pending: the deadline is not set from the clock", Q, "now() + ttlMs)", "ttlMs)"),
    ("pending: an expired login is not wiped", Q, "        if (now() > h.deadline) {\n            h.candidate.wipe()\n", "        if (now() > h.deadline) {\n"),
    ("pending: take keeps the login", Q, "        val h = held ?: return null\n        held = null\n", "        val h = held ?: return null\n"),
    ("pending: a replaced login is not wiped", Q, "        held?.candidate?.wipe()\n        val p = PendingSave(", "        val p = PendingSave("),
    ("pending: clear does not wipe", Q, "    fun clear() {\n        held?.candidate?.wipe()\n", "    fun clear() {\n"),
    ("pending: clear keeps the login", Q, "        held?.candidate?.wipe()\n        held = null\n    }\n}", "        held?.candidate?.wipe()\n    }\n}"),
    ("pending: the text shows the password", Q, 'override fun toString() = "PendingSave(${if (webDomain != null) "site" else "app"}, $candidate)"', 'override fun toString() = "PendingSave(${String(candidate.password)})"'),
    # Wire.kt decodeMergeReply
    ("merge reply: a count above Int.MAX_VALUE is accepted", W, "if (n > Int.MAX_VALUE) return null", "if (false) return null"),
    ("merge reply: the largest Int count is refused", W, "if (n > Int.MAX_VALUE) return null", "if (n >= Int.MAX_VALUE) return null"),
    ("merge reply: a reply without a file is accepted", W, "return if (file.isEmpty()) null else MergeReply(counts, file)", "return MergeReply(counts, file)"),
    ("merge reply: the file is the empty rest", W, "val file = r.take(r.remaining) ?: return null", "val file = ByteArray(0)"),
    ("merge reply: the first count is skipped", W, "List(words) { val n = r.u32()", "List(words) { r.u32(); val n = r.u32()"),
]


def killed(output: str, rc: int) -> str:
    if rc == 0:
        return "survived"
    if "COMPILE ERROR" in output:
        return "does-not-compile"
    if "AssertionError" in output or "FAILED" in output or "Exception in thread" in output:
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
