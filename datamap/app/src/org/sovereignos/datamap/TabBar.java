// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * {@code .bnav}: the floating bar of four tabs (ADR-160). It is 68 dp high with round ends, 14 dp from the sides and 12 from
 * the foot, a translucent surface over a 22 dp blur of what stands behind it, made 1.3 times more saturated. The chosen
 * tab grows and shows its name; the others show their icon. A plain view cannot blur what is behind it, so the bar
 * records the backdrop view into a {@link RenderNode} with a blur effect; it does so only when the backdrop reports a
 * change, so a screen left open costs nothing. Around the bar is 28 dp of room for its shadow.
 */
final class TabBar extends LinearLayout {
    interface OnTab {
        void chosen(int tab);
    }

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

    private static final int ROOM_DP = 28;
    private static final int[] NAMES = {R.string.tab_map, R.string.tab_apps, R.string.tab_companies, R.string.tab_settings};

    private final Ds mDs;
    private final RenderNode mNode = new RenderNode("frost");
    private final Paint mTint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mShape = new Path();
    private final RectF mRect = new RectF();
    private final Tab[] mTabs = new Tab[4];
    private Backdrop mBackdrop;
    private int mEffectFor = -1;

    TabBar(Context context, Ds ds, final OnTab listener) {
        super(context);
        mDs = ds;
        setId(R.id.at_tab_bar);
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        setPadding(ds.dp(ROOM_DP + 8), ds.dp(ROOM_DP), ds.dp(ROOM_DP + 8), ds.dp(ROOM_DP));
        setWillNotDraw(false);
        mTint.setColor(ds.color(R.color.at_glass));
        mRing.setStyle(Paint.Style.STROKE);
        mRing.setStrokeWidth(Math.max(1, ds.dp(1)));
        mRing.setColor(ds.color(R.color.at_bd));
        mShadow.setColor(0xFF000000);
        mShadow.setShadowLayer(ds.px(28), 0, ds.px(8), ds.color(R.color.at_shadow_bar));
        for (int i = 0; i < 4; i++) {
            final int tab = i;
            mTabs[i] = new Tab(context, i);
            mTabs[i].setOnClickListener(v -> listener.chosen(tab));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ds.dp(52), 1f);
            if (i > 0) {
                lp.setMarginStart(ds.dp(4));
            }
            addView(mTabs[i], lp);
        }
    }

    /** The size of the whole view, room for the shadow included, for a bar 68 dp high. */
    static FrameLayout.LayoutParams params(Ds ds) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ds.dp(68 + 2 * ROOM_DP), android.view.Gravity.BOTTOM);
        lp.setMargins(ds.dp(14 - ROOM_DP), 0, ds.dp(14 - ROOM_DP), ds.dp(12 - ROOM_DP));
        return lp;
    }

    void setBackdrop(Backdrop backdrop) {
        mBackdrop = backdrop;
        backdrop.setOnChange(this::invalidate);
        invalidate();
    }

    void select(int tab, boolean animate) {
        for (int i = 0; i < 4; i++) {
            mTabs[i].setSelected(i == tab, animate);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int room = mDs.dp(ROOM_DP);
        int w = getWidth();
        int h = getHeight();
        float radius = (h - 2 * room) / 2f;
        mRect.set(room, room, w - room, h - room);
        mShape.reset();
        mShape.addRoundRect(mRect, radius, radius, Path.Direction.CW);
        // the shadow, outside the bar only
        canvas.save();
        canvas.clipOutPath(mShape);
        canvas.drawPath(mShape, mShadow);
        canvas.restore();
        int bw = w - 2 * room;
        int bh = h - 2 * room;
        if (mBackdrop != null && canvas.isHardwareAccelerated() && bw > 0 && bh > 0) {
            if (mEffectFor != bw) {
                float sigma = mDs.px(22);
                ColorMatrix sat = new ColorMatrix();
                sat.setSaturation(1.3f);
                mNode.setRenderEffect(RenderEffect.createChainEffect(
                        RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(sat)),
                        RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP)));
                mEffectFor = bw;
            }
            mNode.setPosition(0, 0, bw, bh);
            RecordingCanvas rc = mNode.beginRecording(bw, bh);
            try {
                rc.translate(-(getLeft() + getTranslationX() + room - mBackdrop.getLeft()),
                        -(getTop() + getTranslationY() + room - mBackdrop.getTop()));
                mBackdrop.draw(rc);
            } finally {
                mNode.endRecording();
            }
            canvas.save();
            canvas.clipPath(mShape);
            canvas.translate(room, room);
            canvas.drawRenderNode(mNode);
            canvas.restore();
        }
        canvas.drawPath(mShape, mTint);
        mRect.inset(mRing.getStrokeWidth() / 2, mRing.getStrokeWidth() / 2);
        canvas.drawRoundRect(mRect, radius, radius, mRing);
        super.dispatchDraw(canvas);
    }

    /** One tab: an icon and, as it is chosen, its name. All of it is drawn here, so that the growing is one number. */
    private final class Tab extends View {
        private final int mIndex;
        private final Drawable mIcon;
        private final Drawable mIconOn;
        private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mPill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mPillRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String mName;
        private float mP;            // 0 not chosen, 1 chosen
        private boolean mOn;
        private ValueAnimator mAnim;

        Tab(Context context, int index) {
            super(context);
            mIndex = index;
            mName = context.getString(NAMES[index]);
            int[] off = {R.drawable.ic_globe, R.drawable.ic_grid, R.drawable.ic_bizo, R.drawable.ic_gear};
            int[] on = {R.drawable.ic_globe, R.drawable.ic_gridf, R.drawable.ic_biz, R.drawable.ic_gear};
            mIcon = context.getDrawable(off[index]).mutate();
            mIconOn = context.getDrawable(on[index]).mutate();
            mText.setTypeface(Ds.face(Ds.MEDIUM));
            mText.setTextSize(mDs.px(14));
            mPill.setColor(mDs.color(R.color.at_pill));
            mPillRing.setStyle(Paint.Style.STROKE);
            mPillRing.setStrokeWidth(Math.max(1, mDs.dp(1)));
            mPillRing.setColor(mDs.color(R.color.at_pillbd));
            setClickable(true);
            setContentDescription(mName);
            setId(R.id.at_tab);
        }

        void setSelected(boolean on, boolean animate) {
            if (on == mOn && mAnim == null && mP == (on ? 1f : 0f)) {
                return;
            }
            mOn = on;
            setId(on ? R.id.at_tab_selected : R.id.at_tab);
            if (mAnim != null) {
                mAnim.cancel();
            }
            if (!animate || mDs.calm()) {
                mP = on ? 1 : 0;
                applyWeight();
                invalidate();
                return;
            }
            mAnim = ValueAnimator.ofFloat(mP, on ? 1 : 0);
            mAnim.setDuration(350);
            mAnim.setInterpolator(Ds.STANDARD);
            mAnim.addUpdateListener(a -> {
                mP = (float) a.getAnimatedValue();
                applyWeight();
                invalidate();
            });
            mAnim.start();
        }

        /** {@code flex-grow} from 1 to 2.1. */
        private void applyWeight() {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) getLayoutParams();
            if (lp != null) {
                lp.weight = 1f + 1.1f * mP;
                requestLayout();
            }
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            setMinimumWidth(mDs.dp(52));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float radius = mDs.px(26);
            if (mP > 0f) {
                mPill.setAlpha(Math.round(255 * mP));
                mPillRing.setAlpha(Math.round(255 * mP));
                mRect.set(0, 0, w, h);
                canvas.drawRoundRect(mRect, radius, radius, mPill);
                mRect.inset(mPillRing.getStrokeWidth() / 2, mPillRing.getStrokeWidth() / 2);
                canvas.drawRoundRect(mRect, radius, radius, mPillRing);
            }
            float icon = mDs.px(26);
            float label = Math.min(mDs.px(110), mText.measureText(mName));
            float content = icon + mP * (mDs.px(8) + label);
            float x = (w - content) / 2;
            float y = (h - icon) / 2;
            Drawable d = mP > 0.5f ? mIconOn : mIcon;
            if (mIndex == 1 && mP > 0.5f) {
                d.setTintList(null);
            } else {
                d.setTint(blend(mDs.color(R.color.at_onv), mDs.color(R.color.at_on), mP));
            }
            d.setBounds(Math.round(x), Math.round(y), Math.round(x + icon), Math.round(y + icon));
            d.draw(canvas);
            if (mP > 0f) {
                mText.setColor(Ds.withAlpha(mDs.color(R.color.at_on), mP));
                canvas.save();
                canvas.clipRect(x + icon, 0, x + icon + mDs.px(8) + label * mP + mDs.px(2), h);
                canvas.drawText(mName, x + icon + mDs.px(8), h / 2 - (mText.ascent() + mText.descent()) / 2, mText);
                canvas.restore();
            }
        }

        private int blend(int a, int b, float t) {
            int out = 0;
            for (int shift = 24; shift >= 0; shift -= 8) {
                int ca = (a >>> shift) & 0xFF;
                int cb = (b >>> shift) & 0xFF;
                out |= Math.round(ca + (cb - ca) * t) << shift;
            }
            return out;
        }
    }
}
