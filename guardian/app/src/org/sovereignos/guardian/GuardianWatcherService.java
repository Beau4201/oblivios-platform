// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.guardian;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.sovereignos.guardian.application.GuardianApplicationResult;
import org.sovereignos.guardian.ui.GuardianRoute;
import org.sovereignos.guardian.watcher.BackgroundLocationWatcher;

import static org.sovereignos.guardian.GuardianHomeActivityKt.EXTRA_ROUTE;

/**
 * Watches AppOps for background access and, on a real detection, posts a
 * notification rather than forcing GuardianHomeActivity open -- the owner
 * decides whether and when to look, like any other Android app's
 * notification. ADR-034's background-activity-start exemption
 * (GuardianWatcherService force-launching the UI from this persistent
 * process) is retired along with the forced open it existed to justify.
 */
public final class GuardianWatcherService extends Service {
    private static final String TAG = "GuardianWatcherService";
    private static final String CHANNEL_ID = "guardian_detections";
    private static final int DETECTION_NOTIFICATION_ID = 1;

    private GuardianComponents components;
    private BackgroundLocationWatcher watcher;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        createNotificationChannel();
        GuardianApplication app = (GuardianApplication) getApplication();
        components = app.components();
        watcher = new BackgroundLocationWatcher(
            (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE),
            (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE),
            getMainExecutor(),
            event -> {
                GuardianApplicationResult result = components.applicationService().ingest(event);
                onDetection(app, result);
            });
        watcher.start();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "Guardian detections", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Guardian noticed something and wants you to take a look.");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    // Posts (or updates in place, if a prior detection is still unresolved) a
    // notification instead of starting GuardianHomeActivity directly. Tapping
    // it opens GuardianHomeActivity routed straight to incident review.
    // Called for every new detection, including ones that arrive while an
    // earlier one is still unresolved -- FLAG_UPDATE_CURRENT on the
    // PendingIntent and reusing DETECTION_NOTIFICATION_ID means a second
    // detection updates the same notification's summary text (via
    // pendingDetectionCount()) instead of posting a new one.
    private void onDetection(GuardianApplication app, GuardianApplicationResult result) {
        if (!(result instanceof GuardianApplicationResult.DetectedIncident)) {
            Log.i(TAG, "onDetection: not a DetectedIncident, not notifying");
            return;
        }
        app.recordDetection(result);

        Intent intent = new Intent(this, GuardianHomeActivity.class);
        intent.putExtra(EXTRA_ROUTE, GuardianRoute.INCIDENT_REVIEW.name());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        int pendingCount = app.pendingDetectionCount();
        String contentText = pendingCount > 1
            ? pendingCount + " apps flagged -- tap to review"
            : "Tap to see what Guardian found";

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Guardian noticed something")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(DETECTION_NOTIFICATION_ID, notification);
        Log.i(TAG, "onDetection: notification posted, pendingCount=" + pendingCount);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
