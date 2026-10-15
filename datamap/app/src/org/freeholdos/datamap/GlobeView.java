// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import org.freeholdos.datamap.model.AtlasData;
import org.freeholdos.datamap.model.CardPlacer;
import org.freeholdos.datamap.model.CompanyColors;
import org.freeholdos.datamap.model.Curves;
import org.freeholdos.datamap.model.GlobeLayout;
import org.freeholdos.datamap.model.GlobeMath;
import org.freeholdos.datamap.model.Hits;
import org.freeholdos.datamap.model.LandData;
import org.freeholdos.datamap.model.MapModel;
import org.freeholdos.datamap.model.Motion;
import org.freeholdos.datamap.model.PlaceLinks;
import org.freeholdos.datamap.model.Places;
import org.freeholdos.datamap.model.Rotation;
import org.freeholdos.datamap.model.Seeded;
import org.freeholdos.datamap.model.StartView;
import org.freeholdos.datamap.model.UnitVectors;
import org.freeholdos.datamap.model.Words;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The night globe of the design page (ADR-160): a glow, the ocean, a graticule, land with a glow along its coast, a
 * fine texture, the lights of cities, a shade and a bright rim; over it the lines from "Your apps", the lines between
 * places, the ring points and the cards of the places. It is drawn only while something moves: a finger, a flight, a
 * fling, the growth of the lines, or a new choice. While it moves it draws the land with fewer points (a copy made by
 * {@link LandData#decimated}), and when it has stood still for 120 ms it draws once with all of them. No allocation
 * happens per point of the land.
 */
final class GlobeView extends View {
    interface Listener {
        void onPlace(String key);

        /** A touch that chose no place. */
        void onNothing();

        /** A touch on "Your apps": the map is centred. */
        void onAnchor();
    }

    /** What takes a while to make and is made off the main thread: the land at two detail levels and the decoration. */
    static final class Prepared {
        final LandData full;
        final LandData low;
        final float[][][] ringsFull;
        final float[][][] ringsLow;
        final Seeded.Points texture;
        final Seeded.Points lights;
        /** fill, glow and coast masks of the land for the shader, or null (the paths are drawn then) */
        final android.graphics.Bitmap[] masks;

        private Prepared(LandData full, LandData low, Seeded.Points texture, Seeded.Points lights) {
            this.full = full;
            this.low = low;
            this.ringsFull = rings(full);
            this.ringsLow = rings(low);
            this.texture = texture;
            this.lights = lights;
            this.masks = LandTexture.render(full);
        }

        static Prepared of(LandData land) {
            return new Prepared(land, land.decimated(LOW_TOLERANCE_DEG), Seeded.onLand(Seeded.texture(), land),
                    Seeded.lights());
        }

        private static float[][][] rings(LandData d) {
            float[][][] out = new float[d.shapes.length][][];
            for (int s = 0; s < d.shapes.length; s++) {
                out[s] = new float[d.shapes[s].length][];
                for (int r = 0; r < d.shapes[s].length; r++) {
                    out[s][r] = UnitVectors.ring(d.shapes[s][r]);
                }
            }
            return out;
        }
    }

    /** The land is drawn with points no farther than this from the real outline while the globe moves. */
    static final double LOW_TOLERANCE_DEG = 0.2;
    private static final int SETTLE_MS = 120;
    private static final String PERF_TAG = "AtlasPerf";

    // what may be dropped if the median frame stays above 16 ms on the device (ADR-160, section 10), in this order
    static final boolean DRAW_TEXTURE = true;
    static final boolean DRAW_LIGHTS = true;
    static final boolean DRAW_GLOW = true;

    private final Ds mDs;
    private final Apps mApps;
    private Listener mListener;

    // ---- the data
    private AtlasData mData = AtlasData.empty();
    private String mApp;
    private String mSelected;
    private List<Places.Place> mPlaces = Collections.emptyList();
    private List<PlaceLinks.Pair> mPairs = Collections.emptyList();
    private int[] mPairA = new int[0];
    private int[] mPairB = new int[0];
    /** Per place, made when the data changes and not in every frame: the second line of the card, the flag, the country. */
    private String[] mSub = new String[0];
    private Drawable[] mFlag = new Drawable[0];
    private String[] mCountryName = new String[0];
    private float[][] mPlaceUnit = new float[0][];
    private int[] mPlaceColor = new int[0];
    private float[] mCardW = new float[0];   // in dp, one per place
    private Prepared mLand;

    // ---- the view
    private double mLon = StartView.DEFAULT_LON;
    private double mLat = StartView.DEFAULT_LAT;
    private double mZoom = 1;
    private float mFs;                 // 0 the globe at its place, 1 in full screen
    private float mGrow = 1f;          // the lines, 0 to 1 over 900 ms
    private GlobeLayout mHome;
    private GlobeLayout mFull;
    private boolean mTouched;
    private boolean mStartSet;

    // ---- motion
    private ValueAnimator mFlight;
    private ValueAnimator mGrowAnim;
    private Motion.Fling mFling;
    private long mFlingTime;
    private boolean mMoving;
    private boolean mDown;
    private float mLastX;
    private float mLastY;
    private long mLastEvent;
    private double mVx;
    private double mVy;
    private double mMoved;
    private final ScaleGestureDetector mScale;
    private final Runnable mSettle = () -> {
        // the lines still growing keep the coarse land; their own end settles it
        mMoving = mGrowAnim != null && mGrowAnim.isRunning();
        invalidate();
    };

    // ---- drawing
    private final Paint mP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPoints = new Paint();
    private final Path mClip = new Path();
    private final Path mGrat = new Path();
    private final Path mLandFull = new Path();
    /** The land drawn by one shader on the GPU, or null when it could not be made. */
    private android.graphics.RuntimeShader mLandShader;
    private final Paint mLandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mRows = new float[9];
    private final Path mLandLow = new Path();
    private final Path mLine = new Path();
    private final RectF mRect = new RectF();
    private final float[] mTmp = new float[3];
    private final float[] mLinePts = new float[2 * (Curves.ANCHOR_STEPS + 2)];
    private final float[] mArc = new float[3 * (Curves.ARC_STEPS + 1)];
    private final float[] mArcA = new float[3];
    private final float[] mArcB = new float[3];
    private final float[][] mGratLines;
    private float[] mBatch = new float[0];
    private float mShaderCx = Float.NaN;
    private float mShaderCy;
    private float mShaderR;
    private Shader mGlowShader;
    private Shader mOceanShader;
    private Shader mShadeShader;
    private Shader mRimShader;
    private final Map<String, Drawable> mChipIcons = new HashMap<>();
    private final Drawable mLogo;
    private final Drawable mPin;
    private final boolean mCityLights;
    private final boolean mLight;

    // ---- colours, once
    private final int cGlow;
    private final int cOcean0;
    private final int cOcean1;
    private final int cLand;
    private final int cCoast;
    private final int cCoastGlow;
    private final int cGrid;
    private final int cRim;
    private final int cShade;
    private final int cTex;
    private final int cMarkerCore;
    private final int cPri;
    private final int cLbl;
    private final int cLblbd;
    private final int cShadowCard;
    private final int cOn;
    private final int cOnv;
    private final int cPill;

    // ---- the cards and the touch: what was drawn last, in the order of the cards
    private List<Places.Place> mShownPlaces = Collections.emptyList();
    private float[] mShownX = new float[0];
    private float[] mShownY = new float[0];
    private float[] mShownW = new float[0];
    private float[] mShownPx = new float[0];
    private float[] mShownPy = new float[0];
    private float[] mShownAlpha = new float[0];
    private float mAnchorX;
    private float mAnchorY;

    GlobeView(Context context, Apps apps) {
        super(context);
        mDs = new Ds(context);
        mApps = apps;
        mLight = mDs.light();
        mCityLights = context.getResources().getBoolean(R.bool.at_city_lights);
        cGlow = mDs.color(R.color.at_g_glow);
        cOcean0 = mDs.color(R.color.at_g_o0);
        cOcean1 = mDs.color(R.color.at_g_o1);
        cLand = mDs.color(R.color.at_g_land);
        cCoast = mDs.color(R.color.at_g_coast);
        cCoastGlow = mDs.color(R.color.at_g_coastglow);
        cGrid = mDs.color(R.color.at_g_grid);
        cRim = mDs.color(R.color.at_g_rim);
        cShade = mDs.color(R.color.at_g_shade);
        cTex = mDs.color(R.color.at_g_tex);
        cMarkerCore = mDs.color(R.color.at_marker_core);
        cPri = mDs.color(R.color.at_g_pri);
        cLbl = mDs.color(R.color.at_lbl);
        cLblbd = mDs.color(R.color.at_lblbd);
        cShadowCard = mDs.color(R.color.at_shadow_card);
        cOn = mDs.color(R.color.at_on);
        cOnv = mDs.color(R.color.at_onv);
        cPill = mDs.color(R.color.at_pill);
        mLogo = context.getDrawable(R.drawable.ic_logo);
        mPin = context.getDrawable(R.drawable.ic_pin).mutate();
        mPin.setTint(mDs.color(R.color.at_acc));
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeJoin(Paint.Join.ROUND);
        mStroke.setStrokeCap(Paint.Cap.ROUND);
        mPoints.setStyle(Paint.Style.STROKE);
        mPoints.setStrokeCap(Paint.Cap.SQUARE);
        mText.setTextAlign(Paint.Align.LEFT);
        mGratLines = graticule();
        setContentDescription(context.getString(R.string.globe_description));
        mScale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector d) {
                stopMotion();
                mTouched = true;
                startMoving();
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector d) {
                mZoom = GlobeMath.clampZoom(mZoom * d.getScaleFactor());
                invalidate();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector d) {
                settleSoon();
            }
        });
        setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public AccessibilityNodeProvider getAccessibilityNodeProvider(View host) {
                return mNodes;
            }
        });
    }

    void setListener(Listener l) {
        mListener = l;
    }

    /** The land and the decoration, made by {@link Prepared#of} off the main thread. */
    void setPrepared(Prepared p) {
        mLand = p;
        buildLandShader();
        invalidate();
    }

    // ================================================================================== the data

    /** A new snapshot: the places, the lines and the cards are made again. */
    void setData(AtlasData data) {
        mData = data;
        rebuild();
        if (!mStartSet && !mTouched && !mPlaces.isEmpty()) {
            double[] v = StartView.of(placeLatLons(data.out.places(null)));
            mLat = v[0];
            mLon = v[1];
            mStartSet = true;
        }
        invalidate();
    }

    /** The chosen app (its places and lines only), or null. */
    void setApp(String pkg) {
        mApp = pkg;
        rebuild();
        invalidate();
    }

    /** The chosen place, or null: its lines stay bright and the others dim. */
    void setSelected(String key) {
        mSelected = key;
        invalidate();
    }

    String placeKeyOfCompany(String ownerId) {
        for (Places.Place p : mData.out.places(null)) {
            for (MapModel.CompanyRow m : p.members) {
                if (m.owner.id.equals(ownerId)) {
                    return p.key;
                }
            }
        }
        return null;
    }

    /** The app that is chosen on the map, or null. */
    String app() {
        return mApp;
    }

    List<Places.Place> places() {
        return mPlaces;
    }

    private static List<double[]> placeLatLons(List<Places.Place> places) {
        List<double[]> l = new ArrayList<>();
        for (Places.Place p : places) {
            l.add(new double[] {p.lat(), p.lon()});
        }
        return l;
    }

    private void rebuild() {
        // spec 6: with Collection off, or no service, the globe is drawn without places
        mPlaces = mData.showsPlaces() ? mData.out.places(mApp) : Collections.<Places.Place>emptyList();
        mPairs = PlaceLinks.pairs(mPlaces, mData.relations(), mApp);
        int n = mPlaces.size();
        mPlaceUnit = new float[n][3];
        mPlaceColor = new int[n];
        mCardW = new float[n];
        mSub = new String[n];
        mFlag = new Drawable[n];
        mCountryName = new String[n];
        for (int i = 0; i < n; i++) {
            Places.Place p = mPlaces.get(i);
            UnitVectors.of(p.lat(), p.lon(), mPlaceUnit[i]);
            mPlaceColor[i] = CompanyColors.of(p.key, mLight);
            mCountryName[i] = Texts.country(p.lead.owner.countryCode);
            mSub[i] = Words.join(" · ", mCountryName[i], Texts.apps(getContext(), p.apps.size()));
            mFlag[i] = Flags.of(getContext(), p.lead.owner.countryCode);
            mCardW[i] = cardWidth(i, p);
        }
        // the ends of each line as indices, so that a frame does not search the list
        mPairA = new int[mPairs.size()];
        mPairB = new int[mPairs.size()];
        for (int k = 0; k < mPairs.size(); k++) {
            mPairA[k] = mPlaces.indexOf(mPairs.get(k).a);
            mPairB[k] = mPlaces.indexOf(mPairs.get(k).b);
        }
        mChipIcons.clear();
        mNodes.changed();
    }

    // ============================================================================== the motion

    private void startMoving() {
        mMoving = true;
        removeCallbacks(mSettle);
    }

    private void settleSoon() {
        removeCallbacks(mSettle);
        postDelayed(mSettle, SETTLE_MS);
        invalidate();
    }

    private void stopMotion() {
        if (mFlight != null) {
            mFlight.cancel();
            mFlight = null;
        }
        mFling = null;
    }

    /**
     * Turns the globe to a place and zooms and moves it to full screen, all at once over {@code ms}. A null leaves
     * that part where it is.
     */
    void flyTo(Double lon, Double lat, Double zoom, Float fs, int ms) {
        stopMotion();
        final double fLon = mLon;
        final double fLat = mLat;
        final double fZoom = mZoom;
        final float fFs = mFs;
        final double dLon = lon == null ? 0 : GlobeMath.wrapLon(lon - mLon);
        final double tLat = lat == null ? mLat : GlobeMath.clampTilt(lat);
        final double tZoom = zoom == null ? mZoom : GlobeMath.clampZoom(zoom);
        final float tFs = fs == null ? mFs : fs;
        if (ms <= 0 || mDs.calm()) {
            mLon = GlobeMath.wrapLon(fLon + dLon);
            mLat = tLat;
            mZoom = tZoom;
            mFs = tFs;
            invalidate();
            return;
        }
        startMoving();
        mFlight = ValueAnimator.ofFloat(0f, 1f);
        mFlight.setDuration(ms);
        mFlight.setInterpolator(u -> (float) Motion.inOut(u));
        mFlight.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            mLon = GlobeMath.wrapLon(fLon + dLon * t);
            mLat = fLat + (tLat - fLat) * t;
            mZoom = fZoom + (tZoom - fZoom) * t;
            mFs = fFs + (tFs - fFs) * t;
            invalidate();
        });
        mFlight.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                settleSoon();
            }
        });
        mFlight.start();
    }

    void flyToPlace(String key) {
        for (Places.Place p : mPlaces) {
            if (p.key.equals(key)) {
                flyTo(p.lon(), Math.max(-50.0, Math.min(60.0, p.lat())), null, 0f, Motion.FLY_PLACE);
                return;
            }
        }
    }

    /** Centres between the two places of the app that lie farthest apart, so that both are in front. */
    void flyToApp(String pkg) {
        List<double[]> ll = new ArrayList<>();
        for (Places.Place p : mData.out.places(pkg)) {
            for (MapModel.CompanyRow m : p.members) {
                if (m.apps.contains(pkg)) {
                    ll.add(new double[] {m.owner.lat, m.owner.lon});
                }
            }
        }
        if (ll.isEmpty()) {
            return;
        }
        double[] v = StartView.of(ll);
        flyTo(v[1], Math.max(-40.0, Math.min(48.0, v[0])), null, 0f, Motion.FLY_APP);
    }

    /** The start view, where the globe first looked. */
    void goHome(int ms) {
        double[] v = StartView.of(placeLatLons(mData.out.places(null)));
        flyTo(v[1], v[0], 1.0, null, ms);
    }

    void zoomBy(double factor) {
        flyTo(null, null, GlobeMath.clampZoom(mZoom * factor), null, Motion.ZOOM);
    }

    /** The map alone: the globe moves to the middle of the free space and grows, over 650 ms. */
    void setFullScreen(boolean on, boolean animate) {
        flyTo(null, null, 1.0, on ? 1f : 0f, animate ? Motion.FLY_FULL : 0);
    }

    /** The lines grow out of "Your apps" in 900 ms. */
    void replayLines() {
        if (mGrowAnim != null) {
            mGrowAnim.cancel();
        }
        if (mDs.calm()) {
            mGrow = 1f;
            invalidate();
            return;
        }
        mGrow = 0f;
        startMoving();
        mGrowAnim = ValueAnimator.ofFloat(0f, 1f);
        mGrowAnim.setDuration(Motion.LINES);
        mGrowAnim.setInterpolator(null);
        mGrowAnim.addUpdateListener(a -> {
            mGrow = (float) a.getAnimatedValue();
            invalidate();
        });
        mGrowAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (mFlight == null || !mFlight.isRunning()) {
                    settleSoon();
                }
            }
        });
        mGrowAnim.start();
    }

    private void fling() {
        mFlingTime = 0;
        Choreographer.getInstance().postFrameCallback(mFlingFrame);
    }

    private final Choreographer.FrameCallback mFlingFrame = new Choreographer.FrameCallback() {
        private final double[] mOut = new double[2];

        @Override
        public void doFrame(long nanos) {
            if (mFling == null) {
                return;
            }
            if (mDs.calm()) {
                // the animation setting was switched off during the fling: it stops where it is
                mFling = null;
                settleSoon();
                return;
            }
            long now = nanos / 1_000_000;
            double dt = mFlingTime == 0 ? Motion.FRAME_MS : Math.min(64, now - mFlingTime);
            mFlingTime = now;
            boolean alive = mFling.step(dt, mZoom, mOut);
            mLon = GlobeMath.wrapLon(mLon + mOut[0]);
            mLat = GlobeMath.clampTilt(mLat + mOut[1]);
            invalidate();
            if (alive) {
                Choreographer.getInstance().postFrameCallback(this);
            } else {
                mFling = null;
                settleSoon();
            }
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        stopMotion();
        if (mGrowAnim != null) {
            mGrowAnim.cancel();
        }
        removeCallbacks(mSettle);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float d = mDs.px(1);
        mHome = GlobeLayout.of(w, h, false, d);
        mFull = GlobeLayout.of(w, h, true, d);
        mShaderCx = Float.NaN;
    }

    // ==================================================================================== touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        mScale.onTouchEvent(e);
        if (mScale.isInProgress()) {
            mDown = false;
            return true;
        }
        float density = mDs.px(1);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopMotion();
                mDown = true;
                mLastX = e.getX();
                mLastY = e.getY();
                mLastEvent = e.getEventTime();
                mMoved = 0;
                mVx = 0;
                mVy = 0;
                startMoving();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mDown) {
                    return true;
                }
                double dx = (e.getX() - mLastX) / density;
                double dy = (e.getY() - mLastY) / density;
                long dt = Math.max(1, e.getEventTime() - mLastEvent);
                mMoved += Math.abs(dx) + Math.abs(dy);
                mVx = dx * Motion.FRAME_MS / dt;
                mVy = dy * Motion.FRAME_MS / dt;
                mLastX = e.getX();
                mLastY = e.getY();
                mLastEvent = e.getEventTime();
                if (mMoved > 6) {
                    mTouched = true;
                    double[] d = Motion.drag(dx, dy, mZoom);
                    mLon = GlobeMath.wrapLon(mLon + d[0]);
                    mLat = GlobeMath.clampTilt(mLat + d[1]);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (mDown) {
                    mDown = false;
                    if (mMoved <= 6) {
                        settleSoon();
                        tap(e.getX(), e.getY());
                    } else if (!mDs.calm() && Motion.Fling.starts(mMoved, mVx, mVy)
                            && e.getEventTime() - mLastEvent < 80) {
                        mFling = new Motion.Fling(mVx, mVy);
                        fling();
                    } else {
                        settleSoon();
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                mDown = false;
                settleSoon();
                return true;
            default:
                return true;
        }
    }

    private void tap(float x, float y) {
        float density = mDs.px(1);
        List<Hits.Card> cards = new ArrayList<>();
        List<Hits.Point> points = new ArrayList<>();
        for (int i = 0; i < mShownPlaces.size(); i++) {
            Hits.Card c = new Hits.Card();
            c.id = mShownPlaces.get(i).key;
            c.left = mShownX[i];
            c.top = mShownY[i];
            c.right = mShownX[i] + mShownW[i];
            c.bottom = mShownY[i] + 46 * mDs.grow() * density;
            c.alpha = mShownAlpha[i];
            cards.add(c);
            Hits.Point p = new Hits.Point();
            p.id = c.id;
            p.x = mShownPx[i];
            p.y = mShownPy[i];
            p.reach = 24 * density;
            p.alpha = mShownAlpha[i];
            points.add(p);
        }
        String key = Hits.pick(x, y, cards, points);
        if (mListener == null) {
            return;
        }
        if (key != null) {
            mListener.onPlace(key);
        } else if (Math.hypot(x - mAnchorX, y - mAnchorY) <= 24 * density) {
            mListener.onAnchor();
        } else {
            mListener.onNothing();
        }
    }

    // ================================================================================== drawing

    private GlobeLayout layout() {
        return mHome == null ? GlobeLayout.of(1, 1, false, 1f) : GlobeLayout.lerp(mHome, mFull, mFs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long t0 = SystemClock.elapsedRealtimeNanos();
        float w = getWidth();
        GlobeLayout lay = layout();
        float cx = w / 2f;
        float cy = lay.centerY;
        float baseR = lay.radius;
        float r = baseR * (float) mZoom;
        Rotation rot = new Rotation(mLon, mLat);
        ensureShaders(cx, cy, r);
        drawBase(canvas, cx, cy, r, rot);
        mAnchorX = cx;
        mAnchorY = lay.anchorY();
        drawOverlay(canvas, cx, cy, r, rot);
        if (Log.isLoggable(PERF_TAG, Log.DEBUG)) {
            Log.d(PERF_TAG, "draw " + (SystemClock.elapsedRealtimeNanos() - t0) / 1000 + " us moving=" + mMoving);
        }
    }

    private void ensureShaders(float cx, float cy, float r) {
        if (cx == mShaderCx && cy == mShaderCy && r == mShaderR) {
            return;
        }
        mShaderCx = cx;
        mShaderCy = cy;
        mShaderR = r;
        // the glow outside the rim: from the glow colour at 0.97 R through 16 percent to nothing at 1.2 R
        int clear = Ds.withAlpha(cGlow, 0f);
        int mid = Ds.withAlpha(cGlow, 0.16f / Math.max(0.001f, Color.alpha(cGlow) / 255f));
        mGlowShader = new RadialGradient(cx, cy, r * 1.2f, new int[] {cGlow, mid, clear},
                new float[] {0.97f / 1.2f, (0.97f + 0.45f * 0.23f) / 1.2f, 1f}, Shader.TileMode.CLAMP);
        // the ocean: lit from the upper left
        mOceanShader = new RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 0.1f, cx, cy, r,
                new long[] {Color.pack(cOcean0), Color.pack(cOcean1)}, null, Shader.TileMode.CLAMP);
        mShadeShader = new RadialGradient(cx - r * 0.28f, cy - r * 0.32f, r * 0.25f, cx, cy, r,
                new long[] {Color.pack(0x0AFFFFFF), Color.pack(0x00000000), Color.pack(cShade)},
                new float[] {0f, 0.7f, 1f}, Shader.TileMode.CLAMP);
        mRimShader = new RadialGradient(cx, cy, r, new int[] {Ds.withAlpha(cGlow, 0f), Ds.withAlpha(cGlow, 0f), cGlow},
                new float[] {0f, 0.9f, 1f}, Shader.TileMode.CLAMP);
        mClip.rewind();
        mClip.addCircle(cx, cy, r, Path.Direction.CW);
    }

    private void drawBase(Canvas c, float cx, float cy, float r, Rotation rot) {
        mP.setStyle(Paint.Style.FILL);
        if (DRAW_GLOW) {
            mP.setShader(mGlowShader);
            c.drawCircle(cx, cy, r * 1.2f, mP);
        }
        mP.setShader(mOceanShader);
        c.drawCircle(cx, cy, r, mP);
        mP.setShader(null);
        c.save();
        c.clipPath(mClip);
        // the graticule
        mStroke.setStrokeWidth(mDs.px(0.8f));
        mStroke.setColor(cGrid);
        mGrat.rewind();
        for (float[] line : mGratLines) {
            boolean on = false;
            for (int i = 0; i < line.length; i += 3) {
                rot.apply(line[i], line[i + 1], line[i + 2], mTmp);
                if (mTmp[2] > 0) {
                    float sx = cx + r * mTmp[0];
                    float sy = cy - r * mTmp[1];
                    if (on) {
                        mGrat.lineTo(sx, sy);
                    } else {
                        mGrat.moveTo(sx, sy);
                    }
                    on = true;
                } else {
                    on = false;
                }
            }
        }
        c.drawPath(mGrat, mStroke);
        if (mLand != null) {
            drawLand(c, cx, cy, r, rot);
        }
        // the shade, and a bright rim
        mP.setShader(mShadeShader);
        c.drawCircle(cx, cy, r, mP);
        mP.setShader(mRimShader);
        c.drawCircle(cx, cy, r, mP);
        mP.setShader(null);
        c.restore();
        mStroke.setColor(cRim);
        mStroke.setStrokeWidth(mDs.px(1.6f));
        c.drawCircle(cx, cy, r - mDs.px(0.8f), mStroke);
    }

    private void buildLandShader() {
        mLandShader = null;
        if (mLand == null || mLand.masks == null) {
            return;
        }
        try {
            android.graphics.RuntimeShader s = new android.graphics.RuntimeShader(LandTexture.AGSL);
            String[] names = {"fillTex", "glowTex", "coastTex"};
            for (int i = 0; i < 3; i++) {
                android.graphics.BitmapShader b = new android.graphics.BitmapShader(mLand.masks[i],
                        Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
                b.setFilterMode(android.graphics.BitmapShader.FILTER_MODE_LINEAR);
                s.setInputShader(names[i], b);
            }
            s.setFloatUniform("texSize", (float) LandTexture.W, (float) LandTexture.H);
            s.setColorUniform("landC", cLand);
            s.setColorUniform("glowC", cCoastGlow);
            s.setColorUniform("coastC", cCoast);
            mLandShader = s;
        } catch (RuntimeException e) {
            Log.w(PERF_TAG, "land shader refused, drawing paths: " + e);
        }
    }

    private void drawLand(Canvas c, float cx, float cy, float r, Rotation rot) {
        if (mLandShader != null) {
            rot.rows(mRows);
            mLandShader.setFloatUniform("centre", cx, cy);
            mLandShader.setFloatUniform("radius", r);
            mLandShader.setFloatUniform("rowA", mRows[0], mRows[1], mRows[2]);
            mLandShader.setFloatUniform("rowB", mRows[3], mRows[4], mRows[5]);
            mLandShader.setFloatUniform("rowC", mRows[6], mRows[7], mRows[8]);
            mLandPaint.setShader(mLandShader);
            c.drawCircle(cx, cy, r, mLandPaint);
            drawLandDecoration(c, cx, cy, r, rot);
            return;
        }
        boolean detail = !mMoving;
        Path coast = mLandLow;
        trace(mLandLow, mLand.low, mLand.ringsLow, rot, cx, cy, r);
        Path fill = coast;
        if (detail) {
            trace(mLandFull, mLand.full, mLand.ringsFull, rot, cx, cy, r);
            fill = mLandFull;
        }
        mP.setStyle(Paint.Style.FILL);
        mP.setColor(cLand);
        fill.setFillType(Path.FillType.EVEN_ODD);
        c.drawPath(fill, mP);
        // a wide soft line and a narrower one along every coast, from the coarse outline, then the fine line
        mStroke.setColor(cCoastGlow);
        mStroke.setStrokeWidth(mDs.px(5));
        mStroke.setAlpha(Math.round(alphaOf(cCoastGlow) * 0.35f));
        c.drawPath(coast, mStroke);
        mStroke.setStrokeWidth(mDs.px(2.4f));
        mStroke.setColor(cCoastGlow);
        mStroke.setAlpha(Math.round(alphaOf(cCoastGlow) * 0.7f));
        c.drawPath(coast, mStroke);
        mStroke.setColor(cCoast);
        mStroke.setStrokeWidth(mDs.px(0.6f));
        c.drawPath(fill, mStroke);
        drawLandDecoration(c, cx, cy, r, rot);
    }

    private void drawLandDecoration(Canvas c, float cx, float cy, float r, Rotation rot) {
        if (DRAW_TEXTURE) {
            drawTexture(c, cx, cy, r, rot);
        }
        if (DRAW_LIGHTS && mCityLights) {
            drawLights(c, cx, cy, r, rot);
        }
    }

    private static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    /**
     * The outline of the land turned to the screen. A point behind the globe is put on a circle of 1.5 R at its
     * direction, with points between neighbours so that a shape that passes the horizon is filled up to the rim and
     * not cut off; the clip of the globe hides what lies outside it.
     */
    private void trace(Path path, LandData land, float[][][] rings, Rotation rot, float cx, float cy, float r) {
        path.rewind();
        final float far = r * 1.5f;
        final float[] t = mTmp;
        for (int s = 0; s < rings.length; s++) {
            if (!land.mayBeVisible(s, mLat, mLon)) {
                continue;
            }
            for (float[] a : rings[s]) {
                int n = a.length / 3;
                boolean any = false;
                for (int i = 0; i < n && !any; i++) {
                    any = rot.depth(a[3 * i], a[3 * i + 1], a[3 * i + 2]) >= 0;
                }
                if (!any) {
                    continue;
                }
                boolean started = false;
                boolean prevHidden = false;
                boolean firstHidden = false;
                double prevAngle = 0;
                double firstAngle = 0;
                for (int i = 0; i < n; i++) {
                    rot.apply(a[3 * i], a[3 * i + 1], a[3 * i + 2], t);
                    float px;
                    float py;
                    boolean hidden = false;
                    double angle = 0;
                    if (t[2] >= 0) {
                        px = cx + r * t[0];
                        py = cy - r * t[1];
                    } else {
                        hidden = true;
                        angle = Math.atan2(t[1], t[0]);
                        px = cx + far * (float) Math.cos(angle);
                        py = cy - far * (float) Math.sin(angle);
                    }
                    if (!started) {
                        path.moveTo(px, py);
                        started = true;
                        firstHidden = hidden;
                        firstAngle = angle;
                    } else {
                        if (hidden && prevHidden) {
                            around(path, cx, cy, far, prevAngle, angle);
                        }
                        path.lineTo(px, py);
                    }
                    prevHidden = hidden;
                    prevAngle = angle;
                }
                if (firstHidden && prevHidden) {
                    around(path, cx, cy, far, prevAngle, firstAngle);
                }
                path.close();
            }
        }
    }

    /** Points along the circle of radius {@code far}, from one angle to another the short way, 0.3 rad apart. */
    private static void around(Path path, float cx, float cy, float far, double from, double to) {
        double d = to - from;
        while (d > Math.PI) {
            d -= 2 * Math.PI;
        }
        while (d < -Math.PI) {
            d += 2 * Math.PI;
        }
        int k = (int) Math.ceil(Math.abs(d) / 0.3);
        for (int s = 1; s < k; s++) {
            double aa = from + d * s / k;
            path.lineTo(cx + far * (float) Math.cos(aa), cy - far * (float) Math.sin(aa));
        }
    }

    /** The fine texture of the land: points in three opacities and two sizes, each batch drawn in one call. */
    private void drawTexture(Canvas c, float cx, float cy, float r, Rotation rot) {
        Seeded.Points p = mLand.texture;
        float[] alphas = {0.07f, 0.12f, 0.18f};
        float[] sizes = {0.7f, 1.2f};
        for (int b = 0; b < 6; b++) {
            int n = 0;
            ensureBatch(2 * p.n);
            for (int i = 0; i < p.n; i++) {
                int bucket = (p.alpha[i] < 0.09f ? 0 : p.alpha[i] < 0.14f ? 1 : 2) * 2 + (p.size[i] < 0.95f ? 0 : 1);
                if (bucket != b) {
                    continue;
                }
                rot.apply(p.v[3 * i], p.v[3 * i + 1], p.v[3 * i + 2], mTmp);
                if (mTmp[2] > 0.1f) {
                    mBatch[n++] = cx + r * mTmp[0];
                    mBatch[n++] = cy - r * mTmp[1];
                }
            }
            if (n > 0) {
                mPoints.setColor(Ds.withAlpha(cTex, alphas[b / 2]));
                mPoints.setStrokeWidth(mDs.px(sizes[b % 2]));
                c.drawPoints(mBatch, 0, n, mPoints);
            }
        }
    }

    /** The lights of the cities of the night globe: a faint square and a bright one, by colour and size. */
    private void drawLights(Canvas c, float cx, float cy, float r, Rotation rot) {
        Seeded.Points p = mLand.lights;
        int[] colours = {0xFFFFB040, 0xFFFFE9B8, 0xFF5FE0FF};
        float[] sizes = {0.6f, 0.95f, 1.25f};
        for (int col = 0; col < 3; col++) {
            for (int sz = 0; sz < 3; sz++) {
                int n = 0;
                ensureBatch(2 * p.n);
                for (int i = 0; i < p.n; i++) {
                    int cls = p.size[i] < 0.8f ? 0 : p.size[i] < 1.1f ? 1 : 2;
                    if (p.colour[i] != col || cls != sz) {
                        continue;
                    }
                    rot.apply(p.v[3 * i], p.v[3 * i + 1], p.v[3 * i + 2], mTmp);
                    if (mTmp[2] > 0.08f) {
                        mBatch[n++] = cx + r * mTmp[0];
                        mBatch[n++] = cy - r * mTmp[1];
                    }
                }
                if (n == 0) {
                    continue;
                }
                mPoints.setColor(Ds.withAlpha(colours[col], 0.13f));
                mPoints.setStrokeWidth(mDs.px(sizes[sz] * 3.2f));
                c.drawPoints(mBatch, 0, n, mPoints);
                mPoints.setColor(Ds.withAlpha(colours[col], 0.9f));
                mPoints.setStrokeWidth(mDs.px(sizes[sz]));
                c.drawPoints(mBatch, 0, n, mPoints);
            }
        }
    }

    private void ensureBatch(int floats) {
        if (mBatch.length < floats) {
            mBatch = new float[floats];
        }
    }

    // ---------------------------------------------------------------------- lines, points, cards

    private void drawOverlay(Canvas c, float cx, float cy, float r, Rotation rot) {
        float density = mDs.px(1);
        int n = mPlaces.size();
        float ay = mAnchorY;
        // where each place stands, and how clearly: a place behind the globe or at the horizon is not drawn
        float[] px = new float[n];
        float[] py = new float[n];
        float[] alpha = new float[n];
        for (int i = 0; i < n; i++) {
            rot.apply(mPlaceUnit[i][0], mPlaceUnit[i][1], mPlaceUnit[i][2], mTmp);
            px[i] = cx + r * mTmp[0];
            py[i] = cy - r * mTmp[1];
            alpha[i] = mTmp[2] > 0.03f ? (float) GlobeMath.limbAlpha(mTmp[2]) : 0f;
        }
        boolean focus = mSelected != null;
        // the lines between places, then the lines from "Your apps"
        List<float[]> chips = new ArrayList<>();
        List<PlaceLinks.Pair> chipPairs = new ArrayList<>();
        float e = (float) Motion.outCubic(mGrow);
        for (int k = 0; k < mPairs.size(); k++) {
            PlaceLinks.Pair pair = mPairs.get(k);
            int ia = mPairA[k];
            int ib = mPairB[k];
            if (ia < 0 || ib < 0) {
                continue;
            }
            boolean involved = !focus || pair.a.key.equals(mSelected) || pair.b.key.equals(mSelected);
            // spec 11 A4: with more than 12 pairs only the lines of a chosen place or app are drawn
            if (!PlaceLinks.drawn(mPairs.size(), involved, focus, mApp != null)) {
                continue;
            }
            boolean strong = involved && (focus || mApp != null);
            float al = involved ? (strong ? 1f : 0.75f) : 0.12f;
            float width = (strong ? 2.4f : 1.7f) + (pair.rels.isEmpty() ? 0f : 0.8f)
                    + Math.min(2, pair.apps.size() - 1) * 0.5f;
            float[] mid = surfaceArc(c, cx, cy, r, rot, ia, ib, width, al, e);
            if (mid != null && strong && e >= 0.55f) {
                chips.add(mid);
                chipPairs.add(pair);
            }
        }
        for (int i = 0; i < n; i++) {
            if (alpha[i] <= 0.02f) {
                continue;
            }
            boolean strong = !focus || mPlaces.get(i).key.equals(mSelected);
            anchorLine(c, cx, ay, px[i], py[i], mPlaceColor[i], strong, alpha[i], e);
        }
        // the anchor
        drawAnchor(c, cx, ay);
        // the ring points, top to bottom
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) {
            // an insertion sort by the y of the point: n is small and nothing is allocated
            int j = i;
            while (j > 0 && py[idx[j - 1]] > py[i]) {
                idx[j] = idx[j - 1];
                j--;
            }
            idx[j] = i;
        }
        int[] visible = new int[n];
        int m = 0;
        for (int k = 0; k < n; k++) {
            int i = idx[k];
            if (alpha[i] <= 0.02f) {
                continue;
            }
            drawRingPoint(c, px[i], py[i], mPlaceColor[i], mPlaces.get(i).key.equals(mSelected), alpha[i],
                    mPlaces.get(i).lead.server);
            visible[m++] = i;
        }
        for (int k = 0; k < chips.size(); k++) {
            drawChip(c, chips.get(k), chipPairs.get(k));
        }
        // the cards avoid each other and the points
        float[] bx = new float[m];
        float[] by = new float[m];
        float[] bw = new float[m];
        for (int k = 0; k < m; k++) {
            int i = visible[k];
            bx[k] = px[i] / density;
            by[k] = py[i] / density;
            bw[k] = mCardW[i];
        }
        float[] pos = CardPlacer.place(bx, by, bw, 46 * mDs.grow(), getWidth() / density, getHeight() / density);
        List<Places.Place> shownPlaces = new ArrayList<>();
        float[] sx = new float[m];
        float[] sy = new float[m];
        float[] sw = new float[m];
        float[] spx = new float[m];
        float[] spy = new float[m];
        float[] sa = new float[m];
        for (int k = 0; k < m; k++) {
            int i = visible[k];
            shownPlaces.add(mPlaces.get(i));
            sx[k] = pos[2 * k] * density;
            sy[k] = pos[2 * k + 1] * density;
            sw[k] = mCardW[i] * density;
            spx[k] = px[i];
            spy[k] = py[i];
            sa[k] = alpha[i];
            drawCard(c, i, sx[k], sy[k], sw[k], sa[k]);
        }
        mShownPlaces = shownPlaces;
        mShownX = sx;
        mShownY = sy;
        mShownW = sw;
        mShownPx = spx;
        mShownPy = spy;
        mShownAlpha = sa;
    }

    private void anchorLine(Canvas c, float ax, float ay, float px, float py, int col, boolean strong, float al, float e) {
        int count = Curves.anchorLine(ax, ay, px, py, e, mLinePts);
        mLine.rewind();
        mLine.moveTo(mLinePts[0], mLinePts[1]);
        for (int i = 1; i < count; i++) {
            mLine.lineTo(mLinePts[2 * i], mLinePts[2 * i + 1]);
        }
        int mid = Ds.withAlpha(col, strong ? 0.9f : 0.25f);
        int end = Ds.withAlpha(col, strong ? 1f : 0.3f);
        mStroke.setShader(new LinearGradient(ax, ay, px, py, new int[] {Ds.withAlpha(col, 0.25f), mid, end},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        mStroke.setStrokeWidth(mDs.px(strong ? 7 : 4));
        mStroke.setAlpha(Math.round(255 * al * (strong ? 0.16f : 0.08f)));
        c.drawPath(mLine, mStroke);
        mStroke.setStrokeWidth(mDs.px(strong ? 1.6f : 1.1f));
        mStroke.setAlpha(Math.round(255 * al * (strong ? 1f : 0.55f)));
        c.drawPath(mLine, mStroke);
        mStroke.setShader(null);
        mStroke.setAlpha(255);
    }

    /** The great circle between two places, lifted off the globe; returns its middle when that is in front. */
    private float[] surfaceArc(Canvas c, float cx, float cy, float r, Rotation rot, int ia, int ib, float width,
            float al, float e) {
        System.arraycopy(mPlaceUnit[ia], 0, mArcA, 0, 3);
        System.arraycopy(mPlaceUnit[ib], 0, mArcB, 0, 3);
        Curves.surfaceArc(mArcA, mArcB, mArc);
        rot.apply(mArcA[0], mArcA[1], mArcA[2], mTmp);
        float ax = cx + r * mTmp[0];
        float ay = cy - r * mTmp[1];
        rot.apply(mArcB[0], mArcB[1], mArcB[2], mTmp);
        float bx = cx + r * mTmp[0];
        float by = cy - r * mTmp[1];
        mStroke.setShader(new LinearGradient(ax, ay, bx, by, Ds.withAlpha(mPlaceColor[ia], 0.95f),
                Ds.withAlpha(mPlaceColor[ib], 0.95f), Shader.TileMode.CLAMP));
        int steps = Math.round(Curves.ARC_STEPS * e);
        float[] mid = null;
        for (int pass = 0; pass < 2; pass++) {
            mLine.rewind();
            boolean on = false;
            for (int i = 0; i <= steps; i++) {
                rot.apply(mArc[3 * i], mArc[3 * i + 1], mArc[3 * i + 2], mTmp);
                if (mTmp[2] > -0.02f) {
                    float sx = cx + r * mTmp[0];
                    float sy = cy - r * mTmp[1];
                    if (on) {
                        mLine.lineTo(sx, sy);
                    } else {
                        mLine.moveTo(sx, sy);
                    }
                    on = true;
                    if (pass == 1 && i == Curves.ARC_STEPS / 2) {
                        mid = new float[] {sx, sy};
                    }
                } else {
                    on = false;
                }
            }
            mStroke.setAlpha(Math.round(255 * (pass == 1 ? al : al * 0.18f)));
            mStroke.setStrokeWidth(mDs.px(pass == 1 ? width : width * 4));
            c.drawPath(mLine, mStroke);
        }
        mStroke.setShader(null);
        mStroke.setAlpha(255);
        return mid;
    }

    private void drawAnchor(Canvas c, float x, float y) {
        float half = mDs.px(17);
        mLogo.setBounds(Math.round(x - half), Math.round(y - half), Math.round(x + half), Math.round(y + half));
        mLogo.draw(c);
        String label = getContext().getString(R.string.your_apps);
        mText.setTypeface(Ds.face(Ds.MEDIUM));
        mText.setTextSize(mDs.sp(14));
        float w = mText.measureText(label) + mDs.px(36);
        float top = y + mDs.px(19);
        float ah = mDs.px(26) * mDs.grow();
        mRect.set(x - w / 2, top, x + w / 2, top + ah);
        mP.setStyle(Paint.Style.FILL);
        mP.setColor(cLbl);
        c.drawRoundRect(mRect, mDs.px(13), mDs.px(13), mP);
        mStroke.setColor(cLblbd);
        mStroke.setStrokeWidth(Math.max(1, mDs.dp(1)));
        c.drawRoundRect(mRect, mDs.px(13), mDs.px(13), mStroke);
        mText.setColor(cOn);
        mText.setTextAlign(Paint.Align.CENTER);
        c.drawText(label, x, top + ah / 2 - (mText.ascent() + mText.descent()) / 2, mText);
        mText.setTextAlign(Paint.Align.LEFT);
    }

    /** @param hollow a server place (ADR-161): the ring alone, with nothing in it, where a company's point is filled */
    private void drawRingPoint(Canvas c, float x, float y, int col, boolean selected, float alpha, boolean hollow) {
        float rr = mDs.px(selected ? 8 : 6);
        mP.setStyle(Paint.Style.FILL);
        mP.setShader(new RadialGradient(x, y, rr * 3, Ds.withAlpha(col, 0.55f * alpha), Ds.withAlpha(col, 0f),
                Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rr * 3, mP);
        mP.setShader(null);
        if (selected) {
            mStroke.setColor(Ds.withAlpha(col, 0.5f * alpha));
            mStroke.setStrokeWidth(mDs.px(1.4f));
            c.drawCircle(x, y, rr + mDs.px(6), mStroke);
        }
        if (!hollow) {
            mP.setColor(Ds.withAlpha(cMarkerCore, alpha));
            c.drawCircle(x, y, rr, mP);
        }
        mStroke.setColor(Ds.withAlpha(col, alpha));
        mStroke.setStrokeWidth(mDs.px(2.2f));
        c.drawCircle(x, y, rr, mStroke);
        if (!hollow) {
            mP.setColor(Ds.withAlpha(mLight ? col : 0xFFFFFFFF, alpha));
            c.drawCircle(x, y, rr * 0.38f, mP);
        }
    }

    /** On a bright line: the icon of the app that contacts both places, or a diamond for a documented relation. */
    private void drawChip(Canvas c, float[] mid, PlaceLinks.Pair pair) {
        String first = pair.apps.isEmpty() ? null : pair.apps.iterator().next();
        int appCount = pair.apps.size();
        mP.setStyle(Paint.Style.FILL);
        if (first == null) {
            c.save();
            c.translate(mid[0], mid[1]);
            c.rotate(45);
            mRect.set(-mDs.px(6), -mDs.px(6), mDs.px(6), mDs.px(6));
            mP.setColor(cMarkerCore);
            c.drawRoundRect(mRect, mDs.px(3), mDs.px(3), mP);
            mStroke.setColor(cPri);
            mStroke.setStrokeWidth(mDs.px(2));
            c.drawRoundRect(mRect, mDs.px(3), mDs.px(3), mStroke);
            c.restore();
            return;
        }
        float size = mDs.px(20);
        float x = mid[0] - size / 2;
        float y = mid[1] - size / 2;
        mRect.set(x - mDs.px(2), y - mDs.px(2), x + size + mDs.px(2), y + size + mDs.px(2));
        mP.setColor(cMarkerCore);
        mP.setShadowLayer(mDs.px(6), 0, 0, 0x80000000);
        c.drawRoundRect(mRect, mDs.px(8), mDs.px(8), mP);
        mP.clearShadowLayer();
        Drawable icon = mChipIcons.get(first);
        if (icon == null && !mChipIcons.containsKey(first)) {
            icon = mApps.icon(first);
            mChipIcons.put(first, icon);
        }
        if (icon != null) {
            icon.setBounds(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size));
            icon.draw(c);
        }
        if (appCount > 1) {
            float bx = mid[0] + size / 2;
            float by = mid[1] - size / 2;
            mP.setColor(cPri);
            c.drawCircle(bx, by, mDs.px(7), mP);
            mText.setTypeface(Ds.face(Ds.MEDIUM));
            mText.setTextSize(mDs.sp(10));
            mText.setColor(0xFFFFFFFF);
            mText.setTextAlign(Paint.Align.CENTER);
            c.drawText("+" + (appCount - 1), bx, by - (mText.ascent() + mText.descent()) / 2, mText);
            mText.setTextAlign(Paint.Align.LEFT);
        }
    }


    // ---- the card of a place

    /** The widest a card gets, in dp; a longer name or line is cut with an ellipsis. */
    private static final float MAX_CARD_DP = 260;
    /** The flag of a card is 16 dp high and 4 by 3. */
    private static final float FLAG_W = 16f * Flags.ASPECT;

    /** The name of each place as the card shows it (cut to the width of the card). */
    private String[] mNameShown = new String[0];

    /** The text cut to {@code maxPx} with an ellipsis, measured with {@code mText} as it is set. */
    private String cut(String text, float maxPx) {
        if (mText.measureText(text) <= maxPx) {
            return text;
        }
        int n = mText.breakText(text, true, Math.max(0f, maxPx - mText.measureText("\u2026")), null);
        return text.substring(0, n) + "\u2026";
    }

    /** The name on a card: a company's name, or "Hosted in" and the city or country of a server place (ADR-161). */
    private String placeName(Places.Place p) {
        return p.lead.server ? getContext().getString(R.string.hosted_in, Texts.serverName(p.lead)) : p.lead.owner.name;
    }

    private float cardWidth(int i, Places.Place p) {
        if (mNameShown.length != mPlaces.size()) {
            mNameShown = new String[mPlaces.size()];
        }
        float unit = mDs.px(1);
        float plus = p.members.size() > 1 ? 32 : 0;
        mText.setTypeface(Ds.face(Ds.MEDIUM));
        mText.setTextSize(mDs.sp(14));
        float name = mText.measureText(placeName(p)) / unit;
        mText.setTypeface(Ds.face(Ds.REGULAR));
        mText.setTextSize(mDs.sp(12));
        float sub = mText.measureText(mSub[i]) / unit;
        // the room: 11 dp, the flag 16 high and FLAG_W wide, a gap of 7 and 11 dp at the end; the second line has a pin of 13 and a gap of 4
        float width = (float) Math.ceil(Math.max(name + FLAG_W + 7 + plus, sub + 17) + 26);
        float screen = getWidth() > 0 ? getWidth() / unit - 12 : MAX_CARD_DP;
        float most = Math.min(MAX_CARD_DP, Math.max(120, screen));
        mNameShown[i] = placeName(p);
        if (width > most) {
            width = most;
            mText.setTypeface(Ds.face(Ds.MEDIUM));
            mText.setTextSize(mDs.sp(14));
            mNameShown[i] = cut(placeName(p), (width - 26 - FLAG_W - 7 - plus) * unit);
            mText.setTypeface(Ds.face(Ds.REGULAR));
            mText.setTextSize(mDs.sp(12));
            mSub[i] = cut(mSub[i], (width - 26 - 17) * unit);
        }
        return width;
    }

    private void drawCard(Canvas c, int index, float x, float y, float w, float limb) {
        Places.Place p = mPlaces.get(index);
        int col = mPlaceColor[index];
        boolean selected = p.key.equals(mSelected);
        boolean dim = mSelected != null && !selected;
        float alpha = limb < 0.95f ? limb : (dim ? 0.4f : 1f);
        float s = mDs.grow();
        float h = mDs.px(46) * s;
        // a layer is needed only for a card that is faded: the shadow and the text are then faded as one
        int saved = -1;
        if (alpha < 0.999f) {
            saved = c.saveLayerAlpha(x - mDs.px(30), y - mDs.px(30), x + w + mDs.px(30), y + h + mDs.px(40),
                    Math.round(255 * alpha));
        }
        mRect.set(x, y, x + w, y + h);
        mP.setStyle(Paint.Style.FILL);
        mP.setColor(cLbl);
        mP.setShadowLayer(mDs.px(selected ? 16 : 14), 0, mDs.px(selected ? 0 : 4),
                selected ? Ds.withAlpha(col, 0.45f) : cShadowCard);
        c.drawRoundRect(mRect, mDs.px(14), mDs.px(14), mP);
        mP.clearShadowLayer();
        mStroke.setColor(selected ? col : cLblbd);
        mStroke.setStrokeWidth(Math.max(1, mDs.dp(1)));
        c.drawRoundRect(mRect, mDs.px(14), mDs.px(14), mStroke);
        if (selected) {
            mRect.inset(-mDs.px(1), -mDs.px(1));
            c.drawRoundRect(mRect, mDs.px(15), mDs.px(15), mStroke);
        }
        float top = y + mDs.px(4.5f) * s;
        float line1 = top + mDs.px(9) * s;
        // line 1: the flag, the name and, when there are more companies, "+n"
        float fx = x + mDs.px(11);
        float fy = line1 - mDs.px(8);
        Drawable flag = mFlag[index];
        if (flag != null) {
            flag.setBounds(Math.round(fx), Math.round(fy), Math.round(fx + mDs.px(FLAG_W)), Math.round(fy + mDs.px(16)));
            flag.draw(c);
        }
        mText.setTypeface(Ds.face(Ds.MEDIUM));
        mText.setTextSize(mDs.sp(14));
        mText.setColor(cOn);
        float nx = x + mDs.px(11 + FLAG_W + 7);
        String name = mNameShown[index];
        c.drawText(name, nx, line1 - (mText.ascent() + mText.descent()) / 2, mText);
        int extra = p.members.size() - 1;
        if (extra > 0) {
            float tx = nx + mText.measureText(name) + mDs.px(7);
            mText.setTypeface(Ds.face(Ds.MEDIUM));
            mText.setTextSize(mDs.sp(11));
            String more = "+" + extra;
            float pw = mText.measureText(more) + mDs.px(12);
            mRect.set(tx, line1 - mDs.px(8) * s, tx + pw, line1 + mDs.px(8) * s);
            mP.setColor(cPill);
            c.drawRoundRect(mRect, mDs.px(8) * s, mDs.px(8) * s, mP);
            mText.setColor(cOnv);
            c.drawText(more, tx + mDs.px(6), line1 - (mText.ascent() + mText.descent()) / 2, mText);
        }
        // line 2: a pin, the country and how many apps
        float line2 = top + mDs.px(21 + 8) * s;
        mPin.setBounds(Math.round(x + mDs.px(11)), Math.round(line2 - mDs.px(6.5f)), Math.round(x + mDs.px(24)),
                Math.round(line2 + mDs.px(6.5f)));
        mPin.draw(c);
        mText.setTypeface(Ds.face(Ds.REGULAR));
        mText.setTextSize(mDs.sp(12));
        mText.setColor(cOnv);
        c.drawText(mSub[index], x + mDs.px(11 + 13 + 4), line2 - (mText.ascent() + mText.descent()) / 2, mText);
        if (saved >= 0) {
            c.restoreToCount(saved);
        }
    }

    // ====================================================================== the graticule data

    private static float[][] graticule() {
        List<float[]> lines = new ArrayList<>();
        float[] v = new float[3];
        for (int lon = -180; lon < 180; lon += 15) {
            float[] l = new float[3 * 37];
            for (int k = 0; k < 37; k++) {
                UnitVectors.of(-90 + 5 * k, lon, v);
                l[3 * k] = v[0];
                l[3 * k + 1] = v[1];
                l[3 * k + 2] = v[2];
            }
            lines.add(l);
        }
        for (int lat = -75; lat <= 75; lat += 15) {
            float[] l = new float[3 * 73];
            for (int k = 0; k < 73; k++) {
                UnitVectors.of(lat, -180 + 5 * k, v);
                l[3 * k] = v[0];
                l[3 * k + 1] = v[1];
                l[3 * k + 2] = v[2];
            }
            lines.add(l);
        }
        return lines.toArray(new float[0][]);
    }

    // ===================================================================== accessibility

    /** The places as virtual views, so that a screen reader can reach each one and choose it. */
    private final Nodes mNodes = new Nodes();

    private final class Nodes extends AccessibilityNodeProvider {
        /** The virtual view that has the accessibility focus, or {@link View#NO_ID}. */
        private int mFocus = View.NO_ID;

        void changed() {
            if (isAttachedToWindow()) {
                sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
            }
        }

        /** A node is the index of the place in {@code mPlaces}, which does not change while a frame is drawn. */
        private int shownOf(int id) {
            if (id < 0 || id >= mPlaces.size()) {
                return -1;
            }
            return mShownPlaces.indexOf(mPlaces.get(id));
        }

        @Override
        public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
            if (id == View.NO_ID) {
                AccessibilityNodeInfo info = new AccessibilityNodeInfo(GlobeView.this);
                onInitializeAccessibilityNodeInfo(info);
                for (Places.Place p : mShownPlaces) {
                    info.addChild(GlobeView.this, mPlaces.indexOf(p));
                }
                return info;
            }
            int k = shownOf(id);
            if (k < 0) {
                return null;
            }
            Places.Place p = mShownPlaces.get(k);
            AccessibilityNodeInfo info = new AccessibilityNodeInfo(GlobeView.this, id);
            info.setParent(GlobeView.this);
            info.setPackageName(getContext().getPackageName());
            info.setClassName(Button.class.getName());
            int apps = p.apps.size();
            info.setContentDescription(getContext().getString(R.string.place_description, placeName(p),
                    mCountryName[id], getContext().getResources().getQuantityString(R.plurals.n_apps, apps, apps)));
            info.setVisibleToUser(true);
            info.setEnabled(true);
            info.setFocusable(true);
            info.setClickable(true);
            info.setSelected(p.key.equals(mSelected));
            info.setAccessibilityFocused(mFocus == id);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
            info.addAction(mFocus == id ? AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                    : AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
            float d = mDs.px(1);
            android.graphics.Rect r = new android.graphics.Rect(Math.round(mShownX[k]), Math.round(mShownY[k]),
                    Math.round(mShownX[k] + mShownW[k]), Math.round(mShownY[k] + 46 * mDs.grow() * d));
            info.setBoundsInParent(r);
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            r.offset(loc[0], loc[1]);
            info.setBoundsInScreen(r);
            return info;
        }

        @Override
        public boolean performAction(int id, int action, Bundle arguments) {
            int k = shownOf(id);
            if (k < 0) {
                return false;
            }
            switch (action) {
                case AccessibilityNodeInfo.ACTION_CLICK:
                    if (mListener != null) {
                        mListener.onPlace(mShownPlaces.get(k).key);
                    }
                    return true;
                case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS:
                    if (mFocus == id) {
                        return false;
                    }
                    mFocus = id;
                    send(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                    return true;
                case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
                    if (mFocus != id) {
                        return false;
                    }
                    mFocus = View.NO_ID;
                    send(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                    return true;
                default:
                    return false;
            }
        }

        private void send(int id, int type) {
            if (getParent() == null) {
                return;
            }
            AccessibilityEvent e = new AccessibilityEvent(type);
            e.setPackageName(getContext().getPackageName());
            e.setClassName(Button.class.getName());
            e.setSource(GlobeView.this, id);
            getParent().requestSendAccessibilityEvent(GlobeView.this, e);
        }
    }
}
