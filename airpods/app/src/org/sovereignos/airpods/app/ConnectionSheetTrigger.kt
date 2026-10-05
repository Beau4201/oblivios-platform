package org.sovereignos.airpods.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.PowerManager
import android.util.Log
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.ConnectionSheetPolicy

/**
 * Shows [ConnectionSheetActivity] once per connection when the case opens and the AirPods connect (ADR-129, ADR-136).
 * The sheet is started directly, which needs the privileged `START_ACTIVITIES_FROM_BACKGROUND`, and only while the phone
 * is awake and unlocked. There is no notification and no full-screen intent (ADR-137): a locked or dark screen shows
 * nothing. No scan: the protected `ACL_CONNECTED` broadcast is what brought the session up.
 */
internal class ConnectionSheetTrigger(private val context: Context) : AirPodsStateStore.Listener {
    private val policy = ConnectionSheetPolicy()

    override fun onStateChanged(old: AirPodsUiState, new: AirPodsUiState) {
        val action = synchronized(policy) { policy.onState(new, isQuiet()) }
        when (action) {
            ConnectionSheetPolicy.Action.SHOW -> show()
            // The sheet closes itself when the link drops (ConnectionSheetActivity); nothing else is on screen.
            ConnectionSheetPolicy.Action.CANCEL, ConnectionSheetPolicy.Action.NONE -> Unit
        }
    }

    /** False while the phone is in a call or ringing. */
    private fun isQuiet(): Boolean = context.getSystemService(AudioManager::class.java).mode == AudioManager.MODE_NORMAL

    /** True while the phone is awake and not locked: the sheet can be shown straight away. */
    private fun unlockedAndInteractive(): Boolean =
        context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun show() {
        if (!unlockedAndInteractive()) {
            if (isDebuggableBuild()) Log.d(TAG, "the phone is locked or dark, no sheet")
            return
        }
        try {
            context.startActivity(Intent(context, ConnectionSheetActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            Log.w(TAG, "the sheet could not be started: " + e.javaClass.simpleName)
        }
    }

    private companion object {
        const val TAG = "AirPodsSheet"
    }
}
