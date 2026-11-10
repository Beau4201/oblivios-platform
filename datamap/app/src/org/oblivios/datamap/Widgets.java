// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.oblivios.datamap.model.CompanyColors;
import org.oblivios.datamap.model.Initials;
import org.oblivios.datamap.model.MapModel;

import java.util.List;

/**
 * The parts of the design page's lists and pages (ADR-160): cards with rows, tiles, the stack of app icons, the note
 * bar, the facts row, the empty state. Each part carries an id from ids.xml so that the conformance check can find it.
 */
final class Widgets {
    final Ds ds;
    final Apps apps;

    Widgets(Ds ds, Apps apps) {
        this.ds = ds;
        this.apps = apps;
    }


    LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // ------------------------------------------------------------------------------ text

    /** {@code .title}: a 26 sp heading in a row 52 dp high, with something at the end (the filter button) or not. */
    View title(CharSequence text, View end) {
        LinearLayout t = new LinearLayout(ds.ctx);
        t.setId(R.id.at_title);
        t.setOrientation(LinearLayout.HORIZONTAL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = ds.text(text, 26, Ds.MEDIUM, R.color.at_on, 32);
        t.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (end != null) {
            LinearLayout.LayoutParams lp = end.getLayoutParams() != null
                    ? new LinearLayout.LayoutParams(end.getLayoutParams()) : wrap();
            lp.setMarginStart(ds.dp(10));
            t.addView(end, lp);
        }
        t.setMinimumHeight(ds.dp(52));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ds.dp(24);
        t.setLayoutParams(lp);
        return t;
    }

    /** {@code .h3}: a 19 sp heading, 32 dp above and 12 below (the rhythm: 12, 16, 24, 32). */
    TextView h3(CharSequence text) {
        TextView t = ds.text(text, 19, Ds.SEMIBOLD, R.color.at_on, 26);
        t.setId(R.id.at_h3);
        t.setPadding(0, ds.dp(32), 0, ds.dp(12));
        return t;
    }

    /** A heading that stands right under another heading: 4 dp above, so that the two are 16 dp apart. */
    TextView h3Under(CharSequence text) {
        TextView t = h3(text);
        t.setPadding(0, ds.dp(4), 0, ds.dp(12));
        return t;
    }

    /** A text button of the accent colour ({@code .tbtn}): Details, Clear, Reset. */
    TextView textButton(CharSequence text, boolean chevron, View.OnClickListener click) {
        TextView t = ds.text(text, 14, Ds.MEDIUM, R.color.at_acc, 20);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(ds.dp(10), ds.dp(8), ds.dp(4), ds.dp(8));
        if (chevron) {
            Drawable d = ds.ctx.getDrawable(R.drawable.ic_chev);
            d.setTint(ds.color(R.color.at_acc));
            d.setBounds(0, 0, ds.dp(18), ds.dp(18));
            t.setCompoundDrawables(null, null, d, null);
        }
        t.setClickable(true);
        t.setOnClickListener(click);
        t.setBackground(ds.box(0, 10, 0));
        return t;
    }

    ImageView icon(int res, float sizeDp, int tintRes) {
        ImageView v = new ImageView(ds.ctx);
        v.setImageResource(res);
        if (tintRes != 0) {
            v.setImageTintList(ColorStateList.valueOf(ds.color(tintRes)));
        }
        v.setLayoutParams(new LinearLayout.LayoutParams(ds.dp(sizeDp), ds.dp(sizeDp)));
        return v;
    }

    ImageView chevron() {
        return icon(R.drawable.ic_chev, 22, R.color.at_onv);
    }

    /** A round 40 dp button with an icon ({@code .ib}): back, more, close. */
    ImageView iconButton(int res, CharSequence description, View.OnClickListener click) {
        ImageView v = icon(res, 24, R.color.at_on);
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(ds.dp(40), ds.dp(40)));
        v.setContentDescription(description);
        v.setOnClickListener(click);
        v.setClickable(true);
        v.setBackground(ds.pressable());
        v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        return v;
    }

    // ----------------------------------------------------------------------------- note

