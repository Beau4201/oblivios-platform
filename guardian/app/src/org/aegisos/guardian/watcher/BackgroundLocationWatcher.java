package org.aegisos.guardian.watcher;

import android.app.ActivityManager;
import android.app.AppOpsManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class BackgroundLocationWatcher {
    private final AppOpsManager appOps;
    private final ActivityManager activityManager;
    private final Consumer<Map<String, Object>> onEvent;

    public BackgroundLocationWatcher(
            AppOpsManager appOps, ActivityManager activityManager,
            Consumer<Map<String, Object>> onEvent) {
        this.appOps = appOps;
        this.activityManager = activityManager;
        this.onEvent = onEvent;
    }

    public void start() {
        appOps.startWatchingNoted(new String[] {AppOpsManager.OPSTR_FINE_LOCATION},
            (op, uid, packageName, attributionTag, flags, result) ->
                onNoted(op, uid, packageName));
    }

    private void onNoted(String op, int uid, String packageName) {
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
        event.put("resource", "LOCATION_PRECISE");
        event.put("foregroundState", "BACKGROUND");
        event.put("policyState", "ALLOWED");
        event.put("networkCategory", "NONE");
        event.put("bytesSent", 0L);
        event.put("bytesReceived", 0L);
        onEvent.accept(event);
    }

    private boolean isBackground(int uid) {
        return activityManager.getUidImportance(uid) > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    }
}
