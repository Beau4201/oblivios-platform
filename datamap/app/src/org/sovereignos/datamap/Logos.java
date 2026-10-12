// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.content.Context;
import android.graphics.drawable.Drawable;

import org.sovereignos.datamap.model.LogoName;

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
        return id == 0 ? null : context.getDrawable(id);
    }
}