    /** {@code .info}: a bar with an icon and one line; with {@code click} it also has a chevron. */
    View info(CharSequence text, int iconRes, int iconTint, View.OnClickListener click) {
        LinearLayout b = new LinearLayout(ds.ctx);
        b.setId(R.id.at_note);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setMinimumHeight(ds.dp(36));
        b.setPadding(ds.dp(14), ds.dp(6), ds.dp(14), ds.dp(6));
        b.setBackground(ds.box(R.color.at_info, 12, R.color.at_infobd));
        b.addView(icon(iconRes, 22, iconTint));
        TextView t = ds.text(text, 14, Ds.REGULAR, R.color.at_info_text, 18);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(ds.dp(12));
        b.addView(t, tp);
        if (click != null) {
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ds.dp(22), ds.dp(22));
            cp.setMarginStart(ds.dp(12));
            b.addView(chevron(), cp);
            b.setClickable(true);
            b.setOnClickListener(click);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ds.dp(16);
        b.setLayoutParams(lp);
        return b;
    }

    /** A closing line of the page, not a control: an icon over two centred lines in the muted colour, no box. */
    View footnote(CharSequence text, int iconRes) {
        LinearLayout b = new LinearLayout(ds.ctx);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.setPadding(ds.dp(32), 0, ds.dp(32), 0);
        b.addView(icon(iconRes, 20, R.color.at_onv), new LinearLayout.LayoutParams(ds.dp(20), ds.dp(20)));
        TextView t = ds.text(text, 13, Ds.REGULAR, R.color.at_onv, 18);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = ds.dp(8);
        b.addView(t, tp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ds.dp(24);
        b.setLayoutParams(lp);
        return b;
    }

    /** The line under every list: it opens "What Atlas cannot see". */
    View note(View.OnClickListener openLimits) {
        return info(ds.ctx.getString(R.string.note), R.drawable.ic_info, R.color.at_onv, openLimits);
    }

    // ----------------------------------------------------------------------------- cards

    /** {@code .card}: a rounded box with a border; a row that follows a row has a divider above it. */
    static final class Card extends LinearLayout {
        Card(Ds ds) {
            super(ds.ctx);
            setId(R.id.at_card);
            setOrientation(VERTICAL);
            setBackground(ds.box(R.color.at_card, 14, 0));
            setForeground(ds.box(0, 14, R.color.at_bd));
            setClipToOutline(true);
            final float r = ds.px(14);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
                }
            });
            setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        void add(View child) {
            int n = getChildCount();
            if (child instanceof Row && n > 0 && getChildAt(n - 1) instanceof Row) {
                ((Row) child).divider = true;
            }
            addView(child, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    Card card() {
        return new Card(ds);
    }

    /** {@code .row}: a leading tile, a title with a line under it, and an end; 48 dp, or 58 for a big row. */
    static final class Row extends LinearLayout {
        boolean divider;
        /** The title, so that a row with a long sentence can let it wrap. */
        TextView titleView;
        private final Ds mDs;
        private final Paint mLine = new Paint();

        Row(Ds ds) {
            super(ds.ctx);
            mDs = ds;
            mLine.setColor(ds.color(R.color.at_div));
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (divider) {
                canvas.drawRect(mDs.dp(12), 0, getWidth() - mDs.dp(12), Math.max(1, mDs.dp(1)), mLine);
            }
        }
    }

    /**
     * @param lead the tile at the start, or null
     * @param sub the parts of the line under the title: text, or a view (a flag), 6 dp apart; null for none
     * @param end the view at the end, or null
     * @param click null for a row that does not go anywhere
     */
    Row row(View lead, CharSequence title, Object[] sub, View end, boolean big, View.OnClickListener click) {
        Row r = new Row(ds);
        r.setId(big ? R.id.at_row_big : R.id.at_row);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(ds.dp(big ? 58 : 48));
        r.setPadding(ds.dp(12), 0, ds.dp(14), 0);
        if (lead != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(lead.getLayoutParams());
            lp.setMarginEnd(ds.dp(12));
            r.addView(lead, lp);
        }
        LinearLayout tx = new LinearLayout(ds.ctx);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(0, ds.dp(6), 0, ds.dp(6));
        TextView b = ds.text(title, big ? 16 : 15, Ds.MEDIUM, R.color.at_on, 20);
        b.setSingleLine();
        b.setEllipsize(TextUtils.TruncateAt.END);
        r.titleView = b;
        tx.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (sub != null && sub.length > 0) {
            tx.addView(subLine(sub), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (end != null) {
            LinearLayout.LayoutParams ep = end.getLayoutParams() != null
                    ? new LinearLayout.LayoutParams(end.getLayoutParams()) : wrap();
            ep.setMarginStart(ds.dp(12));
            r.addView(end, ep);
        }
        if (click != null) {
            r.setClickable(true);
            r.setFocusable(true);
            r.setOnClickListener(click);
            r.setBackground(ds.pressable());
        }
        return r;
    }

    /** {@code .tx span}: 13 sp in the secondary colour, the parts 6 dp apart; text may wrap. */
    LinearLayout subLine(Object[] parts) {
        LinearLayout l = new LinearLayout(ds.ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < parts.length; i++) {
            View v = parts[i] instanceof View ? (View) parts[i]
                    : ds.text((CharSequence) parts[i], 13, Ds.REGULAR, R.color.at_onv, 17);
            LinearLayout.LayoutParams lp = parts[i] instanceof View && v.getLayoutParams() != null
                    ? new LinearLayout.LayoutParams(v.getLayoutParams()) : wrap();
            if (i > 0) {
                lp.setMarginStart(ds.dp(6));
            }
            l.addView(v, lp);
        }
        return l;
    }

    /** {@code .end}: text in the secondary colour, then a chevron. */
    LinearLayout end(CharSequence text, boolean chevron) {
        LinearLayout e = new LinearLayout(ds.ctx);
        e.setOrientation(LinearLayout.HORIZONTAL);
        e.setGravity(Gravity.CENTER_VERTICAL);
        if (text != null && text.length() > 0) {
            e.addView(ds.text(text, 13, Ds.REGULAR, R.color.at_onv, 18), wrap());
        }
        if (chevron) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ds.dp(22), ds.dp(22));
            if (e.getChildCount() > 0) {
                lp.setMarginStart(ds.dp(6));
            }
            e.addView(chevron(), lp);
        }
        return e;
    }

    // ------------------------------------------------------------------------------ tiles

    /** A square tile with a rounded corner: a logo or a letter on white (companies), or initials (apps without an icon). */
    static final class TileView extends View {
        private final Ds mDs;
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private final float mRadius;
        private final float mLogoShare;
        private final Drawable mLogo;
        private final String mLetter;

        TileView(Ds ds, int fillRes, int strokeRes, float radiusDp, Drawable logo, float logoShare, String letter,
                int letterColor, float letterSp, int letterColorRes) {
            super(ds.ctx);
            mDs = ds;
            mRadius = ds.px(radiusDp);
            mLogo = logo;
            mLogoShare = logoShare;
            mLetter = letter;
            mFill.setColor(ds.color(fillRes));
            mStroke.setStyle(Paint.Style.STROKE);
            mStroke.setStrokeWidth(Math.max(1, ds.dp(1)));
            mStroke.setColor(strokeRes == 0 ? 0 : ds.color(strokeRes));
            mText.setTypeface(Ds.face(Ds.BOLD));
            mText.setTextAlign(Paint.Align.CENTER);
            mText.setTextSize(ds.px(letterSp));
            mText.setColor(letterColorRes != 0 ? ds.color(letterColorRes) : letterColor);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mRadius);
                }
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            mRect.set(0, 0, w, h);
            canvas.drawRoundRect(mRect, mRadius, mRadius, mFill);
            if (mStroke.getColor() != 0) {
                float half = mStroke.getStrokeWidth() / 2;
                mRect.set(half, half, w - half, h - half);
                canvas.drawRoundRect(mRect, mRadius - half, mRadius - half, mStroke);
            }
            if (mLogo != null) {
                int pad = Math.round(w * (1 - mLogoShare) / 2);
                mLogo.setBounds(pad, pad, Math.round(w) - pad, Math.round(h) - pad);
                mLogo.draw(canvas);
            } else if (mLetter != null) {
                float y = h / 2 - (mText.ascent() + mText.descent()) / 2;
                canvas.drawText(mLetter, w / 2, y, mText);
            }
        }
    }

