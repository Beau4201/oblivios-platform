// SPDX-License-Identifier: Apache-2.0
package android.os;

import java.util.HashMap;
import java.util.Map;

/** Host stand-in for android.os.Bundle, only the string accessors that PersonaProcess uses. Test use only. */
public final class Bundle {
    private final Map<String, String> mStrings = new HashMap<>();

    public Bundle() {}

    public void putString(String key, String value) {
        mStrings.put(key, value);
    }

    public String getString(String key) {
        return mStrings.get(key);
    }
}
