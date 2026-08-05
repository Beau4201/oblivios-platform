package org.aegisos.guardian;

import android.app.AppOpsManager;
import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import org.aegisos.guardian.watcher.BackgroundLocationWatcher;

public final class GuardianWatcherService extends Service {
    private GuardianComponents components;
    private BackgroundLocationWatcher watcher;

    @Override
    public void onCreate() {
        super.onCreate();
        components = GuardianComponents.create(this);
        watcher = new BackgroundLocationWatcher(
            (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE),
            (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE),
            event -> components.applicationService().ingest(event));
        watcher.start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
