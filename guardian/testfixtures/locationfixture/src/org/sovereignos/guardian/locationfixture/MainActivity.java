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
            try {
                locationManager.requestSingleUpdate(
                    LocationManager.GPS_PROVIDER, new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            // No-op: the request itself is what triggers the
                            // AppOps note Guardian's watcher observes. The
                            // fixture does not need the result.
                        }
                    }, Looper.getMainLooper());
            } catch (SecurityException noFinePermission) {
                // Expected when this fixture is deliberately granted only
                // ACCESS_COARSE_LOCATION (Task 10's real-hardware coarse-only
                // empirical check) -- the fixture must not crash and take the
                // independent FUSED_PROVIDER request below down with it.
            }
        }, BACKGROUND_LOCATION_REQUEST_DELAY_MS);

        // Second, independent delayed request for a coarse-precision fix
        // (Task 10, Guardian residuals hardening: real-hardware empirical
        // check of the coarse-location AppOps channel prediction made in
        // BackgroundLocationWatcher, ADR-034/036). Same delay/background
        // rationale as the GPS request above.
        //
        // Uses FUSED_PROVIDER, not NETWORK_PROVIDER: confirmed live via
        // `adb shell dumpsys location` on the real lynx device that
        // NETWORK_PROVIDER here is the AOSP stub (identity=android[LocationService],
        // enabled=false, no backing service) -- GrapheneOS ships without a real
        // network-location backend, so a NETWORK_PROVIDER request can register
        // but never produces any actual access for Guardian's watcher to see.
        // FUSED_PROVIDER has a real backing service on this device
        // (com.android.location.fused/.FusedLocationService) and is the
        // documented, modern API that automatically downgrades to coarse
        // precision for a caller holding only ACCESS_COARSE_LOCATION.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            LocationManager locationManager = (LocationManager)
                getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestSingleUpdate(
                LocationManager.FUSED_PROVIDER, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        // No-op: see the GPS request above.
                    }
                }, Looper.getMainLooper());
        }, BACKGROUND_LOCATION_REQUEST_DELAY_MS);
    }
}
