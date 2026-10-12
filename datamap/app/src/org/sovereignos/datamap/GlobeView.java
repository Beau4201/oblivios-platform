// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.PathInterpolator;

import org.sovereignos.datamap.model.Cluster;
import org.sovereignos.datamap.model.GlobeMath;
import org.sovereignos.datamap.model.Initials;
import org.sovereignos.datamap.model.LandData;
import org.sovereignos.datamap.model.MapModel;
import org.sovereignos.datamap.model.LogoPick;
import org.sovereignos.datamap.model.MapSelection;
import org.sovereignos.datamap.model.StartView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The globe. It is drawn only when something changes: a finger moves it, a turn to a chosen place runs, the
 * sheet over it moves, the choice changes, or a new snapshot arrives. There is no timer and no idle
 * animation (MAP-018). What is chosen and what is dimmed is decided by {@link MapSelection}.
 */
final class GlobeView extends View {
    interface Listener {
        void onApp(String pkg);

        void onCompany(String ownerId);

        /** Several companies that stand close together on the map and are shown as one number. */
        void onGroup(List<String> ownerIds);

        /** The marker on the dashed line between two companies; the index is into the model's relations. */
        void onRelation(int index);

        void onNothing();
    }

    /** What stands on the globe: an app at its publisher's base, or a company at its base. */
    private static final class Marker {
        final boolean isApp;
        final String id;          // package name or owner id
        final String name;
        final double lat;
        final double lon;
        final boolean ghost;      // a company that no app contacts directly
        final boolean tracker;
        final String country;
        final int appCount;       // for a company: how many of the user's apps contact it
        float x;
        float y;
        float limb = 1f;          // 1 in the middle of the globe, 0 at the horizon (GlobeMath.limbAlpha)
        boolean visible;

        Marker(boolean isApp, String id, String name, double lat, double lon, boolean ghost,
                boolean tracker, String country, int appCount) {
            this.isApp = isApp;
            this.id = id;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.ghost = ghost;
            this.tracker = tracker;
            this.country = country;
            this.appCount = appCount;
        }
    }

    private static final int HIT_APP = 0;
    private static final int HIT_COMPANY = 1;
    private static final int HIT_GROUP = 2;
    private static final int HIT_RELATION = 3;

    /** Where a tap counts, as drawn the last time. */
    private static final class Hit {
        final int kind;
        final float x;
        final float y;
        final float reach;
        final String id;
        final List<String> ids;
        final int index;

