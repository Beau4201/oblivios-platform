// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/**
 * An app icon the way the design page draws it: the whole picture, square, with the radius of the tile on top. An
 * adaptive icon is drawn without the mask of the launcher: its two layers are laid out at the size of the 108 dp
 * canvas, so that the picture reaches the edges of the tile, and the tile clips it.
 */
final class AppIcons {
    private AppIcons() {}

    static Drawable fullBleed(Drawable icon) {
        if (icon instanceof AdaptiveIconDrawable && icon.getConstantState() != null) {
            return new Bleed((AdaptiveIconDrawable) icon.getConstantState().newDrawable().mutate());
        }
        // a copy for each caller: two views must not set the bounds of one drawable
        Drawable.ConstantState state = icon.getConstantState();
        return state == null ? icon : state.newDrawable().mutate();
    }

    private static final class Bleed extends Drawable {
        /** The visible part of an adaptive icon is the middle 72 of 108 dp: the layers are 108/72 wider than the tile. */
        private static final float OVERSCAN = (108f - 72f) / 2f / 72f;
        private final AdaptiveIconDrawable mIcon;

        Bleed(AdaptiveIconDrawable icon) {
            mIcon = icon;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            int grow = Math.round(b.width() * OVERSCAN);
            Rect big = new Rect(b.left - grow, b.top - grow, b.right + grow, b.bottom + grow);
            Drawable bg = mIcon.getBackground();
            Drawable fg = mIcon.getForeground();
            if (bg != null) {
                bg.setBounds(big);
                bg.draw(canvas);
            }
            if (fg != null) {
                fg.setBounds(big);
                fg.draw(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            mIcon.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            mIcon.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
