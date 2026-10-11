// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * The sheet over the map for a chosen app, company, relation or group (design reference page). It has a
 * handle that switches between half and full height, a scrolling body, and a height that moves with the
 * stock 200 ms standard curve. It draws nothing while it stands still.
 */
final class SheetView extends LinearLayout {
    interface Host {
        void onHandle();

        /** The height in pixels while it moves, so that the globe can keep clear of the sheet. */
        void onHeight(float px);
    }

    private static final PathInterpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private final Host mHost;
    private final ScrollView mScroll;
    private ValueAnimator mAnim;
    private int mHeight;

    SheetView(Context context, Ui ui, Host host) {
        super(context);
        mHost = host;
        setOrientation(VERTICAL);
        setBackground(ui.shape(R.color.dm_row, 28, 0));
        setElevation(ui.dp(8));
        setClipToOutline(true);

        FrameLayout handleRow = new FrameLayout(context);
        View bar = new View(context);
        bar.setBackground(ui.shape(R.color.dm_ol, 2, 2));
        handleRow.addView(bar, new FrameLayout.LayoutParams(ui.dp(32), ui.dp(4), Gravity.CENTER));
        handleRow.setContentDescription(context.getString(R.string.sheet_handle));
        handleRow.setOnClickListener(v -> mHost.onHandle());
        addView(handleRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(28)));

        mScroll = new ScrollView(context);
        mScroll.setVerticalScrollBarEnabled(false);
        addView(mScroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** The scrolling body; {@code bottomPadding} keeps the end of a long list clear of the tab bar. */
    void setBottomPadding(int px) {
        mScroll.setPadding(0, 0, 0, px);
        mScroll.setClipToPadding(false);
    }

    /** Replaces the body. {@code keepScroll}: a live refresh must not throw the reader back to the top. */
    void setContent(View content, boolean keepScroll) {
        final int y = mScroll.getScrollY();
        mScroll.removeAllViews();
        if (content != null) {
            mScroll.addView(content);
        }
        if (keepScroll) {
            mScroll.post(() -> mScroll.scrollTo(0, y));
        } else {
            mScroll.scrollTo(0, 0);
        }
    }

    /** Moves to a height in pixels; 0 hides the sheet when the move has ended. */
    void animateTo(int px) {
        if (mAnim != null) {
            mAnim.cancel();
        }
        final int from = mHeight;
        if (from == px) {
            return;
        }
        setVisibility(View.VISIBLE);
        mAnim = ValueAnimator.ofInt(from, px);
        mAnim.setDuration(200);
        mAnim.setInterpolator(STANDARD);
        mAnim.addUpdateListener(a -> apply((int) a.getAnimatedValue()));
        mAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!mCancelled && mHeight == 0) {
                    setVisibility(View.GONE);
                }
            }
        });
        mAnim.start();
    }

    /** Jumps to a height without moving, for the first layout. */
    void jumpTo(int px) {
        if (mAnim != null) {
            mAnim.cancel();
        }
        apply(px);
        setVisibility(px == 0 ? View.GONE : View.VISIBLE);
    }

    private void apply(int px) {
        mHeight = px;
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null) {
            lp.height = px;
            setLayoutParams(lp);
        }
        mHost.onHeight(px);
    }
}
