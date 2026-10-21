// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.hosttest

import org.freeholdos.passwords.lock.Clock
import org.freeholdos.passwords.lock.Environment
import org.freeholdos.passwords.lock.LockPolicy
import org.freeholdos.passwords.lock.LockState
import org.freeholdos.passwords.lock.PolicyStore
import org.freeholdos.passwords.lock.Reason
import org.freeholdos.passwords.lock.passphraseThrottleMs

private const val MINUTE = 60_000L
private const val DAY = 24 * 60 * MINUTE

private class FakeClock(var wall: Long = 1_000_000_000_000L, var elapsed: Long = 5_000L) : Clock {
    override fun nowMs() = wall
    override fun elapsedMs() = elapsed
    fun advance(ms: Long) {
        wall += ms
        elapsed += ms
    }
}

private class MemoryStore : PolicyStore {
    override var lastPassphraseMs = 0L
    override var bootCount = -1
    override var biometricFailures = 0
    override var passphraseFailures = 0
    override var lastPassphraseFailureMs = 0L
}

private class FakeEnv(var boot: Int = 7, var secret: Boolean = true, var biometrics: Boolean = true) : Environment {
    override fun bootCount() = boot
    override fun hardwareSecretPresent() = secret
    override fun biometricsValid() = biometrics
}

private class World {
    val clock = FakeClock()
    val store = MemoryStore()
    val env = FakeEnv()
    val policy = LockPolicy(clock, store, env)

    /** A passphrase unlock on this boot, as the first start or any later unlock with the passphrase does. */
    fun unlockWithPassphrase() = policy.onUnlocked(viaPassphrase = true)
}

fun main() {
    startStateNeedsThePassphraseAfterARestart()
    locksAfterOneMinuteInTheBackground()
    foregroundingBeforeTheMinuteKeepsTheVaultOpen()
    locksWhenTheDeviceLocks()
    passphraseRequiredAfterSevenDaysExactly()
    fifthBiometricFailureRequiresPassphrase()
    aBiometricUnlockClearsTheFailures()
    restartRequiresPassphrase()
    enrolmentChangeNeedsPassphraseNotUnlocked()
    missingHardwareSecretIsSecretLostNeverUnlocked()
    priorityOfTheReasons()
    throttleGrowsAndNeverWipes()
    passphraseWaitFollowsTheClock()
    aClockSetBackNeverExtendsTheSevenDays()
    theBackgroundTimerFiresOnce()
    aDeviceLockClearsTheBackgroundTimer()
    aLostSecretBeatsARestart()
    unlockingFromTheBackgroundStartsTheMinuteThen()
    anUnlockInTheForegroundDropsAStaleTimer()
    passphraseFailuresStartOverAfterASuccess()
    forgettingStartsOver()
    Check.done("LockPolicyTest")
}

private fun startStateNeedsThePassphraseAfterARestart() {
    val w = World()
    Check.equal("a fresh install has no passphrase unlock on this boot", LockState.NeedsPassphrase(Reason.Restart), w.policy.stateAtStart())
    w.unlockWithPassphrase()
    Check.equal("after a passphrase unlock on this boot the biometric path is open", LockState.Locked, w.policy.stateAtStart())
}

private fun locksAfterOneMinuteInTheBackground() {
    val w = World()
    w.unlockWithPassphrase()
    w.policy.onBackgrounded()
    w.clock.advance(MINUTE - 1)
    Check.that("59.999 s in the background: still open", w.policy.onTick() == null)
    w.clock.advance(1)
    Check.equal("60 s in the background: locked, biometrics allowed", LockState.Locked, w.policy.onTick())
}

private fun foregroundingBeforeTheMinuteKeepsTheVaultOpen() {
    val w = World()
    w.unlockWithPassphrase()
    w.policy.onBackgrounded()
    w.clock.advance(MINUTE - 1)
    Check.that("back after 59.999 s: nothing to lock", w.policy.onForegrounded() == null)
    w.clock.advance(10 * MINUTE)
    Check.that("and the timer is gone: no lock while in the foreground", w.policy.onTick() == null)
    w.policy.onBackgrounded()
    w.clock.advance(MINUTE)
    Check.equal("back after 60 s: locked", LockState.Locked, w.policy.onForegrounded())
}

private fun locksWhenTheDeviceLocks() {
    val w = World()
    w.unlockWithPassphrase()
    Check.equal("device lock locks at once, even in the foreground", LockState.Locked, w.policy.onDeviceLocked())
}

