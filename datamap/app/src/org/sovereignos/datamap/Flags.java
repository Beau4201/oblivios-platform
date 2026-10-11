// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.Locale;

/** The flag of a country, from the vector drawables of this package (res/drawable/flag_xx.xml). */
final class Flags {
    private Flags() {}

    /** @return the flag, or null if the package has none for this code (MAP-016 checks that none is missing). */
    static Drawable of(Context context, String countryCode) {
        if (countryCode == null || !countryCode.matches("[A-Za-z]{2}")) {
            return null;
        }
        int id = context.getResources().getIdentifier(
                "flag_" + countryCode.toLowerCase(Locale.ROOT), "drawable", context.getPackageName());
        return id == 0 ? null : context.getDrawable(id);
    }
}
