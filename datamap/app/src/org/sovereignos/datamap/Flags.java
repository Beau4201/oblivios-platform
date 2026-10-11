// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import org.sovereignos.datamap.model.FlagEmoji;

/** The flag of a country: the emoji of the system font, drawn in the bounds it is given. */
final class Flags {
    private Flags() {}

    /** @return the flag, or null if the text is not a country code. */
    static Drawable of(Context context, String countryCode) {
        String emoji = FlagEmoji.of(countryCode);
        return emoji == null ? null : new EmojiDrawable(emoji);
    }

    private static final class EmojiDrawable extends Drawable {
        private final String mText;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        EmojiDrawable(String text) {
            mText = text;
            mPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            mPaint.setTextSize(b.height() * 0.9f);
            canvas.drawText(mText, b.exactCenterX(),
                    b.exactCenterY() - (mPaint.ascent() + mPaint.descent()) / 2f, mPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            mPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            mPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