    /**
     * The tile of a company: its logo on white, or its first letter in its colour on white (the design page). The
     * size is 32 dp in a list, 38 in a big row and 72 on the company page.
     */
    View companyTile(MapModel.CompanyRow co, int sizeDp) {
        boolean pageTile = sizeDp >= 72;
        float radius = pageTile ? 20 : sizeDp >= 38 ? 11 : 9;
        Drawable logo = Logos.of(ds.ctx, co.owner.id);
        String name = co.owner.name == null ? "" : co.owner.name;
        String letter = name.isEmpty() ? "?" : new String(Character.toChars(Character.toUpperCase(name.codePointAt(0))));
        TileView v = new TileView(ds, R.color.at_tile, R.color.at_tilebd, radius, logo, pageTile ? 0.5f : 0.56f,
                letter, CompanyColors.of(co.owner.id, true), pageTile ? 34 : 16, 0);
        v.setId(pageTile ? R.id.at_tile_big : R.id.at_tile);
        v.setLayoutParams(new LinearLayout.LayoutParams(ds.dp(sizeDp), ds.dp(sizeDp)));
        v.setContentDescription(name);
        v.setElevation(ds.px(pageTile ? 2 : 1));
        v.setOutlineSpotShadowColor(ds.color(pageTile ? R.color.at_shadow_tile_big : R.color.at_shadow_tile));
        v.setOutlineAmbientShadowColor(ds.color(pageTile ? R.color.at_shadow_tile_big : R.color.at_shadow_tile));
        return v;
    }

