// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.sovereignos.datamap.model.AtlasData;
import org.sovereignos.datamap.model.AtlasModel;
import org.sovereignos.datamap.model.AtlasState;
import org.sovereignos.datamap.model.GlobeLayout;
import org.sovereignos.datamap.model.MapModel;
import org.sovereignos.datamap.model.Motion;
import org.sovereignos.datamap.model.PlaceLinks;
import org.sovereignos.datamap.model.Places;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The Map tab (ADR-160): the globe, the three round controls, and the sheet over it with the three things it shows (all
 * apps, one app, one place). It turns the choices of {@link AtlasState} into what is seen: where the globe flies, which
 * lines grow, how tall the sheet is and whether it is there at all.
 */
final class MapTab extends FrameLayout implements SheetView.Host, GlobeView.Listener {
    private final Ds mDs;
    private final Widgets mW;
    private final AtlasState mState;
    private final Pages.Nav mNav;
    private final GlobeView mGlobe;
    private final SheetView mSheet;
    private final View mZoomControl;
    private final View mCentreControl;
    private final View mFullControl;
    private final ImageView mFullIcon;
    private final Paint mShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBackdropPaint = new Paint();
    private final RectF mRect = new RectF();
    private AtlasData mData = AtlasData.empty();
    private boolean mLaidOut;

    MapTab(Context context, Widgets w, AtlasState state, Pages.Nav nav) {
        super(context);
        mDs = w.ds;
        mW = w;
        mState = state;
        mNav = nav;
        setWillNotDraw(false);
        setClipChildren(true);
        mGlobe = new GlobeView(context, w.apps);
        mGlobe.setListener(this);
        addView(mGlobe, new FrameLayout.LayoutParams(-1, -1));
        mSheet = new SheetView(context, mDs, this);
        addView(mSheet, new FrameLayout.LayoutParams(-1, 0, Gravity.BOTTOM));
        mZoomControl = control(new int[] {R.drawable.ic_plus, R.drawable.ic_minus},
                new int[] {R.string.zoom_in, R.string.zoom_out},
                new Runnable[] {() -> mGlobe.zoomBy(1.25), () -> mGlobe.zoomBy(0.8)}, GlobeLayout.CONTROL_ZOOM, false);
        mCentreControl = control(new int[] {R.drawable.ic_locate}, new int[] {R.string.centre_map},
                new Runnable[] {() -> mGlobe.goHome(Motion.FLY_HOME)}, GlobeLayout.CONTROL_CENTRE, false);
        mFullControl = control(new int[] {R.drawable.ic_full}, new int[] {R.string.map_only},
                new Runnable[] {this::toggleFullScreen}, GlobeLayout.CONTROL_FULL, true);
        mFullIcon = (ImageView) ((LinearLayout) mFullControl).getChildAt(0);
        // the page draws the sheet (z 4) above the controls (z 3): a tall sheet must not leave buttons over its rows
        mSheet.bringToFront();
    }

    /** {@code .ctl}: one to two round-cornered buttons 36 by 34 dp, a line between them. */
    private View control(int[] icons, int[] names, final Runnable[] actions, int which, boolean right) {
        LinearLayout c = new LinearLayout(mDs.ctx);
        c.setId(R.id.at_control);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(mDs.box(R.color.at_btn, 12, R.color.at_btnbd));
        for (int i = 0; i < icons.length; i++) {
            final int k = i;
            FrameLayout b = new FrameLayout(mDs.ctx);
            b.addView(mW.icon(icons[i], 20, R.color.at_on), new FrameLayout.LayoutParams(mDs.dp(20), mDs.dp(20), Gravity.CENTER));
            b.setContentDescription(mDs.ctx.getString(names[i]));
            b.setClickable(true);
            b.setOnClickListener(v -> actions[k].run());
            c.addView(b, new LinearLayout.LayoutParams(mDs.dp(36), mDs.dp(34)));
            if (i < icons.length - 1) {
                View line = new View(mDs.ctx);
                line.setBackgroundColor(mDs.color(R.color.at_btnbd));
                c.addView(line, new LinearLayout.LayoutParams(mDs.dp(36), Math.max(1, mDs.dp(1))));
            }
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | (right ? Gravity.END : Gravity.START));
        lp.topMargin = mDs.dp(GlobeLayout.controlTopDp(which, false));
        lp.setMarginStart(mDs.dp(16));
        lp.setMarginEnd(mDs.dp(16));
        addView(c, lp);
        return c;
    }

