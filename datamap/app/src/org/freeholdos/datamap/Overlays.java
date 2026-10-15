// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * What stands over a screen for a moment (ADR-160): the menu of a detail page, the question before everything is
 * deleted, and the sheet of the Companies filter. Each fills the content area; a touch outside closes it.
 */
final class Overlays {
    private Overlays() {}

    /** {@code #menu}: two entries, 190 dp wide or more, that scale in from the top right corner in 160 ms. */
    static View menu(final Widgets w, final FrameLayout host, final Runnable onSettings,
            final Runnable onMap) {
        final Ds ds = w.ds;
        final FrameLayout layer = new FrameLayout(ds.ctx);
        layer.setClickable(true);
        final LinearLayout box = new LinearLayout(ds.ctx);
        box.setId(R.id.at_menu);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setMinimumWidth(ds.dp(190));
        box.setPadding(0, ds.dp(6), 0, ds.dp(6));
        box.setBackground(ds.box(R.color.at_card, 14, R.color.at_bd));
        box.setElevation(ds.px(8));
        box.setOutlineSpotShadowColor(ds.color(R.color.at_shadow_menu));
        int[] icons = {R.drawable.ic_gear, R.drawable.ic_map};
        int[] names = {R.string.menu_settings, R.string.menu_back_to_map};
        final Runnable[] actions = {onSettings, onMap};
        for (int i = 0; i < 2; i++) {
            final int k = i;
            LinearLayout item = new LinearLayout(ds.ctx);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(ds.dp(16), 0, ds.dp(16), 0);
            item.addView(w.icon(icons[i], 20, R.color.at_onv));
            TextView t = ds.text(ds.ctx.getString(names[i]), 15, Ds.REGULAR, R.color.at_on, 20);
            LinearLayout.LayoutParams tp = w.wrap();
            tp.setMarginStart(ds.dp(12));
            item.addView(t, tp);
            item.setClickable(true);
            item.setBackground(ds.pressable());
            item.setOnClickListener(v -> {
                Anim.dismiss(ds, host, layer);
                actions[k].run();
            });
            box.addView(item, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ds.dp(44)));
        }
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        bp.topMargin = ds.dp(52);
        bp.setMarginEnd(ds.dp(12));
        layer.addView(box, bp);
        layer.setOnClickListener(v -> Anim.dismiss(ds, host, layer));
        box.post(() -> Anim.pop(ds, box, box.getWidth(), 0, 160));
        return layer;
    }

    /** {@code #dlg}: a scrim, and a box 316 dp wide with a heading, a sentence and two text buttons. */
    static View dialog(final Widgets w, final FrameLayout host, CharSequence heading, CharSequence text,
            CharSequence cancel, CharSequence confirm, final Runnable onConfirm) {
        final Ds ds = w.ds;
        final FrameLayout layer = new FrameLayout(ds.ctx);
        layer.setBackgroundColor(ds.color(R.color.at_scrim_dialog));
        layer.setClickable(true);
        final LinearLayout box = new LinearLayout(ds.ctx);
        box.setId(R.id.at_dialog);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(ds.dp(24), ds.dp(24), ds.dp(24), ds.dp(14));
        box.setBackground(ds.box(R.color.at_card, 24, R.color.at_bd));
        box.setClickable(true);
        TextView h = ds.text(heading, 22, Ds.MEDIUM, R.color.at_on, 28);
        LinearLayout.LayoutParams hp = w.wrap();
        hp.bottomMargin = ds.dp(8);
        box.addView(h, hp);
        box.addView(ds.text(text, 14, Ds.REGULAR, R.color.at_onv, 20), w.wrap());
        LinearLayout actions = new LinearLayout(ds.ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, ds.dp(18), 0, 0);
        TextView no = ds.text(cancel, 14, Ds.MEDIUM, R.color.at_acc, 20);
        TextView yes = ds.text(confirm, 14, Ds.MEDIUM, R.color.at_err, 20);
        for (TextView b : new TextView[] {no, yes}) {
            b.setPadding(ds.dp(14), ds.dp(10), ds.dp(14), ds.dp(10));
            b.setClickable(true);
            b.setBackground(ds.pressable());
        }
        no.setOnClickListener(v -> Anim.dismiss(ds, host, layer));
        yes.setOnClickListener(v -> {
            Anim.dismiss(ds, host, layer);
            onConfirm.run();
        });
        actions.addView(no, w.wrap());
        LinearLayout.LayoutParams yp = w.wrap();
        yp.setMarginStart(ds.dp(8));
        actions.addView(yes, yp);
        box.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        layer.addView(box, new FrameLayout.LayoutParams(ds.dp(316), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        layer.setOnClickListener(v -> Anim.dismiss(ds, host, layer));
        Anim.fadeIn(ds, layer, 200);
        box.post(() -> Anim.pop(ds, box, box.getWidth() / 2f, box.getHeight() / 2f, 240));
        return layer;
    }

    /** {@code #fsheet}: a scrim and a panel that rises from the foot in 340 ms and leaves in 240 ms. */
    static final class FilterSheet extends FrameLayout {
        private final Ds mDs;
        private final FrameLayout mHost;
        private final LinearLayout mPanelBody;
        private final ScrollView mPanel;
        private final View mScrim;
        private final Runnable mDismissed;

        /** @param dismissed run when the sheet has gone */
        FilterSheet(final Widgets w, FrameLayout host, Runnable dismissed) {
            super(w.ds.ctx);
            mDs = w.ds;
            mHost = host;
            mDismissed = dismissed;
            setId(R.id.at_filter_sheet);
            mScrim = new View(mDs.ctx);
            mScrim.setBackgroundColor(mDs.color(R.color.at_scrim_sheet));
            mScrim.setOnClickListener(v -> dismiss());
            addView(mScrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mPanel = new MaxHeightScroll(mDs.ctx, 0.86f);
            mPanel.setVerticalScrollBarEnabled(false);
            mPanel.setBackground(mDs.sheetBox(28));
            LinearLayout column = new LinearLayout(mDs.ctx);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setPadding(mDs.dp(20), 0, mDs.dp(20), mDs.dp(36));
            FrameLayout grab = new FrameLayout(mDs.ctx);
            View bar = new View(mDs.ctx);
            bar.setBackground(mDs.box(R.color.at_handle, 2, 0));
            grab.addView(bar, new FrameLayout.LayoutParams(mDs.dp(34), mDs.dp(4), Gravity.CENTER));
            column.addView(grab, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mDs.dp(22)));
            mPanelBody = new LinearLayout(mDs.ctx);
            mPanelBody.setOrientation(LinearLayout.VERTICAL);
            column.addView(mPanelBody, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            mPanel.addView(column, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            addView(mPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        }

        /** Replaces the content of the panel (the head and the two cards). */
        void setContent(View[] parts) {
            mPanelBody.removeAllViews();
            for (View v : parts) {
                mPanelBody.addView(v);
            }
        }

        void open() {
            mHost.addView(this, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            if (mDs.calm()) {
                return;
            }
            mScrim.setAlpha(0f);
            mScrim.animate().alpha(1f).setDuration(200).start();
            mPanel.post(() -> {
                mPanel.setTranslationY(mPanel.getHeight());
                mPanel.animate().translationY(0f).setDuration(340).setInterpolator(Ds.STANDARD).start();
            });
        }

        void dismiss() {
            mDismissed.run();
            if (mDs.calm()) {
                mHost.removeView(this);
                return;
            }
            mScrim.animate().alpha(0f).setDuration(240).start();
            mPanel.animate().translationY(mPanel.getHeight()).setDuration(240)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f))
                    .withEndAction(() -> mHost.removeView(this)).start();
        }
    }

    /** A scroll view that is never higher than a share of what it stands in. */
    private static final class MaxHeightScroll extends ScrollView {
        private final float mShare;

        MaxHeightScroll(Context context, float share) {
            super(context);
            mShare = share;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int max = Math.round(MeasureSpec.getSize(heightSpec) * mShare);
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
        }
    }
}