    /** The icon of an app, as its own picture in a tile; initials when the app has no icon. */
    View appTile(String pkg, int sizeDp) {
        float radius = sizeDp >= 72 ? 20 : sizeDp >= 38 ? 11 : sizeDp <= 24 ? 8 : 9;
        Drawable icon = apps.icon(pkg);
        View v;
        if (icon == null) {
            v = new TileView(ds, R.color.at_pill, 0, radius, null, 0, Initials.of(apps.label(pkg)), 0,
                    sizeDp >= 72 ? 28 : 14, R.color.at_on);
        } else {
            ImageView iv = new ImageView(ds.ctx);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            v = iv;
        }
        v.setId(sizeDp >= 72 ? R.id.at_tile_big : R.id.at_tile);
        v.setContentDescription(apps.label(pkg));
        v.setLayoutParams(new LinearLayout.LayoutParams(ds.dp(sizeDp), ds.dp(sizeDp)));
        return v;
    }

    /** {@code .stack}: up to three app icons, each overlapping the one before by 8 dp, then "+n". */
    final class StackView extends View {
        private final String[] mPkgs;
        private final int mExtra;
        private final Drawable[] mIcons;
        private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();

        StackView(List<String> pkgs) {
            super(ds.ctx);
            int shown = Math.min(3, pkgs.size());
            mPkgs = pkgs.subList(0, shown).toArray(new String[0]);
            mExtra = pkgs.size() - shown;
            mIcons = new Drawable[shown];
            for (int i = 0; i < shown; i++) {
                mIcons[i] = apps.icon(mPkgs[i]);
            }
            mRing.setColor(ds.color(R.color.at_card));
            mText.setTypeface(Ds.face(Ds.MEDIUM));
            mText.setTextSize(ds.sp(12));
            mText.setColor(ds.color(R.color.at_onv));
            setId(R.id.at_tile);
            setContentDescription(Texts.apps(ds.ctx, pkgs.size()));
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            float w = ds.px(24) + (mPkgs.length - 1) * ds.px(16);
            if (mExtra > 0) {
                w += ds.px(6) + mText.measureText("+" + mExtra);
            }
            setMeasuredDimension(Math.round(w), ds.dp(28));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = ds.px(24);
            float top = (getHeight() - s) / 2;
            for (int i = 0; i < mPkgs.length; i++) {
                float x = i * ds.px(16);
                float ring = ds.px(2);
                mRect.set(x - ring, top - ring, x + s + ring, top + s + ring);
                canvas.drawRoundRect(mRect, ds.px(10), ds.px(10), mRing);
                mRect.set(x, top, x + s, top + s);
                if (mIcons[i] != null) {
                    mIcons[i].setBounds(Math.round(x), Math.round(top), Math.round(x + s), Math.round(top + s));
                    mIcons[i].draw(canvas);
                } else {
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setColor(ds.color(R.color.at_pill));
                    canvas.drawRoundRect(mRect, ds.px(8), ds.px(8), p);
                }
            }
            if (mExtra > 0) {
                float x = (mPkgs.length - 1) * ds.px(16) + s + ds.px(6);
                canvas.drawText("+" + mExtra, x, getHeight() / 2f - (mText.ascent() + mText.descent()) / 2, mText);
            }
        }
    }

