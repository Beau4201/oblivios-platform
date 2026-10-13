// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

/**
 * An app icon the way the launcher draws a themed icon: the monochrome layer tinted with the accent colour,
 * on the tonal background. The colours are those of the launcher's icon library
 * (frameworks/libs/systemui/iconloaderlib, values-v31 and values-night-v31 of colors.xml: themed_icon_color,
 * themed_icon_background_color), and the construction is that of MonoIconThemeController.createThemedAdaptiveIcon.
 *
 * <p>Two things differ from the launcher, and MAP-014 records them. The launcher keeps its choice of themed
 * icons in its own preferences, which this app cannot read, so the icons here are themed whenever the app has a
 * monochrome layer. And the launcher also makes a monochrome icon for an app that has none; this app shows the
 * app's own icon.
 */
final class ThemedIcons {
    private ThemedIcons() {}

    static Drawable of(Context context, Drawable icon) {
        if (!(icon instanceof AdaptiveIconDrawable) || icon.getConstantState() == null) {
            return icon;
        }
        // a copy: tinting the layer of the shared icon would tint it for everyone who draws it
        AdaptiveIconDrawable copy = (AdaptiveIconDrawable) icon.getConstantState().newDrawable().mutate();
        Drawable mono = copy.getMonochrome();
        if (mono == null) {
            return icon;
        }
        boolean night = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        mono.setTint(context.getColor(night ? android.R.color.system_accent1_200 : android.R.color.system_accent1_700));
        return new AdaptiveIconDrawable(new ColorDrawable(context.getColor(
                night ? android.R.color.system_accent2_800 : android.R.color.system_accent1_100)), mono);
    }
}
