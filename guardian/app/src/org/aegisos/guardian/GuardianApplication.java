package org.aegisos.guardian;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import org.aegisos.guardian.application.GuardianApplicationResult;

public final class GuardianApplication extends Application {
    private static final String TAG = "GuardianApplication";

    private GuardianComponents components;
    private volatile GuardianApplicationResult lastDetection;

    @Override
    public void onCreate() {
        super.onCreate();
        components = GuardianComponents.create(this);
        try {
            startService(new Intent(this, GuardianWatcherService.class));
        } catch (IllegalStateException | SecurityException failure) {
            // android:persistent="true" means a throw here would crash-loop a
            // system process during boot; log and continue rather than crash.
            Log.e(TAG, "Failed to start GuardianWatcherService", failure);
        }
    }

    public GuardianComponents components() {
        return components;
    }

    public void recordDetection(GuardianApplicationResult result) {
        lastDetection = result;
    }

    public GuardianApplicationResult consumeDetection() {
        GuardianApplicationResult result = lastDetection;
        lastDetection = null;
        return result != null
            ? result
            : new GuardianApplicationResult.InvalidEvent("No pending detection");
    }
}
