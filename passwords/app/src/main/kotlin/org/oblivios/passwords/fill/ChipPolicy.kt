// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.fill

/**
 * The chips above the keyboard (ADR-191), without any Android class so that the host checks can run it. The system draws
 * a chip in another process from the content this app hands over; what the keyboard learns from the chips is their
 * number, their kind and whether there are any, so they are offered only to the keyboards listed here.
 */
object ChipPolicy {
    /**
     * Keyboards that may be offered chips: the system keyboard, which has no network permission and reads no chip. Any
     * other keyboard gets the system list instead, which the system draws and no keyboard sees.
     */
    val TRUSTED_KEYBOARDS: Set<String> = setOf("com.android.inputmethod.latin")

    /** Chips are built only for a trusted keyboard that sent a style and wants at least one chip. */
    fun inlineAllowed(hostPackage: String?, specCount: Int, maxCount: Int): Boolean =
        hostPackage != null && hostPackage in TRUSTED_KEYBOARDS && specCount > 0 && maxCount > 0

    /** The style of the keyboard for the chip of the dataset at [datasetIndex]; the last style serves the rest. */
    fun specIndex(datasetIndex: Int, specCount: Int): Int = minOf(datasetIndex, specCount - 1)

    /** How many of [datasetCount] datasets get a chip: the keyboard names the most it wants. */
    fun chipCount(datasetCount: Int, maxCount: Int): Int = minOf(datasetCount, maxCount)

    /** The key of an entry in the preferences: 32 lower-case hex digits for the 16 bytes of its id. */
    fun entryKey(id: ByteArray): String = id.joinToString("") { "%02x".format(it) }

    /**
     * What an automatic request offers. The switch "Suggestions above the keyboard" and the switch of a login govern
     * what appears without being asked; a manual request (Autofill in the menu of the field) always gets the plan. A
     * locked vault cannot read the switches of a login, so its generic entry follows the global switch only.
     */
    fun offer(plan: FillPlan, manual: Boolean, suggestionsOn: Boolean, chipOff: Set<String>): FillPlan {
        if (manual) return plan
        if (!suggestionsOn) return FillPlan.None
        if (plan !is FillPlan.Datasets) return plan
        val kept = plan.items.filter { entryKey(it.entryId) !in chipOff }
        return if (kept.isEmpty()) FillPlan.None else FillPlan.Datasets(kept)
    }
}