        Hit(int kind, float x, float y, float reach, String id, List<String> ids, int index) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.reach = reach;
            this.id = id;
            this.ids = ids;
            this.index = index;
        }
    }

    private static final PathInterpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private final Ui mUi;
    private final Apps mApps;
    private Listener mListener;
    private LandData mLand;
    private MapModel.Output mModel = new MapModel.Output();
    private MapSelection mSel = new MapSelection();
    private Set<String> mVisibleCompanies = new HashSet<>();
    private final List<Marker> mMarkers = new ArrayList<>();
    private final Map<String, Marker> mCompanyMarker = new HashMap<>();
    private final Map<String, Marker> mAppMarker = new HashMap<>();
    private final List<Hit> mHits = new ArrayList<>();

    private double mLat0 = 20;
    private double mLon0 = 10;
    private double mZoom = 1;
    private float mSheetPx;
    private MapSelection.Layout mLayout = MapSelection.layout(1, 1, 0, 1f);
    private ValueAnimator mTurn;

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final Path mBorderPath = new Path();
    private final RectF mRect = new RectF();
    private float[] mTx = new float[0];
    private float[] mTy = new float[0];
    private boolean[] mTv = new boolean[0];
    private boolean mTouched;     // the user has moved the globe: the start view is never applied after that
    private boolean mStartSet;
    private final GestureDetector mGestures;
    private final ScaleGestureDetector mScale;

    GlobeView(Context context, Apps apps) {
        super(context);
        mUi = new Ui(context);
        mApps = apps;
        mStroke.setStyle(Paint.Style.STROKE);
        mBorder.setStyle(Paint.Style.STROKE);
        mBorder.setStrokeWidth(mUi.dp(0.7f));
        mBorder.setStrokeJoin(Paint.Join.ROUND);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setFakeBoldText(true);
        mGestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (mScale.isInProgress()) {
                    return false;
                }
                cancelTurn();
                mTouched = true;
                double deg = 180.0 / Math.PI / radius();
                mLon0 = GlobeMath.wrapLon(mLon0 + dx * deg);
                mLat0 = GlobeMath.clampTilt(mLat0 - dy * deg);
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                tap(e.getX(), e.getY());
                return true;
            }
        });
        mScale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                mTouched = true;
                mZoom = GlobeMath.clampZoom(mZoom * d.getScaleFactor());
                invalidate();
                return true;
            }
        });
    }

    void setListener(Listener l) {
        mListener = l;
    }

    void setLand(LandData land) {
        mLand = land;
        invalidate();
    }

    /** A new snapshot: rebuilds the markers and draws once. */
    void setModel(MapModel.Output model, Map<String, MapModel.Owner> owners) {
        mModel = model;
        mMarkers.clear();
        mCompanyMarker.clear();
        mAppMarker.clear();
        for (MapModel.CompanyRow c : model.companies) {
            Marker m = new Marker(false, c.owner.id, c.owner.name, c.owner.lat, c.owner.lon,
                    !c.contacted, MapModel.isTracker(c.category), c.owner.countryCode, c.apps.size());
            mMarkers.add(m);
            mCompanyMarker.put(c.owner.id, m);
        }
        for (MapModel.AppRow a : model.apps) {
            MapModel.Owner o = a.onMap ? owners.get(a.publisherId) : null;
            if (o != null) {
                Marker m = new Marker(true, a.pkg, mApps.label(a.pkg), o.lat, o.lon, false, false,
                        o.countryCode, 0);
                mMarkers.add(m);
                mAppMarker.put(a.pkg, m);
            }
        }
        if (!mStartSet && !mTouched) {
            List<double[]> places = new ArrayList<>();
            for (MapModel.CompanyRow c : model.companies) {
                if (c.contacted) {
                    places.add(new double[] {c.owner.lat, c.owner.lon});
                }
            }
            if (!places.isEmpty()) {
                double[] view = StartView.of(places);
                mLat0 = view[0];
                mLon0 = view[1];
                mStartSet = true;
            }
        }
        refreshVisible();
        invalidate();
    }

    /** The choice changed (or the model did): which companies are in view is decided again. */
    void setSelection(MapSelection selection) {
        mSel = selection;
        refreshVisible();
        invalidate();
    }

    private void refreshVisible() {
        mVisibleCompanies = new HashSet<>(MapSelection.visibleCompanies(mModel, mSel.app()));
    }

    /** The height in pixels of the sheet over the map, while it moves; the globe keeps clear of it. */
    void setSheetHeightPx(float px) {
        mSheetPx = px;
        relayout();
        invalidate();
    }

    private void relayout() {
        if (getWidth() > 0 && getHeight() > 0) {
            mLayout = MapSelection.layout(getWidth(), getHeight(), mSheetPx,
                    getResources().getDisplayMetrics().density);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        relayout();
    }

    // ------------------------------------------------------------------------------- turning

    /** Turns the globe so that a place is in the middle, and zooms in to at least {@code minZoom}. */
    void flyTo(double lat, double lon, double minZoom) {
        cancelTurn();
        mTouched = true;
        final double fromLat = mLat0;
        final double fromLon = mLon0;
        final double fromZoom = mZoom;
        final double dLon = GlobeMath.wrapLon(lon - fromLon);
        final double toLat = GlobeMath.clampTilt(lat);
        final double toZoom = GlobeMath.clampZoom(Math.max(fromZoom, minZoom));
        mTurn = ValueAnimator.ofFloat(0f, 1f);
        mTurn.setDuration(450);
        mTurn.setInterpolator(STANDARD);
        mTurn.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            mLat0 = fromLat + (toLat - fromLat) * t;
            mLon0 = GlobeMath.wrapLon(fromLon + dLon * t);
            mZoom = fromZoom + (toZoom - fromZoom) * t;
            invalidate();
        });
        mTurn.start();
    }

    /** Back to the whole globe, where it is. */
    void zoomOut() {
        cancelTurn();
        final double fromZoom = mZoom;
        mTurn = ValueAnimator.ofFloat(0f, 1f);
        mTurn.setDuration(350);
        mTurn.setInterpolator(STANDARD);
        mTurn.addUpdateListener(a -> {
            mZoom = fromZoom + (1.0 - fromZoom) * (float) a.getAnimatedValue();
            invalidate();
        });
        mTurn.start();
    }

    void turnToCompany(String ownerId) {
        Marker m = mCompanyMarker.get(ownerId);
        if (m != null) {
            flyTo(m.lat, m.lon, 2.2);
        }
    }

    void turnToApp(String pkg) {
        Marker m = mAppMarker.get(pkg);
        if (m != null) {
            flyTo(m.lat, m.lon, 1.5);
        }
    }

    void turnToRelation(int index) {
        if (index < 0 || index >= mModel.relations.size()) {
            return;
        }
        MapModel.Relation r = mModel.relations.get(index);
        Marker a = mCompanyMarker.get(r.ownerA);
        Marker b = mCompanyMarker.get(r.ownerB);
        if (a != null && b != null) {
            double[] mid = GlobeMath.arc(a.lat, a.lon, b.lat, b.lon, 2, 0)[1];
            flyTo(mid[0], mid[1], GlobeMath.zoomForSpan(GlobeMath.angle(a.lat, a.lon, b.lat, b.lon)));
        }
    }

    void turnToGroup(List<String> ownerIds) {
        double sx = 0;
        double sy = 0;
        double lat = 0;
        int n = 0;
        for (String id : ownerIds) {
            Marker m = mCompanyMarker.get(id);
            if (m != null) {
                sx += Math.cos(Math.toRadians(m.lon));
                sy += Math.sin(Math.toRadians(m.lon));
                lat += m.lat;
                n++;
            }
        }
        if (n > 0) {
            flyTo(lat / n, Math.toDegrees(Math.atan2(sy, sx)), 2.2);
        }
    }

    private void cancelTurn() {
        if (mTurn != null) {
            mTurn.cancel();
            mTurn = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelTurn();
        super.onDetachedFromWindow();
    }

    // ----------------------------------------------------------------------------- geometry

    private float cx() {
        return getWidth() / 2f;
    }

    private float cy() {
        return mLayout.centerY;
    }

    private float radius() {
        return (float) (mLayout.baseRadius * mZoom);
    }

    private void place(Marker m) {
        GlobeMath.P p = GlobeMath.project(m.lat, m.lon, mLat0, mLon0);
        m.visible = p.visible();
        m.limb = (float) GlobeMath.limbAlpha(p.depth);
        m.x = (float) (cx() + p.x * radius());
        m.y = (float) (cy() - p.y * radius());
    }

    // ------------------------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        mScale.onTouchEvent(e);
        mGestures.onTouchEvent(e);
        return true;
    }

    private void tap(float x, float y) {
        Hit hit = null;
        float best = Float.MAX_VALUE;
        for (Hit h : mHits) {
            float dx = h.x - x;
            float dy = h.y - y;
            float d2 = dx * dx + dy * dy;
            if (d2 <= h.reach * h.reach && d2 < best) {
                best = d2;
                hit = h;
            }
        }
        if (mListener == null) {
            return;
        }
        if (hit == null) {
            mListener.onNothing();
        } else if (hit.kind == HIT_APP) {
            mListener.onApp(hit.id);
        } else if (hit.kind == HIT_GROUP) {
            mListener.onGroup(hit.ids);
        } else if (hit.kind == HIT_RELATION) {
            mListener.onRelation(hit.index);
        } else {
            mListener.onCompany(hit.id);
        }
    }

    // ------------------------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas canvas) {
        float r = radius();
        float cx = cx();
        float cy = cy();
        mHits.clear();
        int line = withAlpha(mUi.color(R.color.dm_pri), 0.40f);   // GlobeMath.limbAlphaAtRadius: 1 at 0.94, 0.5 at 0.985, 0 at the rim
        mBorder.setShader(new RadialGradient(cx, cy, r,
                new int[] {line, line, withAlpha(line, 0.5f), withAlpha(line, 0f)},
                new float[] {0f, 0.94f, 0.985f, 1f}, Shader.TileMode.CLAMP));

        // the sphere
        mFill.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f,
                mUi.color(R.color.dm_row), mUi.color(R.color.dm_hi), Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, mFill);
        mFill.setShader(null);

        canvas.save();
        mPath.reset();
        mPath.addCircle(cx, cy, r, Path.Direction.CW);
        canvas.clipPath(mPath);
        drawLand(canvas, r, cx, cy);
        drawRelations(canvas, r, cx, cy);
        drawEdges(canvas, r, cx, cy);
        canvas.restore();

        mStroke.setPathEffect(null);
        mStroke.setStrokeWidth(mUi.dp(1));
        mStroke.setColor(mUi.color(R.color.dm_olv));
        canvas.drawCircle(cx, cy, r, mStroke);

        drawMarkers(canvas);
    }

    private void drawLand(Canvas canvas, float r, float cx, float cy) {
        if (mLand == null) {
            return;
        }
        mFill.setColor(mUi.color(R.color.dm_sc));
        mPath.setFillType(Path.FillType.EVEN_ODD);
        for (int s = 0; s < mLand.shapes.length; s++) {
            if (!mLand.mayBeVisible(s, mLat0, mLon0)) {
                continue;   // wholly on the far side: not projected at all
            }
            mPath.reset();
            mBorderPath.reset();
            boolean any = false;
            for (float[] ring : mLand.shapes[s]) {
                int n = ring.length / 2;
                if (mTx.length < n) {
                    mTx = new float[n];
                    mTy = new float[n];
                    mTv = new boolean[n];
                }
                for (int i = 0; i < n; i++) {
                    GlobeMath.P p = GlobeMath.project(ring[2 * i + 1], ring[2 * i], mLat0, mLon0);
                    double[] q = GlobeMath.toRim(p);
                    mTx[i] = (float) (cx + q[0] * r);
                    mTy[i] = (float) (cy - q[1] * r);
                    mTv[i] = p.visible();
                    any |= mTv[i];
                }
                for (int i = 0; i < n; i++) {
                    if (i == 0) {
                        mPath.moveTo(mTx[i], mTy[i]);
                    } else {
                        mPath.lineTo(mTx[i], mTy[i]);
                    }
                }
                mPath.close();
                // the border: only the pieces whose two ends are in front, so that nothing is drawn along the rim
                boolean pen = false;
                for (int i = 0; i <= n; i++) {
                    int k = i % n;
                    if (!mTv[k]) {
                        pen = false;
                        continue;
                    }
                    if (pen) {
                        mBorderPath.lineTo(mTx[k], mTy[k]);
                    } else {
                        mBorderPath.moveTo(mTx[k], mTy[k]);
                    }
                    pen = true;
                }
            }
            if (any) {
                canvas.drawPath(mPath, mFill);
                canvas.drawPath(mBorderPath, mBorder);
            }
        }
        mPath.setFillType(Path.FillType.WINDING);
    }

    private void drawEdges(Canvas canvas, float r, float cx, float cy) {
        mStroke.setPathEffect(null);
        for (MapModel.Edge e : mModel.edges) {
            float alpha = mSel.edgeAlpha(e.pkg, e.ownerId);
            Marker from = mAppMarker.get(e.pkg);
            Marker to = mCompanyMarker.get(e.ownerId);
            if (alpha <= 0f || from == null || to == null || !mVisibleCompanies.contains(e.ownerId)) {
                continue;
            }
            mStroke.setStrokeWidth(mUi.dp(mSel.edgeWidthDp(e.pkg, e.ownerId)));
            mStroke.setColor(withAlpha(mUi.color(to.tracker ? R.color.dm_err : R.color.dm_pri), alpha));
            drawArc(canvas, from, to, 0.25, r, cx, cy);
        }
    }

    /** The dashed lines between companies, each with a marker in the middle that can be tapped. */
    private void drawRelations(Canvas canvas, float r, float cx, float cy) {
        List<Integer> shown = MapSelection.visibleRelations(mModel, mSel.app());
        for (int index : shown) {
            MapModel.Relation rel = mModel.relations.get(index);
            Marker a = mCompanyMarker.get(rel.ownerA);
            Marker b = mCompanyMarker.get(rel.ownerB);
            if (a == null || b == null) {
                continue;
            }
            boolean chosen = mSel.relation() == index;
            float alpha = mSel.relation() < 0 || chosen ? 0.9f : 0.3f;
            mStroke.setPathEffect(new DashPathEffect(new float[] {mUi.dp(5), mUi.dp(4)}, 0));
            mStroke.setStrokeWidth(mUi.dp(chosen ? 2.5f : 1.5f));
            mStroke.setColor(withAlpha(mUi.color(chosen ? R.color.dm_pri : R.color.dm_ol), alpha));
            float[] mid = drawArc(canvas, a, b, 0.12, r, cx, cy);
            mStroke.setPathEffect(null);
            if (mid != null) {
                mFill.setColor(withAlpha(mUi.color(R.color.dm_hi), alpha));
                canvas.drawCircle(mid[0], mid[1], mUi.dp(6), mFill);
                mStroke.setStrokeWidth(mUi.dp(1.5f));
                mStroke.setColor(withAlpha(mUi.color(chosen ? R.color.dm_pri : R.color.dm_ol), alpha));
                canvas.drawCircle(mid[0], mid[1], mUi.dp(6), mStroke);
                mHits.add(new Hit(HIT_RELATION, mid[0], mid[1], mUi.dp(20), null, null, index));
            }
        }
        mStroke.setPathEffect(null);
    }

    /**
     * A great-circle arc that lifts off the globe in the middle; drawn where both ends of a piece are in
     * front.
     *
     * @return the middle of the arc if it is in front, else null
     */
    private float[] drawArc(Canvas canvas, Marker a, Marker b, double lift, float r, float cx, float cy) {
        double[][] pts = GlobeMath.arc(a.lat, a.lon, b.lat, b.lon, 24, lift);
        float px = 0;
        float py = 0;
        boolean prevVisible = false;
        float[] mid = null;
        for (int i = 0; i < pts.length; i++) {
            double[] p = pts[i];
            GlobeMath.P q = GlobeMath.project(p[0], p[1], mLat0, mLon0);
            float scale = (float) (1.0 + p[2]);
            float x = (float) (cx + q.x * r * scale);
            float y = (float) (cy - q.y * r * scale);
            boolean visible = q.visible();
            if (visible && prevVisible) {
                canvas.drawLine(px, py, x, y, mStroke);
            }
            if (i == pts.length / 2 && visible) {
                mid = new float[] {x, y};
            }
            px = x;
            py = y;
            prevVisible = visible;
        }
        return mid;
    }

    private void drawMarkers(Canvas canvas) {
        MapModel.Relation chosenRel = mSel.relation() >= 0 && mSel.relation() < mModel.relations.size()
                ? mModel.relations.get(mSel.relation()) : null;
        Set<String> appsOfCompany = null;
        if (mSel.company() != null) {
            for (MapModel.CompanyRow c : mModel.companies) {
                if (c.owner.id.equals(mSel.company())) {
                    appsOfCompany = c.apps;
                }
            }
        }
        List<Marker> shown = new ArrayList<>();
        for (Marker m : mMarkers) {
            if (!m.isApp && !mVisibleCompanies.contains(m.id)) {
                continue;
            }
            place(m);
            if (m.visible && m.limb > 0f) {
                shown.add(m);
            }
        }
        double[] xs = new double[shown.size()];
        double[] ys = new double[shown.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = shown.get(i).x;
            ys[i] = shown.get(i).y;
        }
        int[] leader = Cluster.group(xs, ys, mUi.dp(30));
        int[] size = new int[xs.length];
        for (int l : leader) {
            size[l]++;
        }
        for (int i = 0; i < xs.length; i++) {
            if (leader[i] != i) {
                continue;
            }
            Marker m = shown.get(i);
            float half = mUi.dp(m.isApp ? 16 : 18);
            if (m.isApp) {
                float alpha = mSel.appAlpha(m.id, appsOfCompany) * m.limb;
                mFill.setColor(withAlpha(mUi.color(R.color.dm_icbg), alpha));
                canvas.drawRoundRect(m.x - half, m.y - half, m.x + half, m.y + half, mUi.dp(10), mUi.dp(10), mFill);
                Drawable icon = size[i] == 1 ? mApps.icon(m.id) : null;
                if (icon != null) {
                    icon.setBounds((int) (m.x - half), (int) (m.y - half), (int) (m.x + half), (int) (m.y + half));
                    icon.setAlpha((int) (alpha * 255));
                    icon.draw(canvas);
                    icon.setAlpha(255);
                } else {
                    drawLabel(canvas, size[i] == 1 ? Initials.of(m.name) : String.valueOf(size[i]), m.x, m.y,
                            R.color.dm_icfg, 13, alpha);
                }
                if (m.id.equals(mSel.app())) {
                    ring(canvas, m.x, m.y, half + mUi.dp(3), true);
                }
            } else {
                // Several markers stand together: one logo, that of the company most of the user's apps contact,
                // and a badge with the number of the others (LogoPick).
                int[] members = new int[size[i]];
                int k = 0;
                for (int j = 0; j < leader.length; j++) {
                    if (leader[j] == i) {
                        members[k++] = j;
                    }
                }
                String[] ids = new String[members.length];
                boolean[] isApp = new boolean[members.length];
                int[] appCount = new int[members.length];
                for (int j = 0; j < members.length; j++) {
                    Marker mm = shown.get(members[j]);
                    ids[j] = mm.id;
                    isApp[j] = mm.isApp;
                    appCount[j] = mm.appCount;
                }
                int pick = LogoPick.representative(ids, isApp, appCount);
                Marker rep = pick < 0 ? m : shown.get(members[pick]);
                boolean dim = mSel.companyDimmed(rep.id, chosenRel == null ? null : chosenRel.ownerA,
                        chosenRel == null ? null : chosenRel.ownerB);
                float alpha = (dim ? 0.45f : 1f) * m.limb;
                drawCompany(canvas, m.x, m.y, half, rep, alpha);
                int extra = LogoPick.extra(size[i]);
                if (extra > 0) {
                    drawBadge(canvas, m.x + half * 0.8f, m.y - half * 0.8f, extra, alpha);
                }
                Drawable flag = size[i] == 1 ? Flags.of(getContext(), m.country) : null;
                if (flag != null) {
                    int fx = (int) (m.x + half * 0.5f);
                    int fy = (int) (m.y - half - mUi.dp(6));
                    flag.setBounds(fx, fy, fx + mUi.dp(20), fy + mUi.dp(15));
                    flag.setAlpha((int) (alpha * 255));
                    flag.draw(canvas);
                    flag.setAlpha(255);
                }
                if (size[i] == 1 && m.id.equals(mSel.company())) {
                    ring(canvas, m.x, m.y, half + mUi.dp(4), false);
                }
            }
            if (GlobeMath.tappable(m.limb)) {
                addMarkerHit(shown, leader, i);
            }
        }
    }

    /**
     * A company on the globe: its logo on a light round backing inside a ring (red for a tracker, dashed for a
     * company that no app contacts directly), or, with no logo shipped for it, the filled circle with its initials.
     */
    private void drawCompany(Canvas canvas, float x, float y, float half, Marker m, float alpha) {
        int ringColor = mUi.color(m.tracker ? R.color.dm_err : R.color.dm_pri);
        Drawable logo = Logos.of(getContext(), m.id);
        if (logo != null) {
            mFill.setColor(withAlpha(mUi.color(R.color.dm_logo_bg), alpha));
            canvas.drawCircle(x, y, half, mFill);
            float inset = half * 0.28f;
            logo.setBounds((int) (x - half + inset), (int) (y - half + inset), (int) (x + half - inset),
                    (int) (y + half - inset));
            logo.setAlpha((int) (alpha * 255));
            logo.draw(canvas);
            logo.setAlpha(255);
            if (m.ghost) {
                mStroke.setPathEffect(new DashPathEffect(new float[] {mUi.dp(4), mUi.dp(3)}, 0));
            } else {
                mStroke.setPathEffect(null);
            }
            mStroke.setStrokeWidth(mUi.dp(2));
            mStroke.setColor(withAlpha(ringColor, alpha));
            canvas.drawCircle(x, y, half, mStroke);
            mStroke.setPathEffect(null);
            return;
        }
        if (m.ghost) {
            mStroke.setPathEffect(new DashPathEffect(new float[] {mUi.dp(4), mUi.dp(3)}, 0));
            mStroke.setStrokeWidth(mUi.dp(2));
            mStroke.setColor(withAlpha(mUi.color(R.color.dm_pri), alpha));
            canvas.drawCircle(x, y, half, mStroke);
            mStroke.setPathEffect(null);
            drawLabel(canvas, Initials.of(m.name), x, y, R.color.dm_pri, 14, alpha);
        } else {
            mFill.setColor(withAlpha(ringColor, alpha));
            canvas.drawCircle(x, y, half, mFill);
            drawLabel(canvas, Initials.of(m.name), x, y, R.color.dm_onpri, 14, alpha);
        }
    }

    /** The "+N" of a group: a small dark pill at the corner of the logo. */
    private void drawBadge(Canvas canvas, float x, float y, int extra, float alpha) {
        String s = "+" + extra;
        mTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11,
                getResources().getDisplayMetrics()));
        float w = Math.max(mUi.dp(22), mTextPaint.measureText(s) + mUi.dp(10));
        float h = mUi.dp(18);
        mRect.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2);
        mFill.setColor(withAlpha(mUi.color(R.color.dm_inv), alpha));
        canvas.drawRoundRect(mRect, h / 2, h / 2, mFill);
        drawLabel(canvas, s, x, y, R.color.dm_oninv, 11, alpha);
    }

    /** A tap on a marker; on a group it is a tap on the group of companies behind it. */
    private void addMarkerHit(List<Marker> shown, int[] leader, int i) {
        Marker m = shown.get(i);
        List<String> companies = new ArrayList<>();
        for (int j = 0; j < leader.length; j++) {
            if (leader[j] == i && !shown.get(j).isApp) {
                companies.add(shown.get(j).id);
            }
        }
        float reach = mUi.dp(24);
        if (companies.size() > 1) {
            mHits.add(new Hit(HIT_GROUP, m.x, m.y, reach, null, companies, -1));
        } else {
            mHits.add(new Hit(m.isApp ? HIT_APP : HIT_COMPANY, m.x, m.y, reach, m.id, null, -1));
        }
    }

    private void ring(Canvas canvas, float x, float y, float radius, boolean rounded) {
        mStroke.setPathEffect(null);
        mStroke.setStrokeWidth(mUi.dp(2));
        mStroke.setColor(mUi.color(R.color.dm_on));
        if (rounded) {
            canvas.drawRoundRect(x - radius, y - radius, x + radius, y + radius, mUi.dp(12), mUi.dp(12), mStroke);
        } else {
            canvas.drawCircle(x, y, radius, mStroke);
        }
    }

    private void drawLabel(Canvas canvas, String s, float x, float y, int colorRes, float sp, float alpha) {
        mTextPaint.setColor(withAlpha(mUi.color(colorRes), alpha));
        mTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                getResources().getDisplayMetrics()));
        canvas.drawText(s, x, y - (mTextPaint.ascent() + mTextPaint.descent()) / 2f, mTextPaint);
    }

    /** The colour with its own opacity multiplied by {@code alpha}. */
    private static int withAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