    View stack(List<String> pkgs) {
        return new StackView(pkgs);
    }

    /** A flag, 4 by 3, the height in dp ({@code .flg}); the drawing has its own rounded corners and edge. */
    View flag(String countryCode, float heightDp) {
        Drawable d = Flags.of(ds.ctx, countryCode);
        ImageView v = new ImageView(ds.ctx);
        if (d != null) {
            v.setImageDrawable(d);
        }
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        v.setLayoutParams(new LinearLayout.LayoutParams(ds.dp(heightDp * Flags.ASPECT), ds.dp(heightDp)));
        return v;
    }

    /** {@code appRow}: an app with a line under its name and a chevron. */
    Row appRow(String pkg, CharSequence sub, View.OnClickListener click) {
        return row(appTile(pkg, 32), apps.label(pkg), new Object[] {sub}, chevron(), false, click);
    }

    /** {@code coRow}: a company with its types under its name, a text and a chevron at the end. */
    Row companyRow(MapModel.CompanyRow co, CharSequence end, View.OnClickListener click) {
        return row(companyTile(co, 32), co.owner.name, new Object[] {Texts.types(ds.ctx, co)}, end(end, true), false,
                click);
    }

    // --------------------------------------------------------------------------- page parts

    /** {@code .act}: a card-like row with an icon of the accent colour, a title, a line under it and a chevron. */
    View actionRow(int iconRes, CharSequence title, CharSequence sub, View.OnClickListener click) {
        LinearLayout a = new LinearLayout(ds.ctx);
        a.setId(R.id.at_action_row);
        a.setOrientation(LinearLayout.HORIZONTAL);
        a.setGravity(Gravity.CENTER_VERTICAL);
        a.setMinimumHeight(ds.dp(52));
        a.setPadding(ds.dp(14), 0, ds.dp(14), 0);
        a.setBackground(ds.box(R.color.at_card, 14, R.color.at_bd));
        a.addView(icon(iconRes, 24, R.color.at_acc));
        LinearLayout tx = new LinearLayout(ds.ctx);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(ds.text(title, 15, Ds.MEDIUM, R.color.at_on, 20));
        if (sub != null) {
            tx.addView(ds.text(sub, 13, Ds.REGULAR, R.color.at_onv, 17));
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(ds.dp(12));
        a.addView(tx, tp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ds.dp(22), ds.dp(22));
        cp.setMarginStart(ds.dp(12));
        a.addView(chevron(), cp);
        a.setClickable(true);
        a.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ds.dp(14);
        a.setLayoutParams(lp);
        return a;
    }

    /** {@code .id}: the tile, the name, a line under it and, for a company, the country chip. */
    View identity(View tile, CharSequence name, Object[] sub, CharSequence chip) {
        LinearLayout id = new LinearLayout(ds.ctx);
        id.setOrientation(LinearLayout.HORIZONTAL);
        id.setGravity(Gravity.CENTER_VERTICAL);
        id.setPadding(0, ds.dp(6), 0, 0);
        id.addView(tile, new LinearLayout.LayoutParams(tile.getLayoutParams()));
        LinearLayout t = new LinearLayout(ds.ctx);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(ds.text(name, 22, Ds.SEMIBOLD, R.color.at_on, 28));
        if (sub != null) {
            LinearLayout line = subLine(sub);
            for (int i = 0; i < line.getChildCount(); i++) {
                if (line.getChildAt(i) instanceof TextView) {
                    ((TextView) line.getChildAt(i)).setTextSize(14);
                    ((TextView) line.getChildAt(i)).setLineHeight(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
                }
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = ds.dp(2);
            t.addView(line, lp);
        }
        if (chip != null) {
            LinearLayout c = new LinearLayout(ds.ctx);
            c.setOrientation(LinearLayout.HORIZONTAL);
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setPadding(ds.dp(8), 0, ds.dp(12), 0);
            c.setBackground(ds.box(R.color.at_pill, 14, 0));
            c.addView(icon(R.drawable.ic_pin, 16, R.color.at_onv));
            LinearLayout.LayoutParams tp = wrap();
            tp.setMarginStart(ds.dp(6));
            c.addView(ds.text(chip, 13, Ds.MEDIUM, R.color.at_on, 18), tp);
            c.setMinimumHeight(ds.dp(28));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.topMargin = ds.dp(8);
            t.addView(c, cp);
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(ds.dp(16));
        id.addView(t, tp);
        return id;
    }

    /** {@code .facts}: cells side by side, each a small label over a value, with a line above, below and between. */
    View facts(String[] labels, String[] values) {
        final Paint line = new Paint();
        line.setColor(ds.color(R.color.at_div));
        LinearLayout f = new LinearLayout(ds.ctx) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                float t = Math.max(1, ds.dp(1));
                canvas.drawRect(0, 0, getWidth(), t, line);
                canvas.drawRect(0, getHeight() - t, getWidth(), getHeight(), line);
                for (int i = 1; i < getChildCount(); i++) {
                    canvas.drawRect(getChildAt(i).getLeft(), 0, getChildAt(i).getLeft() + t, getHeight(), line);
                }
            }
        };
        f.setId(R.id.at_facts);
        f.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            LinearLayout cell = new LinearLayout(ds.ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(i == 0 ? 0 : ds.dp(14), ds.dp(12), 0, ds.dp(12));
            cell.addView(ds.text(labels[i], 13, Ds.REGULAR, R.color.at_onv, 18));
            TextView v = ds.text(values[i], 15, Ds.SEMIBOLD, R.color.at_on, 22);
            v.setSingleLine();
            cell.addView(v);
            f.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ds.dp(18);
        f.setLayoutParams(lp);
        return f;
    }

    /** {@code .empty}: an icon, a heading, a line of explanation and, when there is one, a button. */
    View emptyState(int iconRes, CharSequence heading, CharSequence text, CharSequence button, View.OnClickListener click) {
        LinearLayout e = new LinearLayout(ds.ctx);
        e.setId(R.id.at_empty);
        e.setOrientation(LinearLayout.VERTICAL);
        e.setGravity(Gravity.CENTER_HORIZONTAL);
        e.setPadding(ds.dp(12), ds.dp(28), ds.dp(12), ds.dp(8));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ds.dp(44), ds.dp(44));
        ip.bottomMargin = ds.dp(6);
        e.addView(icon(iconRes, 44, R.color.at_acc), ip);
        TextView h = ds.text(heading, 20, Ds.MEDIUM, R.color.at_on, 26);
        h.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hp = wrap();
        hp.bottomMargin = ds.dp(6);
        e.addView(h, hp);
        TextView t = ds.text(text, 14, Ds.REGULAR, R.color.at_onv, 20);
        t.setGravity(Gravity.CENTER);
        t.setMaxWidth(ds.dp(290));
        e.addView(t, wrap());
        if (button != null) {
            TextView b = ds.text(button, 14, Ds.SEMIBOLD, R.color.at_btn_text, 40);
            b.setGravity(Gravity.CENTER);
            b.setPadding(ds.dp(22), 0, ds.dp(22), 0);
            b.setBackground(ds.box(R.color.at_acc, 20, 0));
            b.setClickable(true);
            b.setOnClickListener(click);
            b.setMinimumHeight(ds.dp(40));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = ds.dp(14);
            e.addView(b, bp);
        }
        e.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }
}
