// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
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
            setSaveEnabled(false); // two Switches would swap their restored values
            mPos = on ? 1 : 0;   // before setChecked: a switch that is built "on" stands there, it does not slide
            setChecked(on);
            mThumb.setColor(ds.color(R.color.at_thumb));
            mThumb.setShadowLayer(ds.px(3), 0, ds.px(1), ds.color(R.color.at_shadow_thumb));
            // 30 dp of track in a touch target of 48
            setLayoutParams(new android.widget.LinearLayout.LayoutParams(ds.dp(50), ds.dp(48)));
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
        public CharSequence getAccessibilityClassName() {
            return android.widget.Switch.class.getName();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float th = mDs.px(30);
            float top = (getHeight() - th) / 2;
            mTrack.setColor(blend(mDs.color(R.color.at_swoff), mDs.color(R.color.at_sw), mPos));
            mRect.set(0, top, w, top + th);
            canvas.drawRoundRect(mRect, th / 2, th / 2, mTrack);
            float r = mDs.px(11);
            float x = mDs.px(4) + r + (mDs.px(24) - mDs.px(4)) * mPos;
            canvas.drawCircle(x, top + th / 2, r, mThumb);
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
            mText.setTextSize(ds.sp(14));
            mText.setTextAlign(Paint.Align.CENTER);
            setClickable(true);
            setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public AccessibilityNodeProvider getAccessibilityNodeProvider(View host) {
                    return mNodes;
                }
            });
        }

        /** Each choice is a node, so that a screen reader can reach it and choose it. */
        private final AccessibilityNodeProvider mNodes = new AccessibilityNodeProvider() {
            @Override
            public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
                if (id == View.NO_ID) {
                    AccessibilityNodeInfo info = new AccessibilityNodeInfo(Segmented.this);
                    onInitializeAccessibilityNodeInfo(info);
                    for (int i = 0; i < mLabels.length; i++) {
                        info.addChild(Segmented.this, i);
                    }
                    return info;
                }
                if (id < 0 || id >= mLabels.length) {
                    return null;
                }
                AccessibilityNodeInfo info = new AccessibilityNodeInfo(Segmented.this, id);
                info.setParent(Segmented.this);
                info.setPackageName(getContext().getPackageName());
                info.setClassName(android.widget.RadioButton.class.getName());
                info.setText(mLabels[id]);
                info.setSelected(id == mSelected);
                info.setEnabled(true);
                info.setFocusable(true);
                info.setClickable(true);
                info.setVisibleToUser(true);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
                float cell = (getWidth() - 2 * mDs.px(3)) / mLabels.length;
                android.graphics.Rect r = new android.graphics.Rect(Math.round(mDs.px(3) + id * cell), 0,
                        Math.round(mDs.px(3) + (id + 1) * cell), getHeight());
                int[] loc = new int[2];
                getLocationOnScreen(loc);
                r.offset(loc[0], loc[1]);
                info.setBoundsInScreen(r);
                return info;
            }

            @Override
            public boolean performAction(int id, int action, android.os.Bundle arguments) {
                if (id >= 0 && id < mLabels.length && action == AccessibilityNodeInfo.ACTION_CLICK) {
                    if (id != mSelected) {
                        select(id, true);
                        mListener.chosen(id);
                    }
                    return true;
                }
                return false;
            }
        };

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
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(mDs.px(42) * mDs.grow()));
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
