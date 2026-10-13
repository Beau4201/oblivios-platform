// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * {@code #sheet}: the sheet over the map. A handle 18 dp high with a bar of 34 by 4, and a body that scrolls; its top
 * corners are 28 dp round and it is bordered. Its height is set by the map tab; it changes in 300 ms and the sheet
 * slides away in 500 ms when the map is shown alone.
 */
final class SheetView extends FrameLayout {
    interface Host {
        void onHandle();
    }

    private final Ds mDs;
    private final ScrollView mScroll;
    private final LinearLayout mBody;
    private ValueAnimator mHeight;

    SheetView(Context context, Ds ds, final Host host) {
        super(context);
        mDs = ds;
        setId(R.id.at_sheet);
        setBackground(ds.sheetBox(28));
        setClipToOutline(true);
        final float r = ds.px(28);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + (int) r, r);
            }
        });
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        FrameLayout grab = new FrameLayout(context);
        grab.setId(R.id.at_sheet_handle);
        View bar = new View(context);
        bar.setBackground(ds.box(R.color.at_handle, 2, 0));
        grab.addView(bar, new FrameLayout.LayoutParams(ds.dp(34), ds.dp(4), Gravity.CENTER));
        grab.setContentDescription(context.getString(R.string.sheet_handle));
        grab.setClickable(true);
        grab.setOnClickListener(v -> host.onHandle());
        column.addView(grab, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ds.dp(18)));
        mScroll = new ScrollView(context);
        mScroll.setVerticalScrollBarEnabled(false);
        mScroll.setClipToPadding(false);
        mScroll.setPadding(ds.dp(16), 0, ds.dp(16), ds.dp(104));
        mBody = new LinearLayout(context);
        mBody.setOrientation(LinearLayout.VERTICAL);
        mScroll.addView(mBody, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(mScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Replaces what the sheet shows; the children rise in one after the other. */
    void setContent(View[] parts, boolean animate) {
        // a refresh (no animation) keeps where the reader was; a new choice starts at the top
        final int y = animate ? 0 : mScroll.getScrollY();
        mBody.removeAllViews();
        for (View v : parts) {
            mBody.addView(v);
        }
        mScroll.scrollTo(0, 0);
        if (y > 0) {
            mScroll.post(() -> mScroll.scrollTo(0, y));
        }
        if (animate) {
            Anim.fadeUp(mDs, mBody);
        }
    }

    /** The height of the sheet in pixels: 300 ms to the new one, or at once. */
    void setHeightPx(int px, boolean animate) {
        final ViewGroup.LayoutParams lp = getLayoutParams();
        if (mHeight != null) {
            mHeight.cancel();
        }
        int from = lp.height > 0 ? lp.height : px;
        if (!animate || mDs.calm() || from == px) {
            lp.height = px;
            setLayoutParams(lp);
            return;
        }
        mHeight = ValueAnimator.ofInt(from, px);
        mHeight.setDuration(300);
        mHeight.setInterpolator(Ds.STANDARD);
        mHeight.addUpdateListener(a -> {
            lp.height = (int) a.getAnimatedValue();
            setLayoutParams(lp);
        });
        mHeight.start();
    }

    /** True while the sheet is slid away; the offset follows a height that is not known yet or changes later. */
    private boolean mAway;

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (mAway && h != oh) {
            animate().cancel();
            setTranslationY(h * 1.04f);
        }
    }

    /** Slides the sheet away (the map alone) or back, in 500 ms. */
    void slideAway(boolean away, boolean animate) {
        mAway = away;
        float to = away ? getHeight() * 1.04f : 0f;
        animate().cancel();
        if (!animate || mDs.calm()) {
            setTranslationY(to);
            return;
        }
        animate().translationY(to).setDuration(500).setInterpolator(Ds.STANDARD).start();
    }
}
