package org.oblivios.test.sscheck;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/**
 * Registers Activity.ScreenCaptureCallback and logs "FIRED" when the system reports a screenshot.
 * Test tool for the screenshot-notification patch; not part of the product.
 */
public class MainActivity extends Activity {
    private static final String TAG = "SSCheck";
    private int fired;
    private TextView view;
    private final Activity.ScreenCaptureCallback callback = () -> {
        fired++;
        Log.i(TAG, "FIRED count=" + fired);
        runOnUiThread(() -> view.setText("callback registered, fired " + fired + " times"));
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        view = new TextView(this);
        view.setTextSize(24);
        view.setPadding(48, 200, 48, 48);
        view.setText("callback registered, fired 0 times");
        setContentView(view);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerScreenCaptureCallback(getMainExecutor(), callback);
        Log.i(TAG, "REGISTERED");
    }

    @Override
    protected void onStop() {
        unregisterScreenCaptureCallback(callback);
        Log.i(TAG, "UNREGISTERED");
        super.onStop();
    }
}
