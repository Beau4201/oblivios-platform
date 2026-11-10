// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.hosttest

import org.oblivios.passwords.fill.ChipPolicy
import org.oblivios.passwords.fill.DatasetPlan
import org.oblivios.passwords.fill.FillPlan

private val anna = DatasetPlan(byteArrayOf(0x0a, 0xff.toByte()), "anna", listOf("u1"), listOf("p1"))
private val bob = DatasetPlan(byteArrayOf(0x02), "bob", listOf("u1"), listOf("p1"))
private val datasets = FillPlan.Datasets(listOf(anna, bob))
private val authenticate = FillPlan.Authenticate(listOf("u1", "p1"))
private const val LATIN = "com.android.inputmethod.latin"

fun main() {
    onlyTheSystemKeyboardGetsChips()
    theStyleOfTheKeyboardServesEveryDataset()
    onlyTheFirstDatasetsGetAChip()
    theEntryKeyIsLowerCaseHex()
    aManualRequestGetsEverything()
    theGlobalSwitchGovernsAutomaticOffers()
    aLoginThatIsSwitchedOffIsLeftOutOfAutomaticOffers()
    theGenericEntryFollowsOnlyTheGlobalSwitch()
    Check.done("ChipPolicy")
}

private fun onlyTheSystemKeyboardGetsChips() {
    Check.that("the system keyboard", ChipPolicy.inlineAllowed(LATIN, 1, 5))
    Check.that("another keyboard", !ChipPolicy.inlineAllowed("com.example.keyboard", 1, 5))
    Check.that("a look-alike package", !ChipPolicy.inlineAllowed("$LATIN.evil", 1, 5))
    Check.that("a prefix of the package", !ChipPolicy.inlineAllowed("com.android.inputmethod", 1, 5))
    Check.that("no host", !ChipPolicy.inlineAllowed(null, 1, 5))
    Check.that("no style", !ChipPolicy.inlineAllowed(LATIN, 0, 5))
    Check.that("no chip wanted", !ChipPolicy.inlineAllowed(LATIN, 1, 0))
    Check.equal("the list holds one keyboard", setOf(LATIN), ChipPolicy.TRUSTED_KEYBOARDS)
}

private fun theStyleOfTheKeyboardServesEveryDataset() {
    Check.equal("first", 0, ChipPolicy.specIndex(0, 2))
    Check.equal("second", 1, ChipPolicy.specIndex(1, 2))
    Check.equal("past the last style", 1, ChipPolicy.specIndex(4, 2))
    Check.equal("a single style", 0, ChipPolicy.specIndex(3, 1))
}

private fun onlyTheFirstDatasetsGetAChip() {
    Check.equal("fewer datasets than wanted", 2, ChipPolicy.chipCount(2, 5))
    Check.equal("as many", 5, ChipPolicy.chipCount(5, 5))
    Check.equal("more datasets than wanted", 5, ChipPolicy.chipCount(9, 5))
    Check.equal("an unlimited keyboard", 9, ChipPolicy.chipCount(9, Int.MAX_VALUE))
}

private fun theEntryKeyIsLowerCaseHex() {
    Check.equal("two digits per byte, lower case", "0aff", ChipPolicy.entryKey(byteArrayOf(0x0a, 0xff.toByte())))
    Check.equal("a zero byte keeps its digits", "00", ChipPolicy.entryKey(byteArrayOf(0)))
    Check.equal("sixteen bytes make 32 digits", 32, ChipPolicy.entryKey(ByteArray(16)).length)
}

private fun aManualRequestGetsEverything() {
    val off = setOf(ChipPolicy.entryKey(anna.entryId))
    Check.that("with the global switch off", ChipPolicy.offer(datasets, true, false, off) === datasets)
    Check.that("with a login off", ChipPolicy.offer(datasets, true, true, off) === datasets)
    Check.that("the generic entry too", ChipPolicy.offer(authenticate, true, false, emptySet()) === authenticate)
}

private fun theGlobalSwitchGovernsAutomaticOffers() {
    Check.equal("on: the plan is unchanged", datasets, ChipPolicy.offer(datasets, false, true, emptySet()))
    Check.that("off: datasets", ChipPolicy.offer(datasets, false, false, emptySet()) === FillPlan.None)
    Check.that("off: nothing is left to offer", ChipPolicy.offer(FillPlan.None, false, true, emptySet()) === FillPlan.None)
}

private fun aLoginThatIsSwitchedOffIsLeftOutOfAutomaticOffers() {
    val one = ChipPolicy.offer(datasets, false, true, setOf(ChipPolicy.entryKey(anna.entryId)))
    Check.that("one login left", one is FillPlan.Datasets && one.items.size == 1 && one.items[0] === bob)
    val none = ChipPolicy.offer(datasets, false, true, setOf(ChipPolicy.entryKey(anna.entryId), ChipPolicy.entryKey(bob.entryId)))
    Check.that("every login off offers nothing", none === FillPlan.None)
    val other = ChipPolicy.offer(datasets, false, true, setOf("0000"))
    Check.that("a key of another login changes nothing", other is FillPlan.Datasets && other.items.size == 2)
}

private fun theGenericEntryFollowsOnlyTheGlobalSwitch() {
    Check.that("on, with logins off", ChipPolicy.offer(authenticate, false, true, setOf(ChipPolicy.entryKey(anna.entryId))) === authenticate)
    Check.that("off", ChipPolicy.offer(authenticate, false, false, emptySet()) === FillPlan.None)
}
