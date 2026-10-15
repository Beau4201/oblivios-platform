// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
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

        /** The finger lifted after a drag: the sheet is to rest tall or at its ordinary height. */
        void onDragEnd(boolean tall);
    }

    private final Ds mDs;
    private final ScrollView mScroll;
    private final LinearLayout mBody;
    private ValueAnimator mHeight;
    private final Host mHost;
    private final int mSlop;
    /** The two heights the sheet rests at, in pixels; a drag moves between them. */
    private int mMinPx;
    private int mMaxPx;
    private boolean mDragging;
    private boolean mFromTop;
    private float mDownY;
    private int mStartPx;
    private VelocityTracker mVelocity;

    SheetView(Context context, Ds ds, final Host host) {
        super(context);
        mDs = ds;
        mHost = host;
        mSlop = ViewConfiguration.get(context).getScaledTouchSlop();
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
        // one rhythm: every part stands 16 dp under the one before it, whatever margin the part brought
        boolean first = true;
        for (View v : parts) {
            LinearLayout.LayoutParams lp = v.getLayoutParams() instanceof LinearLayout.LayoutParams
                    ? (LinearLayout.LayoutParams) v.getLayoutParams()
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = first ? 0 : mDs.dp(16);
            first = false;
            mBody.addView(v, lp);
        }
        Ds.layoutNow(mScroll);
        mScroll.scrollTo(0, 0);
        if (y > 0) {
            mScroll.post(() -> mScroll.scrollTo(0, y));
        }
        if (animate) {
            Anim.fadeUp(mDs, mBody);
        }
    }

    /** The ordinary and the tall height in pixels: what a drag moves between. */
    void setRange(int minPx, int maxPx) {
        mMinPx = minPx;
        mMaxPx = maxPx;
    }

    private int currentPx() {
        ViewGroup.LayoutParams lp = getLayoutParams();
        return lp.height > 0 ? lp.height : getHeight();
    }

    /**
     * A drag moves the sheet from its handle and top (any direction), and from its list when the list is at the top and
     * the finger goes down, or when the sheet is not tall and the finger goes up: the list scrolls in every other case.
     */
    private boolean canDrag(float dy) {
        if (mAway || mMaxPx <= mMinPx) {
            return false;
        }
        if (mFromTop) {
            return true;
        }
        int h = currentPx();
        return dy > 0 ? mScroll.getScrollY() == 0 && h > mMinPx : h < mMaxPx;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        track(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragging = false;
                mDownY = e.getRawY();
                mFromTop = e.getY() < mDs.dp(48);
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = e.getRawY() - mDownY;
                if (!mDragging && Math.abs(dy) > mSlop && canDrag(dy)) {
                    startDrag(e);
                }
                break;
            default:
                break;
        }
        return mDragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        track(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownY = e.getRawY();
                mFromTop = e.getY() < mDs.dp(48);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging && Math.abs(e.getRawY() - mDownY) > mSlop && canDrag(e.getRawY() - mDownY)) {
                    startDrag(e);
                }
                if (mDragging) {
                    ViewGroup.LayoutParams lp = getLayoutParams();
                    lp.height = Math.max(mMinPx, Math.min(mMaxPx, mStartPx - Math.round(e.getRawY() - mDownY)));
                    setLayoutParams(lp);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mDragging) {
                    mDragging = false;
                    mVelocity.computeCurrentVelocity(1000);
                    float v = mVelocity.getYVelocity();
                    boolean tall = v < -mDs.dp(500) ? true : v > mDs.dp(500) ? false
                            : currentPx() > (mMinPx + mMaxPx) / 2;
                    mHost.onDragEnd(tall);
                }
                if (mVelocity != null) {
                    mVelocity.recycle();
                    mVelocity = null;
                }
                return true;
            default:
                return true;
        }
    }

    private void startDrag(MotionEvent e) {
        mDragging = true;
        if (mHeight != null) {
            mHeight.cancel();
        }
        mDownY = e.getRawY();   // no jump by the distance of the slop
        mStartPx = currentPx();
        getParent().requestDisallowInterceptTouchEvent(true);
    }

    private void track(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && mVelocity != null) {
            mVelocity.recycle();
            mVelocity = null;
        }
        if (mVelocity == null) {
            mVelocity = VelocityTracker.obtain();
        }
        mVelocity.addMovement(e);
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
