// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.android.internal.datamap.DataMapSnapshot;
import com.android.internal.datamap.IDataMapObserver;
import com.android.internal.datamap.IDataMapService;

import org.freeholdos.datamap.model.AtlasData;
import org.freeholdos.datamap.model.AtlasState;
import org.freeholdos.datamap.model.CompanyList;
import org.freeholdos.datamap.model.LandData;
import org.freeholdos.datamap.model.MapModel;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The only component of the app. There is no service, receiver or provider: when this activity is closed no process of
 * the app remains (MAP-013, MAP-015). The service is asked while the activity is started and the observer is removed when
 * it stops. The activity holds the four tabs, one detail page above them, and what stands over a screen for a moment (the
 * menu, the filter, the question before a delete); it makes no screen of its own (ADR-160).
 */
public final class MainActivity extends Activity implements Pages.Nav, SettingsPage.Actions {
    private static final String STATE_ATLAS = "atlas";
    private static final String STATE_DETAIL = "detail";
    private static final String STATE_FILTER = "filter";
    private static final String STATE_OPEN = "open";
    private static final int DETAIL_NONE = 0;
    private static final int DETAIL_COMPANY = 1;
    private static final int DETAIL_APP = 2;

    private final ExecutorService mIo = Executors.newSingleThreadExecutor();
    /** The land is prepared apart, so that the first snapshot is not read behind 3400 point-in-polygon tests. */
    private final ExecutorService mLand = Executors.newSingleThreadExecutor();
    private final AtomicBoolean mRefreshQueued = new AtomicBoolean();
    private final IDataMapObserver mObserver = new IDataMapObserver.Stub() {
        @Override
        public void onChanged() {
            refresh();
        }
    };
    private final OnBackInvokedCallback mBack = this::onBack;
    private boolean mBackRegistered;

    private Ds mDs;
    private Apps mApps;
    private Widgets mW;
    private Pages mPages;
    private AtlasState mState = new AtlasState();
    private final Pages.Filter mFilter = new Pages.Filter();
    private final SettingsPage.Open mOpen = new SettingsPage.Open();
    private AtlasData mData = AtlasData.empty();
    private GlobeView.Prepared mPrepared;

    private FrameLayout mRoot;
    private FrameLayout mContent;
    private TabBar.Backdrop mBackdrop;
    private MapTab mMap;
    private final ScrollView[] mScrolls = new ScrollView[4];
    private FrameLayout mDetail;
    private ScrollView mDetailBody;
    /** The menu or the question that stands over the screen, or null. */
    private View mOverlay;
    private TabBar mTabBar;
    private int mDetailKind = DETAIL_NONE;
    /** Counts the openings and closings of the detail page, so that a late end of an exit does nothing. */
    private int mDetailToken;
    private int mTabToken;
    private SettingsPage mSettingsPage;
    private String mDetailId = "";
    private Overlays.FilterSheet mFilterSheet;

    private volatile IDataMapService mService;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        mDs = new Ds(this);
        mApps = new Apps(this);
        mW = new Widgets(mDs, mApps);
        mPages = new Pages(mW, this, mFilter);
        if (saved != null) {
            mState = AtlasState.fromBundleData(saved.getStringArray(STATE_ATLAS));
            String[] d = saved.getStringArray(STATE_DETAIL);
            if (d != null && d.length == 2) {
                mDetailKind = parse(d[0]);
                mDetailId = d[1];
            }
            int[] f = saved.getIntArray(STATE_FILTER);
            if (f != null && f.length == 2) {
                mFilter.type = CompanyList.validType(f[0]);
                mFilter.sort = f[1] >= CompanyList.APPS && f[1] <= CompanyList.COUNTRY ? f[1] : CompanyList.APPS;
            }
            boolean[] o = saved.getBooleanArray(STATE_OPEN);
            if (o != null && o.length == 2) {
                mOpen.how = o[0];
                mOpen.cannot = o[1];
            }
        }
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        mRoot = new FrameLayout(this);
        mRoot.setBackgroundColor(mDs.color(R.color.at_bg));
        // the content area is what the design page calls .scr: between the status bar and the gesture bar
        mContent = new FrameLayout(this);
        mContent.setClipChildren(true);
        mRoot.addView(mContent, new FrameLayout.LayoutParams(-1, -1));

