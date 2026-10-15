// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.airpods.app

import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.GroupSectionDividerMixin
import org.freeholdos.airpods.R
import org.freeholdos.airpods.ui.AirPodsUiState

/**
 * The header with the three rings, as a non-selectable preference at the top of the page. It is a section divider for
 * Settings' group adapter, so it lies on the page colour like the header of the device details and gets no card.
 */
class RingsPreference(context: Context) : Preference(context), GroupSectionDividerMixin {
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
