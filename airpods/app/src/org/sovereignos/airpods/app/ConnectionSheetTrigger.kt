package org.sovereignos.airpods.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import org.sovereignos.airpods.R
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.ConnectionSheetPolicy

/**
 * Opens [ConnectionSheetActivity] once per connection through a notification with a full-screen intent
 * (ADR-129): no scan, no background activity start permission, the protected `ACL_CONNECTED` broadcast
 * is what brought the session up. Needs `POST_NOTIFICATIONS` and `USE_FULL_SCREEN_INTENT`; without the
 * notification permission nothing is shown and nothing else changes. The notification text is generic
 * (no battery value on the lock screen).
 */
internal class ConnectionSheetTrigger(private val context: Context) : AirPodsStateStore.Listener {
    private val policy = ConnectionSheetPolicy()

    override fun onStateChanged(old: AirPodsUiState, new: AirPodsUiState) {
        val action = synchronized(policy) { policy.onState(new, isQuiet()) }
        when (action) {
            ConnectionSheetPolicy.Action.SHOW -> show()
            ConnectionSheetPolicy.Action.CANCEL -> manager().cancel(NOTIFICATION_ID)
            ConnectionSheetPolicy.Action.NONE -> Unit
        }
    }

    private fun manager(): NotificationManager = context.getSystemService(NotificationManager::class.java)

    /** False while the phone is in a call or ringing. */
    private fun isQuiet(): Boolean = context.getSystemService(AudioManager::class.java).mode == AudioManager.MODE_NORMAL

    private fun show() {
        val manager = manager()
        if (!manager.areNotificationsEnabled()) {
            if (isDebuggableBuild()) Log.d(TAG, "notifications are off, no sheet")
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
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val TAG = "AirPodsSheet"
        const val CHANNEL = "airpods_popup"
        const val NOTIFICATION_ID = 2
        const val TIMEOUT_MS = 15_000L
    }
}
