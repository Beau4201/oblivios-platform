package org.sovereignos.guardian.locationfixture;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public final class MainActivity extends Activity {
    private static final long BACKGROUND_LOCATION_REQUEST_DELAY_MS = 8000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Deliberately delayed: Guardian's watcher only flags location access
        // that happens while this app is backgrounded (it checks
        // ActivityManager.getUidImportance(uid) > IMPORTANCE_FOREGROUND at the
        // moment of the AppOps note). The delay gives the test script time to
        // send this activity to the background (Home) before the request
        // fires. getApplicationContext() is used instead of the Activity
        // itself so the request cannot be torn down by activity lifecycle
        // changes (e.g. onDestroy()) once backgrounded.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            LocationManager locationManager = (LocationManager)
                getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        // No-op: the request itself is what triggers the
                        // AppOps note Guardian's watcher observes. The
                        // fixture does not need the result.
                    }
                }, Looper.getMainLooper());
        }, BACKGROUND_LOCATION_REQUEST_DELAY_MS);
    }
}
