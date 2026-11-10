// SPDX-License-Identifier: Apache-2.0
package org.oblivios.guardian;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import org.oblivios.guardian.application.GuardianApplicationResult;

import java.util.concurrent.ConcurrentLinkedDeque;

public final class GuardianApplication extends Application {
    private static final String TAG = "GuardianApplication";

    private GuardianComponents components;
    // Unbounded FIFO of not-yet-resolved detections: recordDetection() enqueues
    // at the tail, peekDetection() reads the head without removing it (so
    // re-reading for the same pending incident, e.g. GuardianActivity re-created
    // on rotation, keeps showing it), clearDetection() removes specifically the
    // head. A second real detection arriving before the first is resolved is
    // queued instead of overwriting a single slot (ADR-034 residual 4).
    private final ConcurrentLinkedDeque<GuardianApplicationResult> pendingDetections =
        new ConcurrentLinkedDeque<>();

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
        pendingDetections.addLast(result);
    }

    /**
     * Non-destructive read of the oldest pending detection, so opening the UI
     * more than once for the same pending incident (e.g. GuardianActivity
     * re-created on rotation) keeps showing it rather than silently dropping
     * it. Callers must pair this with {@link #clearDetection()} once the
     * incident is actually resolved (protected, undone, or explicitly
     * dismissed by the owner) -- not on every read.
     */
    public GuardianApplicationResult peekDetection() {
        GuardianApplicationResult result = pendingDetections.peekFirst();
        return result != null
            ? result
            : new GuardianApplicationResult.InvalidEvent("No pending detection");
    }

    /** True when another detection is queued behind the one currently shown. */
    public boolean hasPendingDetection() {
        return !pendingDetections.isEmpty();
    }

    /** Count of not-yet-resolved detections, for the notification's summary text. */
    public int pendingDetectionCount() {
        return pendingDetections.size();
    }

    public void clearDetection() {
        pendingDetections.pollFirst();
    }
}
