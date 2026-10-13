// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import org.sovereignos.datamap.model.FlagEmoji;

import java.util.Locale;

/**
 * The flag of a country: the drawing of the flag-icons set (MIT, see the NOTICE), 4 by 3, in a rectangle with rounded
 * corners and a thin edge. The drawings are in {@code res/drawable-nodpi/flag_xx.webp}, made by {@code tools/make-flags.sh}.
 */
final class Flags {
    /** The width of a flag over its height. */
    static final float ASPECT = 4f / 3f;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(32);

    private Flags() {}

    /** @return the flag, or null if the text is not a country code or the set has no flag for it. */
    static Drawable of(Context context, String countryCode) {
        if (FlagEmoji.of(countryCode) == null) {   // the check of what a country code is
            return null;
        }
        String name = "flag_" + countryCode.toLowerCase(Locale.ROOT);
        Bitmap b;
        synchronized (CACHE) {
            b = CACHE.get(name);
        }
        if (b == null) {
            int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
            if (id == 0) {
                return null;
            }
            b = BitmapFactory.decodeResource(context.getResources(), id);
            if (b == null) {
                return null;
            }
            synchronized (CACHE) {
                CACHE.put(name, b);
            }
        }
        return new FlagDrawable(b);
    }

    private static final class FlagDrawable extends Drawable {
        private final Bitmap mBitmap;
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint mEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private final Matrix mMatrix = new Matrix();

        FlagDrawable(Bitmap bitmap) {
            mBitmap = bitmap;
            mFill.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            mEdge.setStyle(Paint.Style.STROKE);
            mEdge.setColor(0x33808CA0);
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            // the flag keeps its 4 by 3 in the bounds it is given, centred
            float h = Math.min(b.height(), b.width() / ASPECT);
            float w = h * ASPECT;
            float left = b.exactCenterX() - w / 2f;
            float top = b.exactCenterY() - h / 2f;
            mMatrix.setScale(w / mBitmap.getWidth(), h / mBitmap.getHeight());
            mMatrix.postTranslate(left, top);
            mFill.getShader().setLocalMatrix(mMatrix);
            float radius = h * 0.14f;
            float edge = Math.max(1f, h / 18f);
            mEdge.setStrokeWidth(edge);
            mRect.set(left, top, left + w, top + h);
            canvas.drawRoundRect(mRect, radius, radius, mFill);
            mRect.inset(edge / 2f, edge / 2f);
            canvas.drawRoundRect(mRect, radius, radius, mEdge);
        }

        @Override
        public void setAlpha(int alpha) {
            mFill.setAlpha(alpha);
            mEdge.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            mFill.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
