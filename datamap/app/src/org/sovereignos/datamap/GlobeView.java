// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.sovereignos.datamap.model.Cluster;
import org.sovereignos.datamap.model.GlobeMath;
import org.sovereignos.datamap.model.Initials;
import org.sovereignos.datamap.model.LandData;
import org.sovereignos.datamap.model.MapModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The globe. It is drawn only when something changes: a finger moves it, a turn to a chosen place runs,
 * or a new snapshot arrives. There is no timer and no idle animation (MAP-018).
 */
final class GlobeView extends View {
    interface Listener {
        void onApp(String pkg);

        void onCompany(String ownerId);

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
        float x;
        float y;
        boolean visible;

        Marker(boolean isApp, String id, String name, double lat, double lon, boolean ghost,
                boolean tracker, String country) {
            this.isApp = isApp;
            this.id = id;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.ghost = ghost;
            this.tracker = tracker;
            this.country = country;
        }
    }

    private final Ui mUi;
    private final Apps mApps;
    private Listener mListener;
    private LandData mLand;
    private MapModel.Output mModel = new MapModel.Output();
    private final List<Marker> mMarkers = new ArrayList<>();
    private final Map<String, Marker> mCompanyMarker = new HashMap<>();
    private final Map<String, Marker> mAppMarker = new HashMap<>();

    private double mLat0 = 20;
    private double mLon0 = 10;
    private double mZoom = 1;
    private ValueAnimator mTurn;

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final GestureDetector mGestures;
    private final ScaleGestureDetector mScale;

