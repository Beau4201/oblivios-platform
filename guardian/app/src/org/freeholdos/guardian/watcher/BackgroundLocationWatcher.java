// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.guardian.watcher;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class BackgroundLocationWatcher {

    private final AppOpsManager appOps;
    private final ActivityManager activityManager;
    private final Executor callbackExecutor;
    private final Consumer<Map<String, Object>> onEvent;

    public BackgroundLocationWatcher(
            AppOpsManager appOps, ActivityManager activityManager, Executor callbackExecutor,
            Consumer<Map<String, Object>> onEvent) {
        this.appOps = appOps;
        this.activityManager = activityManager;
        this.callbackExecutor = callbackExecutor;
        this.onEvent = onEvent;
    }

    public void start() {
        // No-Executor overload: AppOpsManager guarantees main-thread delivery for
        // this specific overload (confirmed against real AppOpsManager source,
        // ADR-034 residual 2). onAccess() asserts this below so a future change
        // to the Executor overload fails loudly instead of silently racing the
        // in-memory/audit stores this callback feeds.
        appOps.startWatchingNoted(new String[] {
                AppOpsManager.OPSTR_FINE_LOCATION,
                AppOpsManager.OPSTR_GPS,
                AppOpsManager.OPSTR_MONITOR_LOCATION,
                AppOpsManager.OPSTR_COARSE_LOCATION,
            },
            (op, uid, packageName, attributionTag, flags, result) -> onAccess(op, uid, packageName));

        // GPS/MONITOR_LOCATION/COARSE_LOCATION are duration-tracked (startOp/
        // finishOp) ops on real hardware: AppOpsService's AttributedOp#started()
        // dispatches them exclusively through the active-callback path, never
        // through the noted path above. Confirmed for GPS/MONITOR_LOCATION by
        // reading AppOpsService.java/AttributedOp.java directly (ADR-036); for
        // COARSE_LOCATION by reading LocationPermissions.java (maps the coarse
        // permission level to OP_COARSE_LOCATION) and SystemAppOpsHelper.java
        // (dispatches that same op through the generic startOpNoThrow/finishOp
        // pair used for every location op, GPS included) -- the same duration-
        // tracked mechanism, not the instantaneous noted path. Both watchers are
        // required to cover how these ops are actually recorded.
        appOps.startWatchingActive(new String[] {
                AppOpsManager.OPSTR_GPS,
                AppOpsManager.OPSTR_MONITOR_LOCATION,
                AppOpsManager.OPSTR_COARSE_LOCATION,
            },
            callbackExecutor,
            (op, uid, packageName, active) -> {
                if (active) {
                    onAccess(op, uid, packageName);
                }
            });
    }

    private void onAccess(String op, int uid, String packageName) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException(
                "BackgroundLocationWatcher.onAccess() must run on the main thread -- "
                + "startWatchingNoted's no-Executor overload guarantees this today; "
                + "if that registration ever gains an Executor argument, this assertion "
                + "is what will catch the resulting race instead of it failing silently.");
        }
        // startWatchingNoted/startWatchingActive are registered system-wide, not scoped to
        // this process's own Android user -- confirmed via real source read, not assumed. On
        // a FreeholdOS multi-user device (e.g. a Duress Unlock decoy session, or any two ordinary
        // users) this Guardian instance would otherwise be notified about, and ingest, another
        // user's own app activity into this user's own overview store (which has no userId
        // field to filter on downstream). Found during an exhaustive audit of decoy
        // identity-leak surfaces, but the bug itself is general to any multi-user pairing, not
        // decoy-specific.
        if (!UserHandle.getUserHandleForUid(uid).equals(Process.myUserHandle())) {
            return;
        }
        if (!isBackground(uid)) {
            return;
        }
        Map<String, Object> event = new HashMap<>();
        event.put("schemaVersion", 1);
        event.put("eventId", UUID.randomUUID().toString());
        event.put("occurredAt", Instant.now().toString());
        event.put("packageName", packageName);
        event.put("uid", uid);
        event.put("eventType", "BACKGROUND_LOCATION");
        event.put("resource", isCoarse(op) ? "LOCATION_APPROXIMATE" : "LOCATION_PRECISE");
        event.put("foregroundState", "BACKGROUND");
        event.put("policyState", "ALLOWED");
        event.put("networkCategory", "NONE");
        event.put("bytesSent", 0L);
        event.put("bytesReceived", 0L);
        onEvent.accept(event);
    }

    private boolean isCoarse(String op) {
        return AppOpsManager.OPSTR_COARSE_LOCATION.equals(op);
    }

    private boolean isBackground(int uid) {
        return activityManager.getUidImportance(uid) > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    }
}
