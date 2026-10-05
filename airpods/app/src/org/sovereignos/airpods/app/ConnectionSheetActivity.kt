package org.sovereignos.airpods.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import com.android.settingslib.widget.theme.R as ThemeR
import org.sovereignos.airpods.R
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode

/**
 * The connection sheet: the three rings with their battery levels, the current listening mode, Done and
 * Settings. Started only on an unlocked phone, not shown over the lock screen, not exported, closed when the
 * link drops. Settings opens our own page and asks to unlock first if the phone locked meanwhile.
 */
class ConnectionSheetActivity : Activity() {
    private val storeListener = AirPodsStateStore.Listener { _, new -> runOnUiThread { bind(new) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.airpods_sheet)
        findViewById<View>(R.id.sheet_root).setOnClickListener { finish() }
        findViewById<View>(R.id.sheet_card).setOnClickListener { }
        findViewById<Button>(R.id.sheet_settings).setOnClickListener { openSettings() }
        val surface = getColor(ThemeR.color.settingslib_materialColorSurfaceContainerHigh)
        for (id in intArrayOf(R.id.ring_left, R.id.ring_case, R.id.ring_right)) {
            findViewById<BatteryRingView>(id).setGapColor(surface)
        }
        bind(AirPodsStateStore.state)
    }

    override fun onStart() {
        super.onStart()
        AirPodsStateStore.addListener(storeListener)
        bind(AirPodsStateStore.state)
    }

    override fun onStop() {
        AirPodsStateStore.removeListener(storeListener)
        super.onStop()
    }

    private fun bind(state: AirPodsUiState) {
        if (!state.isConnected) {
            finish()
            return
        }
        findViewById<BatteryRingView>(R.id.ring_left).set(BatteryRingView.Kind.LEFT, state.leftPercent, state.leftCharging)
        findViewById<BatteryRingView>(R.id.ring_case).set(BatteryRingView.Kind.CASE, state.casePercent, state.caseCharging)
        findViewById<BatteryRingView>(R.id.ring_right).set(BatteryRingView.Kind.RIGHT, state.rightPercent, state.rightCharging)
        findViewById<TextView>(R.id.ring_left_label).text = label(R.string.airpods_left, state.leftPercent)
        findViewById<TextView>(R.id.ring_case_label).text = label(R.string.airpods_case, state.casePercent)
        findViewById<TextView>(R.id.ring_right_label).text = label(R.string.airpods_right, state.rightPercent)
        val mode = findViewById<TextView>(R.id.sheet_mode)
        val text = modeText(state.listeningMode)
        if (text == null) {
            mode.visibility = View.GONE
        } else {
            mode.visibility = View.VISIBLE
            mode.setText(text)
        }
    }

    /** The ring's name with its battery level, or the name alone while the level is not known (percent -1). */
    private fun label(@StringRes name: Int, percent: Int): String =
        if (percent < 0) getString(name) else getString(R.string.airpods_ring_label, getString(name), percent)

    @StringRes
    private fun modeText(mode: ListeningMode?): Int? = when (mode) {
        ListeningMode.OFF -> R.string.airpods_sheet_mode_off
        ListeningMode.TRANSPARENCY -> R.string.airpods_sheet_mode_transparency
        ListeningMode.ANC -> R.string.airpods_sheet_mode_noise_cancel
        ListeningMode.ADAPTIVE -> R.string.airpods_sheet_mode_adaptive
        null -> null
    }

    private fun openSettings() {
        val open = {
            startActivity(
                Intent().setClassName(packageName, AirPodsContract.ALL_SETTINGS_ACTIVITY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            finish()
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    open()
                }
            })
        } else {
            open()
        }
    }
}
