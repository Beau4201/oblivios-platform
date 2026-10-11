// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.airpods.debugharness;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/**
 * Validation harness for AIR-029..032 and AIR-036 (ADR-120). Installed in userdebug and eng
 * images only. Callable by uid shell or root only, so no app can drive it; the provider is
 * additionally guarded by android.permission.DUMP in the manifest. Same source for three
 * apps that differ only in their permission set, so a refusal is caused by the permission.
 * The Bluetooth address is an argument only and is never logged.
 */
public final class HarnessProvider extends ContentProvider {
    static final String METHOD_IDENTITY = "identity";
    static final String METHOD_METADATA = "metadata";
    static final String EXTRA_ON = "on";
    static final String EXTRA_KEY = "key";
    static final String EXTRA_HEX = "hex";
    static final String RESULT = "result";
    static final String ERROR = "error";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (uid != Process.SHELL_UID && uid != Process.ROOT_UID) {
            throw new SecurityException("adb shell only");
        }
        Bundle out = new Bundle();
        try {
            BluetoothAdapter adapter = getContext().getSystemService(BluetoothManager.class).getAdapter();
            if (adapter == null) throw new IllegalStateException("no Bluetooth adapter");
            BluetoothDevice device = adapter.getRemoteDevice(arg);
            if (METHOD_IDENTITY.equals(method)) {
                if (extras == null || !extras.containsKey(EXTRA_ON)) {
                    throw new IllegalArgumentException("missing --extra on:b:<true|false>");
                }
                out.putBoolean(RESULT, device.setAirPodsIdentity(extras.getBoolean(EXTRA_ON)));
            } else if (METHOD_METADATA.equals(method)) {
                if (extras == null || !extras.containsKey(EXTRA_KEY)) {
                    throw new IllegalArgumentException("missing --extra key:i:<int>");
                }
                byte[] value = HexBytes.parse(extras.getString(EXTRA_HEX));
                if (value == null) {
                    throw new IllegalArgumentException(
                            "--extra hex:s:<even-length ASCII hex, at most 4096 bytes>");
                }
                out.putBoolean(RESULT, device.setAirPodsMetadata(extras.getInt(EXTRA_KEY), value));
            } else {
                throw new IllegalArgumentException("unknown method");
            }
        } catch (RuntimeException e) {
            // The exception class is the evidence: AIR-032/036 need SecurityException, not a
            // linkage or hidden-API failure.
            out.putString(ERROR, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return out;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