private fun passphraseRequiredAfterSevenDaysExactly() {
    val w = World()
    w.unlockWithPassphrase()
    w.clock.advance(7 * DAY - 1)
    Check.equal("7 days minus 1 ms: biometrics still allowed", LockState.Locked, w.policy.stateAtStart())
    w.clock.advance(1)
    Check.equal("exactly 7 days: the passphrase", LockState.NeedsPassphrase(Reason.Expired), w.policy.stateAtStart())
    w.unlockWithPassphrase()
    Check.equal("a new passphrase unlock starts the 7 days again", LockState.Locked, w.policy.stateAtStart())
    w.clock.advance(3 * DAY)
    w.policy.onUnlocked(viaPassphrase = false)
    w.clock.advance(4 * DAY - 1)
    Check.equal("a biometric unlock does not extend the 7 days", LockState.Locked, w.policy.stateAtStart())
    w.clock.advance(1)
    Check.equal("so the passphrase is due 7 days after the last passphrase", LockState.NeedsPassphrase(Reason.Expired), w.policy.stateAtStart())
}

private fun fifthBiometricFailureRequiresPassphrase() {
    val w = World()
    w.unlockWithPassphrase()
    repeat(4) { Check.that("failure ${it + 1} of 5 does not lock out biometrics", w.policy.onBiometricFailed() == null) }
    Check.equal("the fifth does", LockState.NeedsPassphrase(Reason.FiveFailures), w.policy.onBiometricFailed())
    Check.equal("and it stays so after a restart of the process", LockState.NeedsPassphrase(Reason.FiveFailures), w.policy.stateAtStart())
    w.unlockWithPassphrase()
    Check.equal("a passphrase unlock clears the count", LockState.Locked, w.policy.stateAtStart())
}

private fun aBiometricUnlockClearsTheFailures() {
    val w = World()
    w.unlockWithPassphrase()
    repeat(4) { w.policy.onBiometricFailed() }
    w.policy.onUnlocked(viaPassphrase = false)
    Check.that("after a success the count starts at zero", w.policy.onBiometricFailed() == null && w.store.biometricFailures == 1)
}

private fun restartRequiresPassphrase() {
    val w = World()
    w.unlockWithPassphrase()
    w.env.boot = 8
    Check.equal("a different boot count means the phone restarted", LockState.NeedsPassphrase(Reason.Restart), w.policy.stateAtStart())
    w.unlockWithPassphrase()
    Check.equal("the passphrase on the new boot opens biometrics again", LockState.Locked, w.policy.stateAtStart())
}

private fun enrolmentChangeNeedsPassphraseNotUnlocked() {
    val w = World()
    w.unlockWithPassphrase()
    w.env.biometrics = false
    Check.equal("changed biometrics: the passphrase", LockState.NeedsPassphrase(Reason.BiometricsChanged), w.policy.stateAtStart())
    w.policy.onBackgrounded()
    w.clock.advance(MINUTE)
    Check.equal("a lock never lands on a biometric state then", LockState.NeedsPassphrase(Reason.BiometricsChanged), w.policy.onTick())
}

private fun missingHardwareSecretIsSecretLostNeverUnlocked() {
    val w = World()
    w.unlockWithPassphrase()
    w.env.secret = false
    Check.equal("a lost secret beats every other state", LockState.SecretLost, w.policy.stateAtStart())
    w.policy.onBackgrounded()
    w.clock.advance(MINUTE)
    Check.equal("also when the background timer locks", LockState.SecretLost, w.policy.onTick())
    Check.equal("also when the device locks", LockState.SecretLost, w.policy.onDeviceLocked())
    w.env.biometrics = false
    Check.equal("also with changed biometrics", LockState.SecretLost, w.policy.stateAtStart())
}

private fun priorityOfTheReasons() {
    val w = World()
    w.unlockWithPassphrase()
    repeat(5) { w.policy.onBiometricFailed() }
    w.env.biometrics = false
    Check.equal("changed biometrics before five failures", LockState.NeedsPassphrase(Reason.BiometricsChanged), w.policy.stateAtStart())
    w.env.boot = 9
    Check.equal("a restart before changed biometrics", LockState.NeedsPassphrase(Reason.Restart), w.policy.stateAtStart())
}

private fun throttleGrowsAndNeverWipes() {
    Check.equal("no delay for the first three failures", listOf(0L, 0L, 0L, 0L), (0..3).map { passphraseThrottleMs(it) })
    Check.equal("then 1 s, 2 s, 4 s, 8 s", listOf(1_000L, 2_000L, 4_000L, 8_000L), (4..7).map { passphraseThrottleMs(it) })
    var previous = 0L
    for (n in 0..10_000) {
        val t = passphraseThrottleMs(n)
        Check.that("never shrinks (failure $n)", t >= previous)
        Check.that("never above five minutes (failure $n)", t <= 300_000L)
        previous = t
    }
    Check.equal("the cap is five minutes", 300_000L, passphraseThrottleMs(1_000_000))
    Check.equal("a negative count is no delay", 0L, passphraseThrottleMs(-5))
    // There is deliberately no wipe: the policy has no operation that deletes anything, whatever the count.
    val w = World()
    w.unlockWithPassphrase()
    repeat(100) { w.policy.onPassphraseFailed() }
    Check.equal("after 100 failures the vault is still only throttled", 100, w.store.passphraseFailures)
    Check.equal("and the biometric state is untouched by passphrase failures", LockState.Locked, w.policy.stateAtStart())
}

