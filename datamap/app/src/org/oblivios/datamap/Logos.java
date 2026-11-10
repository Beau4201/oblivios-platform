// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap;

import android.content.Context;
import android.graphics.drawable.Drawable;

import org.oblivios.datamap.model.LogoName;

/**
 * The logos of companies (ADR-159): vector drawables in this package that tools/import-logos.py made from the
 * files recorded in logos/logos.tsv. A company with no logo here has none: the caller draws its initials.
 * Nothing is fetched at run time.
 */
final class Logos {
    private Logos() {}

    static Drawable of(Context context, String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return null;
        }
        int id = context.getResources().getIdentifier(LogoName.of(ownerId), "drawable", context.getPackageName());
        if (id == 0) {
            return null;
        }
        try {
            return context.getDrawable(id);
        } catch (RuntimeException e) {
            return null;   // a drawable that cannot be read must cost a logo, not the app: the initials are drawn
        }
    }
}
