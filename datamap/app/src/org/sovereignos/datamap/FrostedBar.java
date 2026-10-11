// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * The floating tab bar of the design reference: the surface colour at 72 percent over a 22 dp blur of what
 * stands behind it. A plain view cannot blur what is behind it, so the bar records the backdrop view into a
 * {@link RenderNode} with a blur effect and draws that, clipped to its own shape. It does so only when the
 * backdrop reports a change: a screen left open costs nothing. Its cost is measured on the device (MAP-018).
 */
final class FrostedBar extends LinearLayout {
    /** The views the bar looks through. It tells the bar when something in it is drawn again. */
    static final class Backdrop extends FrameLayout {
        private Runnable mOnChange;

        Backdrop(Context context) {
            super(context);
        }

        void setOnChange(Runnable r) {
            mOnChange = r;
        }

        @Override
        public void onDescendantInvalidated(View child, View target) {
            super.onDescendantInvalidated(child, target);
            if (mOnChange != null) {
                mOnChange.run();
            }
        }
    }

    private final Ui mUi;
    private final RenderNode mNode = new RenderNode("frost");
    private final Paint mTint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mShape = new Path();
    private final RectF mRect = new RectF();
    private Backdrop mBackdrop;
    private int mEffectFor = -1;

    FrostedBar(Context context, Ui ui) {
        super(context);
        mUi = ui;
        mTint.setColor(ui.color(R.color.dm_glass));
        mRing.setStyle(Paint.Style.STROKE);
        mRing.setStrokeWidth(ui.dp(1));
        mRing.setColor(0x29808080);
        setWillNotDraw(false);
        setElevation(ui.dp(6));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
            }
        });
    }

    void setBackdrop(Backdrop backdrop) {
        mBackdrop = backdrop;
        backdrop.setOnChange(this::invalidate);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float radius = h / 2f;
        mRect.set(0, 0, w, h);
        mShape.reset();
        mShape.addRoundRect(mRect, radius, radius, Path.Direction.CW);
        if (mBackdrop != null && canvas.isHardwareAccelerated() && w > 0 && h > 0) {
            if (mEffectFor != w) {
                float sigma = mUi.dp(22);
                mNode.setRenderEffect(RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP));
                mEffectFor = w;
            }
            mNode.setPosition(0, 0, w, h);
            RecordingCanvas rc = mNode.beginRecording(w, h);
            try {
                rc.translate(-(getLeft() + getTranslationX() - mBackdrop.getLeft()),
                        -(getTop() + getTranslationY() - mBackdrop.getTop()));
                mBackdrop.draw(rc);
            } finally {
                mNode.endRecording();
            }
            canvas.save();
            canvas.clipPath(mShape);
            canvas.drawRenderNode(mNode);
            canvas.restore();
        }
        canvas.drawPath(mShape, mTint);
        canvas.drawPath(mShape, mRing);
        super.dispatchDraw(canvas);
    }
}
