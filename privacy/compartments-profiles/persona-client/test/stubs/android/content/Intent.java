// SPDX-License-Identifier: Apache-2.0
package android.content;

import java.util.HashMap;
import java.util.Map;

/** Host stand-in of the part of Intent that PersonaProcess touches. */
public class Intent {
    public static final String ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED";
    public static final String EXTRA_TIMEZONE = "time-zone";

    private final String action;
    private final Map<String, String> extras = new HashMap<>();

    public Intent(String action) {
        this.action = action;
    }

    public String getAction() {
        return action;
    }

    public Intent putExtra(String name, String value) {
        extras.put(name, value);
        return this;
    }

    public String getStringExtra(String name) {
        return extras.get(name);
    }

    public boolean hasExtra(String name) {
        return extras.containsKey(name);
    }
}
