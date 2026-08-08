package org.aegisos.guardian;

import android.app.AppOpsManager;
import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.aegisos.guardian.application.GuardianApplicationResult;
import org.aegisos.guardian.watcher.BackgroundLocationWatcher;

public final class GuardianWatcherService extends Service {
    private static final String TAG = "GuardianWatcherService";

    private GuardianComponents components;
    private BackgroundLocationWatcher watcher;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
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

    // GuardianActivity is android:exported="false" (no external caller may
    // launch it, see the Task-8-final-review fix), so Guardians own
    // persistent process opening it here is the only path to the UI.
    // Verified against real source that this is allowed from the background:
    // BackgroundActivityStartController grants BAL_ALLOW_ALLOWLISTED_COMPONENT
    // to any caller whose process state is <= PROCESS_STATE_PERSISTENT_UI,
    // which android:persistent="true" satisfies.
    //
    // Called for every new detection, including ones that arrive while
    // GuardianActivity is already open for an earlier one -- FLAG_ACTIVITY_NEW_TASK
    // brings the existing instance to front rather than recreating it, and
    // GuardianActivity's own onDone callback re-checks the queue after clearing
    // an item (see GuardianActivity.kt), so it is safe to call this unconditionally.
    private void onDetection(GuardianApplication app, GuardianApplicationResult result) {
        if (!(result instanceof GuardianApplicationResult.DetectedIncident)) {
            Log.i(TAG, "onDetection: not a DetectedIncident, not opening UI");
            return;
        }
        app.recordDetection(result);
        Intent intent = new Intent(this, GuardianActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            Log.i(TAG, "onDetection: startActivity succeeded");
        } catch (ActivityNotFoundException | SecurityException failure) {
            Log.e(TAG, "Failed to open GuardianActivity for a real detection", failure);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