    // ------------------------------------------------------------------------------------ data

    void setPrepared(GlobeView.Prepared p) {
        mGlobe.setPrepared(p);
    }

    /** A new snapshot: the globe and the sheet follow it. A chosen place or app that is gone is let go. */
    void setData(AtlasData data) {
        mData = data;
        mGlobe.setData(data);
        if (mState.app() != null && data.atlas.byPkg.get(mState.app()) == null) {
            mState.clearApp();
        }
        mGlobe.setApp(mState.app());
        if (mState.place() != null && !hasPlace(mState.place())) {
            mState.deselectPlace();
        }
        mGlobe.setSelected(mState.place());
        render(false);
    }

    private boolean hasPlace(String key) {
        for (Places.Place p : mGlobe.places()) {
            if (p.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** Puts the globe, the sheet and the controls in the state of {@link AtlasState}, without a flight. */
    void restore() {
        mGlobe.setApp(mState.app());
        mGlobe.setSelected(mState.place());
        mGlobe.setFullScreen(mState.fullScreen(), false);
        applyFullScreen(false);
        render(false);
    }

    // ------------------------------------------------------------------------------- choices

    void chooseApp(String pkg) {
        boolean wasFull = mState.fullScreen();
        mState.chooseApp(pkg);
        mGlobe.setApp(pkg);
        mGlobe.setSelected(null);
        if (wasFull) {
            applyFullScreen(true);
        }
        mGlobe.flyToApp(pkg);
        mGlobe.replayLines();
        render(true);
        sheetHeight(true);
        mNav.stateChanged();
    }

    void clearApp() {
        mState.clearApp();
        mGlobe.setApp(null);
        mGlobe.setSelected(null);
        mGlobe.goHome(Motion.FLY_BACK);
        mGlobe.replayLines();
        render(true);
        mNav.stateChanged();
    }

    void selectPlace(String key) {
        boolean wasFull = mState.fullScreen();
        mState.selectPlace(key);
        mGlobe.setSelected(key);
        if (wasFull) {
            applyFullScreen(true);
        }
        mGlobe.flyToPlace(key);
        mGlobe.replayLines();
        render(true);
        mNav.stateChanged();
    }

    void deselectPlace() {
        mState.deselectPlace();
        mGlobe.setSelected(null);
        mGlobe.goHome(Motion.FLY_BACK);
        mGlobe.replayLines();
        render(true);
        mNav.stateChanged();
    }

    void showCompanyOnMap(String ownerId) {
        String key = mGlobe.placeKeyOfCompany(ownerId);
        if (key == null) {
            return;
        }
        mState.clearApp();
        mGlobe.setApp(null);
        selectPlace(key);
    }

    void toggleFullScreen() {
        mState.toggleFullScreen();
        if (mState.fullScreen()) {
            mGlobe.setSelected(null);
        }
        mGlobe.setFullScreen(mState.fullScreen(), true);
        applyFullScreen(true);
        render(false);
        mNav.stateChanged();
    }

    private void applyFullScreen(boolean animate) {
        boolean fs = mState.fullScreen();
        mSheet.slideAway(fs, animate);
        mFullIcon.setImageResource(fs ? R.drawable.ic_fullx : R.drawable.ic_full);
        ((View) mFullIcon.getParent()).setContentDescription(
                mDs.ctx.getString(fs ? R.string.map_list : R.string.map_only));
        float shift = mDs.px(104);
        for (View v : new View[] {mZoomControl, mCentreControl, mFullControl}) {
            v.animate().cancel();
            if (!animate || mDs.calm()) {
                v.setTranslationY(fs ? shift : 0);
            } else {
                v.animate().translationY(fs ? shift : 0).setDuration(500).setInterpolator(Ds.STANDARD).start();
            }
        }
    }

    /** One step back: the place, then full screen, then the app. @return false if there was nothing to close */
    boolean back() {
        String place = mState.place();
        boolean fs = mState.fullScreen();
        String app = mState.app();
        if (place != null) {
            deselectPlace();
            return true;
        }
        if (fs) {
            toggleFullScreen();
            return true;
        }
        if (app != null) {
            clearApp();
            return true;
        }
        return false;
    }

    boolean canGoBack() {
        return mState.place() != null || mState.fullScreen() || mState.app() != null;
    }

    // --------------------------------------------------------------------------- sheet and map

    @Override
    public void onHandle() {
        mState.toggleSheet();
        sheetHeight(true);
    }

    @Override
    public void onPlace(String key) {
        selectPlace(key);
    }

    @Override
    public void onNothing() {
        if (mState.place() != null) {
            deselectPlace();
        }
    }

    @Override
    public void onAnchor() {
        mGlobe.goHome(Motion.FLY_HOME);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        mLaidOut = true;
        sheetHeight(false);
        applyFullScreen(false);
    }

    private void sheetHeight(boolean animate) {
        if (!mLaidOut) {
            return;
        }
        float hDp = getHeight() / mDs.px(1);
        mSheet.setHeightPx(mDs.dp(GlobeLayout.sheetHeightDp(hDp, mState.sheetTall())), animate);
    }

    /**
     * {@code #tabMap}'s background: an ellipse of light 75 percent of the width and 45 percent of the height wide, centred
     * at 50 and 30 percent.
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        mBackdropPaint.setShader(new android.graphics.RadialGradient(0, 0, 1,
                new int[] {mDs.color(R.color.at_map_glow), mDs.color(R.color.at_map_edge)}, null,
                android.graphics.Shader.TileMode.CLAMP));
        canvas.save();
        canvas.translate(w * 0.5f, h * 0.3f);
        canvas.scale(w * 0.75f, h * 0.45f);
        canvas.drawRect(-10, -10, 10, 10, mBackdropPaint);   // the gradient stops at 1, the rest is its last colour
        canvas.restore();
        super.dispatchDraw(canvas);
    }

    /** {@code #sheet}'s shadow: a soft glow above its top edge, drawn over the globe and under the sheet. */
    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == mSheet && mSheet.getHeight() > 0 && !mState.fullScreen()) {
            mShadow.setColor(mDs.color(R.color.at_bg));
            mShadow.setShadowLayer(mDs.px(40), 0, -mDs.px(10), mDs.color(R.color.at_shadow_sheet));
            mRect.set(mSheet.getLeft(), mSheet.getTop() + mSheet.getTranslationY(), mSheet.getRight(),
                    mSheet.getBottom() + mSheet.getTranslationY());
            canvas.drawRoundRect(mRect, mDs.px(28), mDs.px(28), mShadow);
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    // ------------------------------------------------------------------------- the sheet content

    void render(boolean animate) {
        List<View> parts = new ArrayList<>();
        if (!mData.available || !mData.collecting || mData.nothingCollected()) {
            Context c = mDs.ctx;
            parts.add(!mData.available
                    ? mW.emptyState(R.drawable.ic_eye, c.getString(R.string.service_missing_title),
                            c.getString(R.string.service_missing), null, null)
                    : mData.collecting
                    ? mW.emptyState(R.drawable.ic_eye, c.getString(R.string.empty_nothing_title),
                            c.getString(R.string.empty_map), null, null)
                    : mW.emptyState(R.drawable.ic_eye, c.getString(R.string.off_title), c.getString(R.string.off_map),
                            c.getString(R.string.turn_on), v -> mNav.turnOnCollection()));
        } else if (mState.sheet() == AtlasState.SHEET_PLACE) {
            place(parts, mState.place());
        } else if (mState.sheet() == AtlasState.SHEET_APP) {
            app(parts, mState.app());
        } else {
            all(parts);
        }
        mSheet.setContent(parts.toArray(new View[0]), animate);
    }

    private View header(View lead, CharSequence title, CharSequence small, View end, boolean two) {
        LinearLayout h = new LinearLayout(mDs.ctx);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setMinimumHeight(mDs.dp(48));
        if (lead != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(lead.getLayoutParams());
            lp.setMarginEnd(mDs.dp(10));
            h.addView(lead, lp);
        }
        LinearLayout t = new LinearLayout(mDs.ctx);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView name = mDs.text(title, two ? 22 : 26, Ds.MEDIUM, R.color.at_on, two ? 26 : 32);
        name.setSingleLine();
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(name);
        if (small != null) {
            TextView s = mDs.text(small, 13, Ds.REGULAR, R.color.at_onv, 16);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.topMargin = -mDs.dp(2);
            t.addView(s, sp);
        }
        h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (end != null) {
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(end.getLayoutParams() != null
                    ? end.getLayoutParams() : new ViewGroup.LayoutParams(-2, -2));
            ep.setMarginStart(mDs.dp(10));
            h.addView(end, ep);
        }
        return h;
    }

    private void all(List<View> parts) {
        Context c = mDs.ctx;
        parts.add(header(null, c.getString(R.string.your_apps), null, null, false));
        Widgets.Card card = mW.card();
        for (AtlasModel.App a : mData.atlas.withCompanies) {
            final String pkg = a.pkg;
            card.add(mW.appRow(pkg, Texts.connections(c, a.connections), v -> chooseApp(pkg)));
        }
        parts.add(card);
        parts.add(mW.note(v -> mNav.showLimits()));
    }

    private void app(List<View> parts, final String pkg) {
        Context c = mDs.ctx;
        AtlasModel.App a = mData.atlas.byPkg.get(pkg);
        View back = mW.iconButton(R.drawable.ic_back, c.getString(R.string.back_to_all_apps), v -> clearApp());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(mDs.dp(40), mDs.dp(40));
        bp.setMarginStart(-mDs.dp(8));
        back.setLayoutParams(bp);
        LinearLayout lead = new LinearLayout(c);
        lead.setOrientation(LinearLayout.HORIZONTAL);
        lead.setGravity(Gravity.CENTER_VERTICAL);
        lead.addView(back);
        View icon = mW.appTile(pkg, 32);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(mDs.dp(32), mDs.dp(32));
        ip.setMarginStart(mDs.dp(2));
        lead.addView(icon, ip);
        lead.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int companies = a == null ? 0 : a.companies();
        TextView details = mW.textButton(c.getString(R.string.details), true, v -> mNav.openApp(pkg));
        parts.add(header(lead, mW.apps.label(pkg), Texts.companies(c, companies), details, true));
        Widgets.Card card = mW.card();
        if (a != null) {
            List<MapModel.CompanyRow> rows = new ArrayList<>();
            for (String id : a.perCompany.keySet()) {
                rows.add(mData.byOwner.get(id));
            }
            final AtlasModel.App app = a;
            Collections.sort(rows, new Comparator<MapModel.CompanyRow>() {
                @Override
                public int compare(MapModel.CompanyRow x, MapModel.CompanyRow y) {
                    return app.perCompany.get(y.owner.id) - app.perCompany.get(x.owner.id);
                }
            });
            for (final MapModel.CompanyRow co : rows) {
                card.add(mW.companyRow(co, Texts.connections(c, a.perCompany.get(co.owner.id)),
                        v -> mNav.openCompany(co.owner.id)));
            }
        }
        parts.add(card);
        parts.add(mW.note(v -> mNav.showLimits()));
    }

    private void place(List<View> parts, String key) {
        Context c = mDs.ctx;
        Places.Place place = null;
        for (Places.Place p : mGlobe.places()) {
            if (p.key.equals(key)) {
                place = p;
            }
        }
        if (place == null) {
            return;
        }
        LinearLayout flags = new LinearLayout(c);
        flags.setOrientation(LinearLayout.HORIZONTAL);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < place.countries.size(); i++) {
            names.add(Texts.country(place.countries.get(i)));
            if (i < 2) {
                View f = mW.flag(place.countries.get(i), 30);
                LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(mDs.dp(30), mDs.dp(30));
                fp.setMarginEnd(mDs.dp(i == 0 ? 10 : 0));
                flags.addView(f, fp);
            }
        }
        flags.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View close = mW.iconButton(R.drawable.ic_close, c.getString(R.string.close), v -> deselectPlace());
        parts.add(header(flags, Texts.join(c, names), c.getString(R.string.n_companies_n_apps,
                Texts.companies(c, place.members.size()), Texts.apps(c, place.apps.size())), close, true));
        Widgets.Card card = mW.card();
        for (final MapModel.CompanyRow co : place.members) {
            card.add(mW.companyRow(co, Texts.apps(c, co.apps.size()), v -> mNav.openCompany(co.owner.id)));
        }
        parts.add(card);
        List<PlaceLinks.Joined> joined = PlaceLinks.joined(place.members, mData.atlas.contacted, mData.relations());
        if (!joined.isEmpty()) {
            parts.add(mW.h3(c.getString(R.string.connected_to)));
            Widgets.Card j = mW.card();
            for (final PlaceLinks.Joined e : joined) {
                j.add(mW.row(mW.companyTile(e.other, 32), e.other.owner.name,
                        new Object[] {Texts.via(c, mW.apps, e, place.members)}, mW.chevron(), false,
                        v -> mNav.openCompany(e.other.owner.id)));
            }
            parts.add(j);
        }
        parts.add(mW.note(v -> mNav.showLimits()));
    }
}