        mBackdrop = new TabBar.Backdrop(this);
        mMap = new MapTab(this, mW, mState, this);
        mBackdrop.addView(mMap, new FrameLayout.LayoutParams(-1, -1));
        for (int tab = AtlasState.APPS; tab <= AtlasState.SETTINGS; tab++) {
            ScrollView s = new ScrollView(this);
            s.setVerticalScrollBarEnabled(false);
            s.setBackgroundColor(mDs.color(R.color.at_bg));
            s.setClipToPadding(false);
            s.setPadding(mDs.dp(16), mDs.dp(14), mDs.dp(16), mDs.dp(108));
            s.setVisibility(View.GONE);
            mScrolls[tab] = s;
            mBackdrop.addView(s, new FrameLayout.LayoutParams(-1, -1));
        }
        mDetail = new FrameLayout(this);
        mDetail.setBackgroundColor(mDs.color(R.color.at_bg));
        mDetail.setVisibility(View.GONE);
        mDetail.setClickable(true);
        mBackdrop.addView(mDetail, new FrameLayout.LayoutParams(-1, -1));
        mContent.addView(mBackdrop, new FrameLayout.LayoutParams(-1, -1));

        mTabBar = new TabBar(this, mDs, tab -> {
            closeDetail();
            showTab(tab, true);
        });
        mTabBar.setBackdrop(mBackdrop);
        mContent.addView(mTabBar, TabBar.params(mDs));

