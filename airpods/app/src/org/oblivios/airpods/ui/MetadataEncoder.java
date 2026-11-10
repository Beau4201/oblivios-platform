// SPDX-License-Identifier: Apache-2.0
package org.oblivios.airpods.ui;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Encodes the Bluetooth metadata values Settings reads (BluetoothUtils.getIntMetaData and friends
 * parse plain text: Integer.parseInt of the bytes, Boolean.parseBoolean, a URI string, and for key
 * 25 the first matching <TAG>value</TAG>). The platform only checks the length, so the format is
 * enforced here.
 */
public final class MetadataEncoder {
    /** BluetoothDevice.METADATA_MAX_LENGTH. */
    public static final int MAX_BYTES = 2048;

    private static final Pattern TAG_VALUE = Pattern.compile("[A-Za-z0-9._$:/-]+");
    private static final Pattern URI = Pattern.compile("content://[A-Za-z0-9._-]+/[A-Za-z0-9._/-]+");

    private MetadataEncoder() {}

    private static byte[] ascii(String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        if (b.length > MAX_BYTES) throw new IllegalArgumentException("value too long");
        return b;
    }

    /** Decimal text 0..100, no trailing newline (parseInt does not trim). */
    public static byte[] percent(int p) {
        if (p < 0 || p > 100) throw new IllegalArgumentException("percent out of range");
        return ascii(Integer.toString(p));
    }

    /** Unknown (-1) becomes an empty value, which clears the key. */
    public static byte[] percentOrClear(int p) {
        return p == -1 ? new byte[0] : percent(p);
    }

    public static byte[] bool(boolean b) {
        return ascii(b ? "true" : "false");
    }

    /** A content URI string, used verbatim by Settings. */
    public static byte[] uri(String uri) {
        if (uri == null || !URI.matcher(uri).matches()) throw new IllegalArgumentException("bad uri");
        return ascii(uri);
    }

    /** Value of metadata key 25 that tells Settings which service provides the device page. */
    public static byte[] configTags(String packageName, String className, String action) {
        return ascii(tag("DEVICE_SETTINGS_CONFIG_PACKAGE_NAME", packageName)
                + tag("DEVICE_SETTINGS_CONFIG_CLASS", className)
                + tag("DEVICE_SETTINGS_CONFIG_ACTION", action));
    }

    private static String tag(String name, String value) {
        if (value == null || !TAG_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("bad tag value");
        }
        return "<" + name + ">" + value + "</" + name + ">";
    }
}
