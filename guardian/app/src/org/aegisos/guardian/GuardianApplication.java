package org.aegisos.guardian;

import android.app.Application;

import org.aegisos.guardian.application.GuardianApplicationResult;

public final class GuardianApplication extends Application {
    private GuardianComponents components;
    private volatile GuardianApplicationResult lastDetection;

    @Override
    public void onCreate() {
        super.onCreate();
        components = GuardianComponents.create(this);
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
