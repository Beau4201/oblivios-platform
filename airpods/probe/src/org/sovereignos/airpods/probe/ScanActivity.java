package org.sovereignos.airpods.probe;

import android.app.Activity;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Advertisement capture for AIR-148 (does the case value reach the phone without the accessory
 * channel?). Debug tool, never in an image; installed with adb, then
 * `pm grant org.sovereignos.airpods.probe android.permission.BLUETOOTH_SCAN`, revoked afterwards.
 * Start with, for example:
 * adb shell am start -n org.sovereignos.airpods.probe/.ScanActivity --ei seconds 30 --es label ears
 * Add `--ez any true` to drop the Apple manufacturer filter (only the histogram of Apple message
 * types is kept then, never content of other types).
 *
 * What is logged (tag AirPodsScan): for the proximity message (Apple type 0x07) the first 11 bytes
 * (type, length, paired flag, model, status, pod battery, case flags and battery, lid, colour,
 * connection state) in hex and a 32-bit hash of the rest (the block that newer firmware
 * encrypts), only when one of them changed for that emitter. Emitters are named E1, E2 in order
 * of appearance; an address is never logged. A summary with the counts follows at the end.
 * The layout is third-party documentation (LibrePods), not yet verified on this pair: this run
 * is what verifies it.
 */
public final class ScanActivity extends Activity {
    private static final String TAG = "AirPodsScan";
    private static final int APPLE = 0x004C;
    private static final int HEAD = 11;

    private final Map<String, String> alias = new HashMap<>();
    private final Map<String, String> last = new HashMap<>();
    private final TreeMap<Integer, Integer> types = new TreeMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private ScanCallback callback;
    private String label;
    private long t0;
    private int proximity;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int seconds = getIntent().getIntExtra("seconds", 30);
        boolean any = getIntent().getBooleanExtra("any", false);
        label = getIntent().getStringExtra("label");
        if (label == null) label = "none";
        scanner = getSystemService(BluetoothManager.class).getAdapter().getBluetoothLeScanner();
        if (scanner == null) {
            Log.e(TAG, "no scanner (Bluetooth off?)");
            finish();
            return;
        }
        t0 = SystemClock.elapsedRealtime();
        callback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                handle(result);
            }

            @Override
            public void onBatchScanResults(java.util.List<ScanResult> results) {
                for (ScanResult r : results) handle(r);
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "scan failed, code " + errorCode);
            }
        };
        ArrayList<ScanFilter> filters = new ArrayList<>();
        if (!any) {
            filters.add(new ScanFilter.Builder().setManufacturerData(APPLE, new byte[] {0x07}, new byte[] {(byte) 0xFF}).build());
        }
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        try {
            scanner.startScan(filters, settings, callback);
        } catch (SecurityException e) {
            Log.e(TAG, "BLUETOOTH_SCAN not granted");
            finish();
            return;
        }
        Log.i(TAG, "scan started label=" + label + " seconds=" + seconds + " filter=" + (any ? "none" : "apple type 07"));
        handler.postDelayed(this::stop, seconds * 1000L);
    }

    private void handle(ScanResult result) {
        ScanRecord record = result.getScanRecord();
        byte[] data = record == null ? null : record.getManufacturerSpecificData(APPLE);
        if (data == null || data.length == 0) return;
        int type = data[0] & 0xFF;
        types.merge(type, 1, Integer::sum);
        if (type != 0x07 || data.length < HEAD) return;
        proximity++;
        String address = result.getDevice().getAddress();
        String name = alias.get(address);
        if (name == null) {
            name = "E" + (alias.size() + 1);
            alias.put(address, name);
        }
        String head = hex(data, 0, HEAD);
        String tail = String.format("%08x", hash(data, HEAD));
        String key = head + " " + tail;
        if (key.equals(last.get(address))) return;
        last.put(address, key);
        Log.i(TAG, "+" + (SystemClock.elapsedRealtime() - t0) + " ms label=" + label + " " + name + " rssi=" + result.getRssi()
                + " len=" + data.length + " head=" + head + " tail#=" + tail);
    }

    private void stop() {
        try {
            scanner.stopScan(callback);
        } catch (SecurityException ignored) {
            // permission revoked while scanning
        }
        Log.i(TAG, "scan done label=" + label + " proximityResults=" + proximity + " emitters=" + alias.size() + " appleTypes=" + types);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (scanner != null && callback != null) {
            try {
                scanner.stopScan(callback);
            } catch (SecurityException ignored) {
                // permission revoked while scanning
            }
        }
        super.onDestroy();
    }

    private static String hex(byte[] b, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < Math.min(to, b.length); i++) sb.append(String.format("%02X ", b[i] & 0xFF));
        return sb.toString().trim();
    }

    private static int hash(byte[] b, int from) {
        int h = 0x811C9DC5;
        for (int i = from; i < b.length; i++) h = (h ^ (b[i] & 0xFF)) * 0x01000193;
        return h;
    }
}
