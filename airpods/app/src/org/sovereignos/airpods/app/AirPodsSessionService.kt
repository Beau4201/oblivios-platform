package org.sovereignos.airpods.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import org.sovereignos.airpods.R

/**
 * Keeps the process alive while the recognised AirPods are connected, so the session keeps
 * running and Settings always finds a current state. It does nothing else: the session itself is
 * owned by AirPodsSessionHost. Not exported; started only by our own receiver and process start.
 */
class AirPodsSessionService : Service() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.airpods_notification_channel), NotificationManager.IMPORTANCE_MIN),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: RuntimeException) {
            // For example BLUETOOTH_CONNECT is not granted: no session, no crash.
            Log.w(TAG, "startForeground refused: " + e.javaClass.simpleName)
            stopSelf()
            return START_NOT_STICKY
        }
        AirPodsSessionHost.onServiceStarted(this)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AirPodsSessionHost.onServiceStopped(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_airpods_ear)
            .setContentTitle(getString(R.string.airpods_notification_title))
            .setOngoing(true)
            .build()

    private companion object {
        const val TAG = "AirPodsService"
        const val CHANNEL = "airpods_session"
        const val NOTIFICATION_ID = 1
    }
}
