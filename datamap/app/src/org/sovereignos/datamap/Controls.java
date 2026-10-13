// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;

/** The controls of the Settings page, drawn as the design page draws them (ADR-160). */
final class Controls {
    private Controls() {}

    /** {@code .sw2}: a 50 by 30 dp switch; the thumb slides in 200 ms. */
    static final class Switch extends CompoundButton {
        private final Ds mDs;
        private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mThumb = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private float mPos;   // 0 off, 1 on
        private ValueAnimator mAnim;

        Switch(Ds ds, boolean on) {
            super(ds.ctx);
            mDs = ds;
            setId(R.id.at_switch);
            setChecked(on);
            mPos = on ? 1 : 0;
            mThumb.setColor(ds.color(R.color.at_thumb));
            mThumb.setShadowLayer(ds.px(3), 0, ds.px(1), ds.color(R.color.at_shadow_thumb));
            setLayoutParams(new android.widget.LinearLayout.LayoutParams(ds.dp(50), ds.dp(30)));
            setClickable(true);
        }

        @Override
        public void setChecked(boolean checked) {
            boolean was = isChecked();
            super.setChecked(checked);
            if (was != checked && mDs != null) {
                if (mAnim != null) {
                    mAnim.cancel();
                }
                if (mDs.calm()) {
                    mPos = checked ? 1 : 0;
                    invalidate();
                    return;
                }
                mAnim = ValueAnimator.ofFloat(mPos, checked ? 1 : 0);
                mAnim.setDuration(200);
                mAnim.setInterpolator(Ds.STANDARD);
                mAnim.addUpdateListener(a -> {
                    mPos = (float) a.getAnimatedValue();
                    invalidate();
                });
                mAnim.start();
            }
        }

        @Override
        public boolean performClick() {
            toggle();
            return super.performClick();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            mTrack.setColor(blend(mDs.color(R.color.at_swoff), mDs.color(R.color.at_sw), mPos));
            mRect.set(0, 0, w, h);
            canvas.drawRoundRect(mRect, h / 2, h / 2, mTrack);
            float r = mDs.px(11);
            float x = mDs.px(4) + r + (mDs.px(24) - mDs.px(4)) * mPos;
            canvas.drawCircle(x, h / 2, r, mThumb);
        }

        private static int blend(int a, int b, float t) {
            int out = 0;
            for (int shift = 24; shift >= 0; shift -= 8) {
                int ca = (a >>> shift) & 0xFF;
                int cb = (b >>> shift) & 0xFF;
                out |= Math.round(ca + (cb - ca) * t) << shift;
            }
            return out;
        }
    }

    /** What {@link Segmented} tells when a choice is made. */
    interface OnChoice {
        void chosen(int index);
    }

    /**
     * {@code .seg2}: choices side by side on a track 42 dp high, with one thumb that slides to the chosen one in
     * 320 ms. The labels are 14 sp.
     */
    static final class Segmented extends View {
        private final Ds mDs;
        private final String[] mLabels;
        private final OnChoice mListener;
        private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mThumb = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private float mPos;        // the thumb, in choices
        private int mSelected;
        private ValueAnimator mAnim;

        Segmented(Ds ds, String[] labels, int selected, OnChoice listener) {
            super(ds.ctx);
            mDs = ds;
            mLabels = labels;
            mSelected = selected;
            mPos = selected;
            mListener = listener;
            setId(R.id.at_segmented);
            mTrack.setColor(ds.color(R.color.at_segtrack));
            mThumb.setColor(ds.color(R.color.at_segthumb));
            if (ds.light()) {
                mThumb.setShadowLayer(ds.px(3), 0, ds.px(1), 0x38142850);   // 0 1px 3px rgba(20,40,80,.22)
            }
            mText.setTypeface(Ds.face(Ds.MEDIUM));
            mText.setTextSize(ds.px(14));
            mText.setTextAlign(Paint.Align.CENTER);
            setClickable(true);
        }

        void select(int index, boolean animate) {
            if (index == mSelected) {
                return;
            }
            mSelected = index;
            if (mAnim != null) {
                mAnim.cancel();
            }
            if (!animate || mDs.calm()) {
                mPos = index;
                invalidate();
                return;
            }
            mAnim = ValueAnimator.ofFloat(mPos, index);
            mAnim.setDuration(320);
            mAnim.setInterpolator(Ds.STANDARD);
            mAnim.addUpdateListener(a -> {
                mPos = (float) a.getAnimatedValue();
                invalidate();
            });
            mAnim.start();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), mDs.dp(42));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float pad = mDs.px(3);
            mRect.set(0, 0, w, h);
            canvas.drawRoundRect(mRect, mDs.px(13), mDs.px(13), mTrack);
            float cell = (w - 2 * pad) / mLabels.length;
            mRect.set(pad + mPos * cell, pad, pad + (mPos + 1) * cell, h - pad);
            canvas.drawRoundRect(mRect, mDs.px(10), mDs.px(10), mThumb);
            for (int i = 0; i < mLabels.length; i++) {
                mText.setColor(mDs.color(i == mSelected ? R.color.at_on : R.color.at_onv));
                float cx = pad + (i + 0.5f) * cell;
                canvas.drawText(mLabels[i], cx, h / 2 - (mText.ascent() + mText.descent()) / 2, mText);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float pad = mDs.px(3);
                int i = (int) ((e.getX() - pad) / ((getWidth() - 2 * pad) / mLabels.length));
                i = Math.max(0, Math.min(mLabels.length - 1, i));
                if (i != mSelected) {
                    select(i, true);
                    mListener.chosen(i);
                }
                performClick();
            }
            return true;
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }

    /**
     * {@code .collapse}: a part that opens and closes in place. Its height is its content's height times a progress
     * that moves from 0 to 1 in 320 ms; what is below it moves with it.
     */
    static final class Collapse extends FrameLayout {
        private final Ds mDs;
        private float mProgress;
        private boolean mOpen;
        private ValueAnimator mAnim;

        Collapse(Ds ds, View content, boolean open) {
            super(ds.ctx);
            mDs = ds;
            mOpen = open;
            mProgress = open ? 1 : 0;
            addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            View c = getChildAt(0);
            int w = MeasureSpec.getSize(widthSpec);
            c.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            setMeasuredDimension(w, Math.round(c.getMeasuredHeight() * mProgress));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            View c = getChildAt(0);
            c.layout(0, 0, r - l, c.getMeasuredHeight());
        }

        boolean isOpen() {
            return mOpen;
        }

        void setOpen(boolean open, boolean animate) {
            if (open == mOpen) {
                return;
            }
            mOpen = open;
            if (mAnim != null) {
                mAnim.cancel();
            }
            if (!animate || mDs.calm()) {
                mProgress = open ? 1 : 0;
                requestLayout();
                return;
            }
            mAnim = ValueAnimator.ofFloat(mProgress, open ? 1 : 0);
            mAnim.setDuration(320);
            mAnim.setInterpolator(Ds.STANDARD);
            mAnim.addUpdateListener(a -> {
                mProgress = (float) a.getAnimatedValue();
                requestLayout();
            });
            mAnim.start();
        }
    }
}
