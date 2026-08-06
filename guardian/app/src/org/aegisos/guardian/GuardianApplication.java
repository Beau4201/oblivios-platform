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

    /**
     * Non-destructive read, so opening the UI more than once for the same
     * pending incident (e.g. GuardianActivity re-created on rotation) keeps
     * showing it rather than silently dropping it. Callers must pair this
     * with {@link #clearDetection()} once the incident is actually resolved
     * (protected, undone, or explicitly dismissed by the owner) -- not on
     * every read.
     */
    public GuardianApplicationResult peekDetection() {
        GuardianApplicationResult result = lastDetection;
        return result != null
            ? result
            : new GuardianApplicationResult.InvalidEvent("No pending detection");
    }

    public void clearDetection() {
        lastDetection = null;
    }
}
