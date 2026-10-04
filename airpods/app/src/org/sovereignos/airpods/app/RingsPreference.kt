package org.sovereignos.airpods.app

import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.sovereignos.airpods.R
import org.sovereignos.airpods.ui.AirPodsUiState

/** The header with the three rings, as a non-selectable preference at the top of the page. */
class RingsPreference(context: Context) : Preference(context) {
    private var state: AirPodsUiState = AirPodsUiState.DISCONNECTED

    init {
        layoutResource = R.layout.airpods_rings
        isSelectable = false
        key = KEY
    }

    fun show(next: AirPodsUiState) {
        if (next == state) return
        state = next
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        (holder.findViewById(R.id.ring_left) as BatteryRingView)
            .set(BatteryRingView.Kind.LEFT, state.leftPercent, state.leftCharging)
        (holder.findViewById(R.id.ring_case) as BatteryRingView)
            .set(BatteryRingView.Kind.CASE, state.casePercent, state.caseCharging)
        (holder.findViewById(R.id.ring_right) as BatteryRingView)
            .set(BatteryRingView.Kind.RIGHT, state.rightPercent, state.rightCharging)
    }

    private companion object {
        const val KEY = "rings"
    }
}