private fun passphraseWaitFollowsTheClock() {
    val w = World()
    w.unlockWithPassphrase()
    repeat(3) { w.policy.onPassphraseFailed() }
    Check.equal("three failures: no wait", 0L, w.policy.passphraseWaitMs())
    w.policy.onPassphraseFailed()
    Check.equal("the fourth: one second from now", 1_000L, w.policy.passphraseWaitMs())
    w.clock.advance(400)
    Check.equal("part of it passes", 600L, w.policy.passphraseWaitMs())
    w.clock.advance(600)
    Check.equal("and it ends", 0L, w.policy.passphraseWaitMs())
    w.clock.advance(5_000)
    Check.equal("a long time later it is still 0, never negative", 0L, w.policy.passphraseWaitMs())
    w.policy.onPassphraseFailed()
    w.clock.wall -= 10_000
    Check.equal("a clock set back does not shorten the wait", 2_000L, w.policy.passphraseWaitMs())
    w.unlockWithPassphrase()
    Check.equal("a successful unlock ends the wait", 0L, w.policy.passphraseWaitMs())
}

private fun aClockSetBackNeverExtendsTheSevenDays() {
    val w = World()
    w.unlockWithPassphrase()
    w.clock.wall -= 1
    Check.equal("a wall clock before the last unlock cannot be trusted", LockState.NeedsPassphrase(Reason.Expired), w.policy.stateAtStart())
}

private fun theBackgroundTimerFiresOnce() {
    val w = World()
    w.unlockWithPassphrase()
    w.policy.onBackgrounded()
    w.clock.advance(MINUTE)
    Check.equal("the first tick after a minute locks", LockState.Locked, w.policy.onTick())
    w.clock.advance(MINUTE)
    Check.that("the next tick has nothing left to do", w.policy.onTick() == null)
    Check.that("and neither has coming back", w.policy.onForegrounded() == null)
}

private fun aDeviceLockClearsTheBackgroundTimer() {
    val w = World()
    w.unlockWithPassphrase()
    w.policy.onBackgrounded()
    w.policy.onDeviceLocked()
    w.clock.advance(5 * MINUTE)
    Check.that("a locked vault has no background timer left to fire", w.policy.onTick() == null)
}

private fun aLostSecretBeatsARestart() {
    val w = World()
    w.unlockWithPassphrase()
    w.env.boot = 8
    w.env.secret = false
    Check.equal("a lost secret is reported before a restart is", LockState.SecretLost, w.policy.stateAtStart())
}

private fun unlockingFromTheBackgroundStartsTheMinuteThen() {
    val w = World()
    w.policy.onUnlocked(viaPassphrase = true, inForeground = false)
    w.clock.advance(MINUTE - 1)
    Check.that("opened while in the background: still open after 59.999 s", w.policy.onTick() == null)
    w.clock.advance(1)
    Check.equal("and locked after the minute", LockState.Locked, w.policy.onTick())
}

private fun anUnlockInTheForegroundDropsAStaleTimer() {
    val w = World()
    w.unlockWithPassphrase()
    w.policy.onBackgrounded()
    w.clock.advance(30_000)
    w.unlockWithPassphrase()
    w.clock.advance(MINUTE)
    Check.that("an unlock while the app is in front clears the old background timer", w.policy.onTick() == null)
}

private fun passphraseFailuresStartOverAfterASuccess() {
    val w = World()
    w.unlockWithPassphrase()
    repeat(6) { w.policy.onPassphraseFailed() }
    w.unlockWithPassphrase()
    Check.equal("the count is back to zero", 0, w.store.passphraseFailures)
    w.policy.onPassphraseFailed()
    Check.equal("so the next failure is the first", 0L, w.policy.passphraseWaitMs())
}

private fun forgettingStartsOver() {
    val w = World()
    w.unlockWithPassphrase()
    repeat(3) { w.policy.onBiometricFailed() }
    repeat(5) { w.policy.onPassphraseFailed() }
    w.policy.forget()
    Check.equal("a forgotten vault has no passphrase unlock on this boot", LockState.NeedsPassphrase(Reason.Restart), w.policy.stateAtStart())
    Check.equal("no wait", 0L, w.policy.passphraseWaitMs())
    Check.equal("no biometric failures", 0, w.store.biometricFailures)
    Check.equal("no passphrase failures", 0, w.store.passphraseFailures)
    Check.equal("no passphrase time", 0L, w.store.lastPassphraseMs)
    Check.equal("no boot count", -1, w.store.bootCount)
    Check.equal("no failure time", 0L, w.store.lastPassphraseFailureMs)
}