        mRoot.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContent.getLayoutParams();
            lp.topMargin = bars.top;
            lp.bottomMargin = bars.bottom;
            lp.leftMargin = bars.left;
            lp.rightMargin = bars.right;
            mContent.setLayoutParams(lp);
            return insets;
        });
        setContentView(mRoot);
        mMap.restore();
        showTab(mState.tab(), false);
        if (mDetailKind != DETAIL_NONE) {
            showDetail(mDetailKind, mDetailId, false);
        }
        mLand.execute(() -> {
            try (InputStream in = getResources().openRawResource(R.raw.land)) {
                final GlobeView.Prepared p = GlobeView.Prepared.of(LandData.read(in));
                runOnUiThread(() -> {
                    mPrepared = p;
                    mMap.setPrepared(p);
                    mPages.setData(mData, p);
                    if (mDetailKind == DETAIL_COMPANY) {
                        showDetail(mDetailKind, mDetailId, false);
                    }
                });
            } catch (IOException e) {
                // the globe is drawn without land; the lists still work
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putStringArray(STATE_ATLAS, mState.toBundleData());
        out.putStringArray(STATE_DETAIL, new String[] {Integer.toString(mDetailKind), mDetailId});
        out.putIntArray(STATE_FILTER, new int[] {mFilter.type, mFilter.sort});
        out.putBooleanArray(STATE_OPEN, new boolean[] {mOpen.how, mOpen.cannot});
    }

    private static int parse(String s) {
        try {
            int k = Integer.parseInt(s);
            return k == DETAIL_COMPANY || k == DETAIL_APP ? k : DETAIL_NONE;
        } catch (NumberFormatException e) {
            return DETAIL_NONE;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mService = DataMapClient.connect();
        if (mService == null) {
            apply(AtlasData.unavailable());
            return;
        }
        try {
            mService.registerObserver(mObserver);
        } catch (RemoteException e) {
            // without live updates the screen still refreshes when it is opened
        }
        refresh();
    }

    @Override
    protected void onStop() {
        IDataMapService s = mService;
        mService = null;
        if (s != null) {
            try {
                s.unregisterObserver(mObserver);
            } catch (RemoteException e) {
                // the service drops a dead observer by itself
            }
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mIo.shutdownNow();
        mLand.shutdownNow();
        super.onDestroy();
    }

    // ================================================================================= data

    /** Reads a new snapshot off the main thread. A burst of changes causes one read. */
    private void refresh() {
        if (!mRefreshQueued.compareAndSet(false, true)) {
            return;
        }
        execute(() -> {
            mRefreshQueued.set(false);
            IDataMapService s = mService;
            if (s == null) {
                return;
            }
            try {
                final DataMapSnapshot snap = s.getSnapshot(mApps.installedPackages());
                final MapModel.Output out = MapModel.build(SnapshotMapper.toInput(snap));
                final AtlasData data = new AtlasData(out, snap.collecting, snap.historyOn, snap.historyDays,
                        snap.serverHistory, snap.nowWallMs);
                java.util.List<String> pkgs = new java.util.ArrayList<>();
                for (MapModel.AppRow a : out.apps) {
                    pkgs.add(a.pkg);
                }
                mApps.warm(pkgs);
                runOnUiThread(() -> apply(data));
            } catch (RemoteException | RuntimeException e) {
                // the screen keeps what it has
            }
        });
    }

    /** A finger is down: the pages are not built again until it is up. */
    private boolean mTouching;
    private boolean mRebuildPending;

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mTouching = true;
        }
        boolean handled = super.dispatchTouchEvent(e);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mTouching = false;
            if (mRebuildPending) {
                mRebuildPending = false;
                buildPage(mState.tab(), true);
                rebuildOverlays();
            }
        }
        return handled;
    }

    private void apply(AtlasData data) {
        mData = data;
        mPages.setData(data, mPrepared);
        mMap.setData(data);
        // the Settings page moves its own controls: it is never built again for a new snapshot
        if (mState.tab() == AtlasState.SETTINGS && mSettingsPage != null && mSettingsPage.update(data)) {
            rebuildOverlays();
            return;
        }
        if (mTouching) {
            mRebuildPending = true;   // a page built again under a finger takes the touch away from it
            return;
        }
        buildPage(mState.tab(), true);
        rebuildOverlays();
    }

    private void rebuildOverlays() {
        if (mDetailKind != DETAIL_NONE) {
            showDetail(mDetailKind, mDetailId, false);
        }
        if (mFilterSheet != null) {
            mFilterSheet.setContent(mPages.filterPanel(mFilterSheet::dismiss));
        }
    }

    // ================================================================================= tabs

    private void showTab(int tab, boolean animate) {
        final int old = mState.tab();
        boolean changed = tab != old;
        mState.go(tab);
        final int token = ++mTabToken;
        final View leaving = !changed || !animate ? null : old == AtlasState.MAP ? mMap : mScrolls[old];
        mMap.animate().cancel();
        mMap.setAlpha(1f);
        mMap.setTranslationY(0f);
        mMap.setVisibility(tab == AtlasState.MAP || leaving == mMap ? View.VISIBLE : View.GONE);
        for (int t = AtlasState.APPS; t <= AtlasState.SETTINGS; t++) {
            mScrolls[t].animate().cancel();
            mScrolls[t].setAlpha(1f);
            mScrolls[t].setTranslationY(0f);
            mScrolls[t].setVisibility(t == tab || mScrolls[t] == leaving ? View.VISIBLE : View.GONE);
        }
        if (leaving != null) {
            Anim.pageOut(mDs, leaving, () -> {
                if (token == mTabToken) {   // a later switch has already settled every page
                    leaving.setVisibility(View.GONE);
                    leaving.setAlpha(1f);
                    leaving.setTranslationY(0f);
                }
            });
        }
        if (tab != AtlasState.MAP) {
            buildPage(tab, false);
            if (animate && changed) {
                Anim.pageIn(mDs, mScrolls[tab]);
            }
        }
        mTabBar.select(tab, animate);
        updateBack();
    }

    /** Builds the page of a tab again; a live refresh keeps where the reader was. */
    private void buildPage(int tab, boolean keepScroll) {
        if (tab == AtlasState.MAP) {
            return;
        }
        ScrollView s = mScrolls[tab];
        int y = keepScroll ? s.getScrollY() : 0;
        View page;
        if (tab == AtlasState.SETTINGS) {
            mSettingsPage = new SettingsPage(mW, this, mOpen);
            page = mSettingsPage.build(mData);
        } else {
            page = tab == AtlasState.APPS ? mPages.apps() : mPages.companies();
        }
        s.removeAllViews();
        s.addView(page, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Ds.layoutNow(s);
        final int to = y;
        s.post(() -> s.scrollTo(0, to));
    }

    // ================================================================================ detail

    private void showDetail(int kind, String id, boolean animate) {
        // a live refresh of the same page keeps where the reader was
        final int keepY = !animate && mDetailBody != null && mDetailBody.isAttachedToWindow()
                && kind == mDetailKind && id.equals(mDetailId) ? mDetailBody.getScrollY() : 0;
        mDetailToken++;   // an exit that is running ends without taking this page away
        mDetail.animate().cancel();
        mDetail.setAlpha(1f);
        mDetail.setTranslationX(0f);
        mDetailKind = kind;
        mDetailId = id;
        mDetail.removeAllViews();
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        // the header: a back arrow and the menu, 56 dp, with nothing between them
        LinearLayout head = new LinearLayout(this);
        head.setId(R.id.at_detail_header);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(mDs.dp(8), 0, mDs.dp(16), 0);
        View back = mW.iconButton(R.drawable.ic_back, getString(R.string.back), v -> closeDetailAnimated());
        back.setId(R.id.at_back);
        head.addView(back, new LinearLayout.LayoutParams(mDs.dp(40), mDs.dp(40)));
        head.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        View more = mW.iconButton(R.drawable.ic_more, getString(R.string.more), v -> openMenu());
        more.setId(R.id.at_more);
        head.addView(more, new LinearLayout.LayoutParams(mDs.dp(40), mDs.dp(40)));
        column.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mDs.dp(56)));
        final ScrollView body = new ScrollView(this);
        mDetailBody = body;
        body.setVerticalScrollBarEnabled(false);
        body.setClipToPadding(false);
        body.setPadding(mDs.dp(16), mDs.dp(4), mDs.dp(16), mDs.dp(108));
        body.addView(kind == DETAIL_COMPANY ? mPages.company(id) : mPages.app(id), new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        mDetail.addView(column, new FrameLayout.LayoutParams(-1, -1));
        mDetail.setVisibility(View.VISIBLE);
        Ds.layoutNow(mDetail);
        if (keepY > 0) {
            body.post(() -> body.scrollTo(0, keepY));
        }
        if (animate) {
            Anim.detailIn(mDs, mDetail);
        }
        updateBack();
    }

    /** Closes the detail page at once: the caller moves to another tab. */
    private void closeDetail() {
        mDetailToken++;
        mDetail.animate().cancel();
        mDetail.setAlpha(1f);
        mDetail.setTranslationX(0f);
        mDetailKind = DETAIL_NONE;
        mDetail.removeAllViews();
        mDetail.setVisibility(View.GONE);
        updateBack();
    }

    /** Closes the detail page the way it came: it slides out to the right. */
    private void closeDetailAnimated() {
        if (mDetailKind == DETAIL_NONE) {
            return;
        }
        mDetailKind = DETAIL_NONE;
        final int token = ++mDetailToken;
        updateBack();
        Anim.detailOut(mDs, mDetail, () -> {
            if (token == mDetailToken) {   // a page opened meanwhile keeps the view
                closeDetail();
            }
        });
    }

    private void openMenu() {
        showOverlay(Overlays.menu(mW, mContent, () -> {
            closeDetail();
            showTab(AtlasState.SETTINGS, true);
        }, () -> {
            closeDetail();
            showTab(AtlasState.MAP, true);
        }));
    }

    private void showOverlay(View layer) {
        if (mOverlay != null) {
            mContent.removeView(mOverlay);
        }
        mOverlay = layer;
        layer.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {}

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (mOverlay == v) {
                    mOverlay = null;
                    updateBack();
                }
            }
        });
        mContent.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        updateBack();
    }

    // ================================================================================== back

    private boolean needsBack() {
        return mDetailKind != DETAIL_NONE || mFilterSheet != null || mOverlay != null
                || (mState.tab() == AtlasState.MAP && mMap.canGoBack());
    }

    private void updateBack() {
        boolean need = needsBack();
        if (need && !mBackRegistered) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, mBack);
            mBackRegistered = true;
        } else if (!need && mBackRegistered) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(mBack);
            mBackRegistered = false;
        }
    }

    private void onBack() {
        if (mOverlay != null) {
            Anim.dismiss(mDs, mContent, mOverlay);
        } else if (mFilterSheet != null) {
            mFilterSheet.dismiss();
        } else if (mDetailKind != DETAIL_NONE) {
            closeDetailAnimated();
        } else if (mState.tab() == AtlasState.MAP) {
            mMap.back();
        }
        updateBack();
    }

    // ============================================================================= Pages.Nav

    @Override
    public void openCompany(String ownerId) {
        showDetail(DETAIL_COMPANY, ownerId, true);
    }

    @Override
    public void openApp(String pkg) {
        showDetail(DETAIL_APP, pkg, true);
    }

    @Override
    public void showAppOnMap(String pkg) {
        closeDetail();
        showTab(AtlasState.MAP, true);
        mMap.chooseApp(pkg);
    }

    @Override
    public void showCompanyOnMap(String ownerId) {
        closeDetail();
        showTab(AtlasState.MAP, true);
        mMap.showCompanyOnMap(ownerId);
    }

    @Override
    public void showLimits() {
        closeDetail();
        mOpen.cannot = true;
        mState.go(AtlasState.SETTINGS);
        showTab(AtlasState.SETTINGS, true);
    }

    @Override
    public void turnOnCollection() {
        setCollecting(true);
    }

    @Override
    public void openFilter() {
        if (mFilterSheet != null) {
            return;
        }
        mFilterSheet = new Overlays.FilterSheet(mW, mContent, () -> {
            mFilterSheet = null;
            updateBack();
        });
        mFilterSheet.setContent(mPages.filterPanel(mFilterSheet::dismiss));
        mFilterSheet.open();
        updateBack();
    }

    @Override
    public void filterChanged() {
        buildPage(AtlasState.COMPANIES, true);
        if (mFilterSheet != null) {
            mFilterSheet.setContent(mPages.filterPanel(mFilterSheet::dismiss));
        }
    }

    @Override
    public void stateChanged() {
        updateBack();
    }

    // ======================================================================== SettingsPage.Actions

    @Override
    public void setCollecting(boolean collecting) {
        call(s -> s.setCollecting(collecting));
    }

    @Override
    public void setHistory(boolean enabled, int days) {
        call(s -> s.setHistory(enabled, days));
    }

    @Override
    public void setServerHistory(boolean enabled) {
        call(s -> s.setServerHistory(enabled));
    }

    @Override
    public void askDelete() {
        showOverlay(Overlays.dialog(mW, mContent, getString(R.string.delete_title), getString(R.string.delete_text),
                getString(R.string.cancel), getString(R.string.delete), () -> call(IDataMapService::clear)));
    }

    private interface Call {
        void run(IDataMapService s) throws RemoteException;
    }

    /** The observer calls in on a binder thread, possibly after the activity is gone: never throw there. */
    private void execute(Runnable r) {
        try {
            mIo.execute(r);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            mRefreshQueued.set(false);
        }
    }

    private void call(Call c) {
        execute(() -> {
            IDataMapService s = mService;
            if (s != null) {
                try {
                    c.run(s);
                } catch (RemoteException | RuntimeException e) {
                    // a refused change shows on the next read, because the screen reads the settings back
                }
            }
            refresh();
        });
    }
}