    GlobeView(Context context, Apps apps) {
        super(context);
        mUi = new Ui(context);
        mApps = apps;
        mStroke.setStyle(Paint.Style.STROKE);
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
                    !c.contacted, MapModel.isTracker(c.category), c.owner.countryCode);
            mMarkers.add(m);
            mCompanyMarker.put(c.owner.id, m);
        }
        for (MapModel.AppRow a : model.apps) {
            MapModel.Owner o = a.onMap ? owners.get(a.publisherId) : null;
            if (o != null) {
                Marker m = new Marker(true, a.pkg, mApps.label(a.pkg), o.lat, o.lon, false, false,
                        o.countryCode);
                mMarkers.add(m);
                mAppMarker.put(a.pkg, m);
            }
        }
        invalidate();
    }

    /** Turns the globe so that a place is in the middle. The only animation; it ends by itself. */
    void turnTo(double lat, double lon) {
        cancelTurn();
        final double fromLat = mLat0;
        final double fromLon = mLon0;
        final double dLon = GlobeMath.wrapLon(lon - fromLon);
        final double toLat = GlobeMath.clampTilt(lat);
        mTurn = ValueAnimator.ofFloat(0f, 1f);
        mTurn.setDuration(350);
        mTurn.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            mLat0 = fromLat + (toLat - fromLat) * t;
            mLon0 = GlobeMath.wrapLon(fromLon + dLon * t);
            invalidate();
        });
        mTurn.start();
    }

    void turnToCompany(String ownerId) {
        Marker m = mCompanyMarker.get(ownerId);
        if (m != null) {
            turnTo(m.lat, m.lon);
        }
    }

    void turnToApp(String pkg) {
        Marker m = mAppMarker.get(pkg);
        if (m != null) {
            turnTo(m.lat, m.lon);
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
        return getHeight() * 0.44f;
    }

    private float radius() {
        return (float) (Math.min(getWidth(), getHeight()) * 0.42 * mZoom);
    }

    private void place(Marker m) {
        GlobeMath.P p = GlobeMath.project(m.lat, m.lon, mLat0, mLon0);
        m.visible = p.visible();
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
        float reach = mUi.dp(24);
        Marker hit = null;
        float best = reach * reach;
        for (Marker m : mMarkers) {
            if (!m.visible) {
                continue;
            }
            float dx = m.x - x;
            float dy = m.y - y;
            float d2 = dx * dx + dy * dy;
            if (d2 <= best) {
                best = d2;
                hit = m;
            }
        }
        if (mListener == null) {
            return;
        }
        if (hit == null) {
            mListener.onNothing();
        } else if (hit.isApp) {
            mListener.onApp(hit.id);
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
        for (float[][] shape : mLand.shapes) {
            mPath.reset();
            boolean any = false;
            for (float[] ring : shape) {
                for (int i = 0; i < ring.length; i += 2) {
                    GlobeMath.P p = GlobeMath.project(ring[i + 1], ring[i], mLat0, mLon0);
                    if (p.visible()) {
                        any = true;
                    }
                    double[] q = GlobeMath.toRim(p);
                    float x = (float) (cx + q[0] * r);
                    float y = (float) (cy - q[1] * r);
                    if (i == 0) {
                        mPath.moveTo(x, y);
                    } else {
                        mPath.lineTo(x, y);
                    }
                }
                mPath.close();
            }
            if (any) {
                canvas.drawPath(mPath, mFill);
            }
        }
        mPath.setFillType(Path.FillType.WINDING);
    }

    private void drawEdges(Canvas canvas, float r, float cx, float cy) {
        mStroke.setPathEffect(null);
        mStroke.setStrokeWidth(mUi.dp(1.5f));
        for (MapModel.Edge e : mModel.edges) {
            Marker from = mAppMarker.get(e.pkg);
            Marker to = mCompanyMarker.get(e.ownerId);
            if (from == null || to == null) {
                continue;
            }
            mStroke.setColor(withAlpha(mUi.color(to.tracker ? R.color.dm_err : R.color.dm_pri), 0.75f));
            drawArc(canvas, from, to, 0.25, r, cx, cy);
        }
    }

    private void drawRelations(Canvas canvas, float r, float cx, float cy) {
        mStroke.setPathEffect(new DashPathEffect(new float[] {mUi.dp(5), mUi.dp(4)}, 0));
        mStroke.setStrokeWidth(mUi.dp(1.5f));
        mStroke.setColor(withAlpha(mUi.color(R.color.dm_ol), 0.9f));
        for (MapModel.Relation rel : mModel.relations) {
            Marker a = mCompanyMarker.get(rel.ownerA);
            Marker b = mCompanyMarker.get(rel.ownerB);
            if (a != null && b != null) {
                drawArc(canvas, a, b, 0.12, r, cx, cy);
            }
        }
        mStroke.setPathEffect(null);
    }

    /** A great-circle arc that lifts off the globe in the middle; drawn where both ends of a piece are in front. */
    private void drawArc(Canvas canvas, Marker a, Marker b, double lift, float r, float cx, float cy) {
        double[][] pts = GlobeMath.arc(a.lat, a.lon, b.lat, b.lon, 24, lift);
        float px = 0;
        float py = 0;
        boolean prevVisible = false;
        for (double[] p : pts) {
            GlobeMath.P q = GlobeMath.project(p[0], p[1], mLat0, mLon0);
            float scale = (float) (1.0 + p[2]);
            float x = (float) (cx + q.x * r * scale);
            float y = (float) (cy - q.y * r * scale);
            boolean visible = q.visible();
            if (visible && prevVisible) {
                canvas.drawLine(px, py, x, y, mStroke);
            }
            px = x;
            py = y;
            prevVisible = visible;
        }
    }

    private void drawMarkers(Canvas canvas) {
        List<Marker> shown = new ArrayList<>();
        for (Marker m : mMarkers) {
            place(m);
            if (m.visible) {
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
                mFill.setColor(mUi.color(R.color.dm_icbg));
                canvas.drawRoundRect(m.x - half, m.y - half, m.x + half, m.y + half, mUi.dp(10), mUi.dp(10), mFill);
                Drawable icon = size[i] == 1 ? mApps.icon(m.id) : null;
                if (icon != null) {
                    icon.setBounds((int) (m.x - half), (int) (m.y - half), (int) (m.x + half), (int) (m.y + half));
                    icon.draw(canvas);
                } else {
                    drawLabel(canvas, size[i] == 1 ? Initials.of(m.name) : String.valueOf(size[i]), m.x, m.y,
                            R.color.dm_icfg, 13);
                }
            } else {
                if (m.ghost) {
                    mStroke.setPathEffect(new DashPathEffect(new float[] {mUi.dp(4), mUi.dp(3)}, 0));
                    mStroke.setStrokeWidth(mUi.dp(2));
                    mStroke.setColor(mUi.color(R.color.dm_pri));
                    canvas.drawCircle(m.x, m.y, half, mStroke);
                    mStroke.setPathEffect(null);
                    drawLabel(canvas, Initials.of(m.name), m.x, m.y, R.color.dm_pri, 14);
                } else {
                    mFill.setColor(mUi.color(m.tracker ? R.color.dm_err : R.color.dm_pri));
                    canvas.drawCircle(m.x, m.y, half, mFill);
                    drawLabel(canvas, size[i] == 1 ? Initials.of(m.name) : String.valueOf(size[i]), m.x, m.y,
                            R.color.dm_onpri, 14);
                }
                Drawable flag = size[i] == 1 ? Flags.of(getContext(), m.country) : null;
                if (flag != null) {
                    int fx = (int) (m.x + half * 0.5f);
                    int fy = (int) (m.y - half - mUi.dp(6));
                    flag.setBounds(fx, fy, fx + mUi.dp(20), fy + mUi.dp(15));
                    flag.draw(canvas);
                }
            }
        }
    }

    private void drawLabel(Canvas canvas, String s, float x, float y, int colorRes, float sp) {
        mTextPaint.setColor(mUi.color(colorRes));
        mTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                getResources().getDisplayMetrics()));
        canvas.drawText(s, x, y - (mTextPaint.ascent() + mTextPaint.descent()) / 2f, mTextPaint);
    }

    private static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | ((int) (alpha * 255) << 24);
    }
}
