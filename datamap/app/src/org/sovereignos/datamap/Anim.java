// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.view.View;
import android.view.ViewGroup;

/** The small entrances of the design page (ADR-160). Each does nothing when the system setting removes animation. */
final class Anim {
    private Anim() {}

    /** {@code #sheetIn.fx>*}: the children rise 8 dp and fade in over 300 ms, 40 ms after one another (the first four). */
    static void fadeUp(Ds ds, ViewGroup parent) {
        if (ds.calm()) {
            return;
        }
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(ds.px(8));
            v.animate().alpha(1f).translationY(0f).setDuration(300).setStartDelay(Math.min(i, 3) * 40L)
                    .setInterpolator(Ds.STANDARD).start();
        }
    }

    /** {@code .page}: a tab page fades up 14 dp over 320 ms. */
    static void pageIn(Ds ds, View v) {
        if (ds.calm()) {
            return;
        }
        v.setAlpha(0f);
        v.setTranslationY(ds.px(14));
        v.animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(Ds.STANDARD).start();
    }

    /** {@code #screen}: a detail page slides in from the right, 30 dp, over 320 ms. */
    static void detailIn(Ds ds, View v) {
        if (ds.calm()) {
            return;
        }
        v.setAlpha(0f);
        v.setTranslationX(v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -ds.px(30) : ds.px(30));
        v.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(Ds.STANDARD).start();
    }

    /** The way out of {@link #pageIn}: the page fades out and lifts 6 dp over 160 ms; {@code end} runs when it is gone. */
    static void pageOut(Ds ds, View v, Runnable end) {
        if (ds.calm()) {
            end.run();
            return;
        }
        v.animate().alpha(0f).translationY(-ds.px(6)).setDuration(160).setInterpolator(Ds.STANDARD)
                .withEndAction(end).start();
    }

    /** The way out of {@link #detailIn}: the page slides 30 dp to the right and fades out over 240 ms. */
    static void detailOut(Ds ds, View v, Runnable end) {
        if (ds.calm()) {
            end.run();
            return;
        }
        v.animate().alpha(0f).translationX(v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -ds.px(30) : ds.px(30))
                .setDuration(240).setInterpolator(Ds.STANDARD).withEndAction(end).start();
    }

    /** The way out of {@link #pop}: a dialog or a menu fades out over 160 ms and is then removed from its host. */
    static void dismiss(Ds ds, ViewGroup host, View layer) {
        if (ds.calm() || !layer.isAttachedToWindow()) {
            host.removeView(layer);
            return;
        }
        layer.setClickable(false);
        layer.animate().alpha(0f).setDuration(160).setInterpolator(Ds.STANDARD)
                .withEndAction(() -> host.removeView(layer)).start();
    }

    /** {@code pop}: a dialog or a menu scales in from 92 percent; {@code pivotX} and {@code pivotY} are the origin. */
    static void pop(Ds ds, View v, float pivotX, float pivotY, int ms) {
        if (ds.calm()) {
            return;
        }
        v.setPivotX(pivotX);
        v.setPivotY(pivotY);
        v.setAlpha(0f);
        v.setScaleX(0.92f);
        v.setScaleY(0.92f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ms).setInterpolator(Ds.STANDARD).start();
    }

    static void fadeIn(Ds ds, View v, int ms) {
        if (ds.calm()) {
            return;
        }
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(ms).start();
    }
}
