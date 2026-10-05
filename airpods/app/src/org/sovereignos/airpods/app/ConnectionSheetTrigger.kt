package org.sovereignos.airpods.app

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.media.AudioManager
import android.util.Log
import org.sovereignos.airpods.R
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.ConnectionSheetPolicy

/**
 * Shows [ConnectionSheetActivity] once per connection when the case opens and the AirPods connect (ADR-129, ADR-136).
 * On an unlocked, awake phone the sheet is started directly, which needs the privileged
 * `START_ACTIVITIES_FROM_BACKGROUND`; on a locked or dark screen it is the full-screen intent of the notification
 * (`USE_FULL_SCREEN_INTENT`). The notification (`POST_NOTIFICATIONS`) is also the record: tapping it opens Settings'
 * device details. No scan: the protected `ACL_CONNECTED` broadcast is what brought the session up. The notification
 * text is generic (no battery value on the lock screen).
 */
internal class ConnectionSheetTrigger(private val context: Context) : AirPodsStateStore.Listener {
    private val policy = ConnectionSheetPolicy()

    override fun onStateChanged(old: AirPodsUiState, new: AirPodsUiState) {
        val action = synchronized(policy) { policy.onState(new, isQuiet()) }
        when (action) {
            ConnectionSheetPolicy.Action.SHOW -> show(new.address)
            ConnectionSheetPolicy.Action.CANCEL -> manager().cancel(NOTIFICATION_ID)
            ConnectionSheetPolicy.Action.NONE -> Unit
        }
    }

    private fun manager(): NotificationManager = context.getSystemService(NotificationManager::class.java)

    /** False while the phone is in a call or ringing. */
    private fun isQuiet(): Boolean = context.getSystemService(AudioManager::class.java).mode == AudioManager.MODE_NORMAL

    /** True while the phone is awake and not locked: the sheet can be shown straight away. */
    private fun unlockedAndInteractive(): Boolean =
        context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun startSheet() {
        try {
            context.startActivity(Intent(context, ConnectionSheetActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            Log.w(TAG, "the sheet could not be started: " + e.javaClass.simpleName)
        }
    }

    private fun show(address: String?) {
        val direct = unlockedAndInteractive()
        if (direct) startSheet()
        val manager = manager()
        if (!manager.areNotificationsEnabled()) {
            if (isDebuggableBuild()) Log.d(TAG, "notifications are off, no notification")
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.airpods_sheet_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, ConnectionSheetActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_airpods_ear)
            .setContentTitle(context.getString(R.string.airpods_sheet_title))
            .setContentText(context.getString(R.string.airpods_sheet_notification_text))
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(if (address != null) deviceDetails(address) else open)
            .apply { if (!direct) setFullScreenIntent(open, true) }
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Tapping the notification opens Settings' device details of the AirPods, like the notification of Settings itself
     * (BluetoothKeyMissingReceiver): the action and the argument bundle are the ones Settings defines. The sheet stays
     * the full-screen intent.
     */
    private fun deviceDetails(address: String): PendingIntent {
        val args = Bundle().apply { putString("device_address", address) }
        val intent = Intent(DEVICE_DETAILS_ACTION)
            .setPackage("com.android.settings")
            .putExtra(":settings:show_fragment_args", args)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val DEVICE_DETAILS_ACTION = "com.android.settings.BLUETOOTH_DEVICE_DETAIL_SETTINGS"
        const val TAG = "AirPodsSheet"
        const val CHANNEL = "airpods_popup"
        const val NOTIFICATION_ID = 2
        const val TIMEOUT_MS = 15_000L
    }
}
