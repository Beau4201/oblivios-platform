package org.sovereignos.airpods.app

import android.content.Context
import org.sovereignos.airpods.ui.AirPodsUiState

/** Keeps the last reported case battery for the next session; the value comes from the state store. */
internal class CaseBatteryRecorder(private val context: Context) : AirPodsStateStore.Listener {
    override fun onStateChanged(old: AirPodsUiState, new: AirPodsUiState) {
        val address = new.address ?: return
        if (new.casePercent < 0) return
        if (new.casePercent == old.casePercent && new.caseCharging == old.caseCharging) return
        CaseBatteryStore.write(context, address, new.casePercent, new.caseCharging)
    }
}
