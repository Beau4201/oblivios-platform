// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.android.internal.datamap.DataMapSnapshot;
import com.android.internal.datamap.IDataMapObserver;
import com.android.internal.datamap.IDataMapService;

import org.sovereignos.datamap.model.LandData;
import org.sovereignos.datamap.model.MapModel;
import org.sovereignos.datamap.model.MapSelection;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The only component of the app. There is no service, receiver or provider: when this activity is closed
 * no process of the app remains (MAP-013, MAP-015). The service is asked while the activity is started and
 * the observer is removed when it stops.
 */
public final class MainActivity extends Activity
        implements Screens.Nav, SettingsScreen.Actions, GlobeView.Listener, SheetView.Host {
    private static final int TAB_MAP = 0;
    private static final int TAB_APPS = 1;
    private static final int TAB_COMPANIES = 2;
    private static final int TAB_LINKS = 3;

    private final ExecutorService mIo = Executors.newSingleThreadExecutor();
    private final AtomicBoolean mRefreshQueued = new AtomicBoolean();
    private final IDataMapObserver mObserver = new IDataMapObserver.Stub() {
        @Override
        public void onChanged() {
            refresh();
        }
    };
    private final OnBackInvokedCallback mBack = this::onBack;
    private boolean mBackRegistered;
    private final MapSelection mSel = new MapSelection();

    private Ui mUi;
    private Apps mApps;
    private Screens mScreens;
    private GlobeView mGlobe;
    private FrameLayout mRoot;
    private FrostedBar.Backdrop mBackdrop;
    private FrameLayout mPageHost;
    private FrameLayout mScreenHost;
    private ScrollView mScroll;
    private SheetView mSheet;
    private FrostedBar mTabBar;
    private View mHeader;
    private View mHeaderTitle;
    private View mHeaderBack;
    private android.widget.TextView mLead;
    private int mTab = TAB_MAP;
    private int mBottomInset;

    private volatile IDataMapService mService;
    private DataMapSnapshot mLast;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        mUi = new Ui(this);
        mApps = new Apps(this);
        mScreens = new Screens(mUi, mApps, this);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        mRoot = new FrameLayout(this);
        mRoot.setBackgroundColor(mUi.color(R.color.dm_bg));

        // Everything the tab bar looks through: the globe, the pages and the sheet.
        mBackdrop = new FrostedBar.Backdrop(this);
        mGlobe = new GlobeView(this, mApps);
        mGlobe.setListener(this);
        mBackdrop.addView(mGlobe, new FrameLayout.LayoutParams(-1, -1));

        mScroll = new ScrollView(this);
        mScroll.setBackgroundColor(mUi.color(R.color.dm_bg));
        mScroll.setVerticalScrollBarEnabled(false);
        mPageHost = new FrameLayout(this);
        mPageHost.addView(mScroll, new FrameLayout.LayoutParams(-1, -1));
        mBackdrop.addView(mPageHost, new FrameLayout.LayoutParams(-1, -1));

        mSheet = new SheetView(this, mUi, this);
        mBackdrop.addView(mSheet, new FrameLayout.LayoutParams(-1, 0, Gravity.BOTTOM));
        mSheet.jumpTo(0);
        mRoot.addView(mBackdrop, new FrameLayout.LayoutParams(-1, -1));

        mHeader = header();
        mRoot.addView(mHeader, new FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));

        mTabBar = tabBar();
        mTabBar.setBackdrop(mBackdrop);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, mUi.dp(68), Gravity.BOTTOM);
        tp.setMargins(mUi.dp(14), 0, mUi.dp(14), mUi.dp(12));
        mRoot.addView(mTabBar, tp);

        mScreenHost = new FrameLayout(this);
        mScreenHost.setVisibility(View.GONE);
        mRoot.addView(mScreenHost, new FrameLayout.LayoutParams(-1, -1));

        mRoot.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            mBottomInset = bars.bottom;
            mHeader.setPadding(0, bars.top, 0, 0);
            ((FrameLayout.LayoutParams) mTabBar.getLayoutParams()).bottomMargin = mUi.dp(12) + bars.bottom;
            mTabBar.requestLayout();
            mScroll.setPadding(0, bars.top, 0, mUi.dp(104) + bars.bottom);
            mScroll.setClipToPadding(false);
            mSheet.setBottomPadding(mUi.dp(104) + bars.bottom);
            return insets;
        });
        // The sheet is a share of the height of the screen: it follows a rotation.
        mBackdrop.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) {
                applySelection(false);
            }
        });
        setContentView(mRoot);
        showTab(TAB_MAP);

        mIo.execute(() -> {
            try (InputStream in = getResources().openRawResource(R.raw.land)) {
                final LandData land = LandData.read(in);
                runOnUiThread(() -> mGlobe.setLand(land));
            } catch (IOException e) {
                // the globe is drawn without land; the lists still work
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mService = DataMapClient.connect();
        if (mService == null) {
            mLead.setText(R.string.service_missing);
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
        super.onDestroy();
    }

    // -------------------------------------------------------------------------------- data

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
                final MapModel.Input in = SnapshotMapper.toInput(snap);
                final MapModel.Output out = MapModel.build(in);
                runOnUiThread(() -> apply(snap, in, out));
            } catch (RemoteException | RuntimeException e) {
                runOnUiThread(() -> mLead.setText(R.string.service_missing));
            }
        });
    }

    private void apply(DataMapSnapshot snap, MapModel.Input in, MapModel.Output out) {
        mLast = snap;
        mScreens.set(out, in.owners, snap.nowWallMs);
        mGlobe.setModel(out, in.owners);
        mGlobe.setSelection(mSel);
        mLead.setText(out.apps.isEmpty() ? R.string.empty_nothing : R.string.lead_map);
        renderSheet(true);
        if (mTab != TAB_MAP) {
            final int y = mScroll.getScrollY();   // a live refresh must not throw the reader back to the top
            showTab(mTab);
            mScroll.post(() -> mScroll.scrollTo(0, y));
        }
    }

    // ----------------------------------------------------------------------------- screens

    private View header() {
        FrameLayout h = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView t = mUi.text(getString(R.string.app_name), 28, false, R.color.dm_on);
        t.setPadding(mUi.dp(24), mUi.dp(10), mUi.dp(24), 0);
        mLead = mUi.text("", 14, false, R.color.dm_onv);
        mLead.setPadding(mUi.dp(24), 0, mUi.dp(72), 0);
        col.addView(t);
        col.addView(mLead);
        mHeaderTitle = col;
        h.addView(col);
        // With the sheet open the title gives way to a way back, as on the design page.
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(android.content.res.ColorStateList.valueOf(mUi.color(R.color.dm_on)));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription(getString(R.string.back));
        back.setOnClickListener(v -> backFromSelection());
        back.setVisibility(View.GONE);
        mHeaderBack = back;
        h.addView(back, new FrameLayout.LayoutParams(mUi.dp(48), mUi.dp(48), Gravity.TOP | Gravity.START));
        ImageView menu = new ImageView(this);
        menu.setImageResource(R.drawable.ic_menu);
        menu.setImageTintList(android.content.res.ColorStateList.valueOf(mUi.color(R.color.dm_on)));
        menu.setScaleType(ImageView.ScaleType.CENTER);
        menu.setContentDescription(getString(R.string.settings));
        menu.setOnClickListener(v -> openSettings());
        h.addView(menu, new FrameLayout.LayoutParams(mUi.dp(48), mUi.dp(48), Gravity.TOP | Gravity.END));
        h.setClickable(false);
        return h;
    }

    private FrostedBar tabBar() {
        FrostedBar bar = new FrostedBar(this, mUi);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(mUi.dp(8), 0, mUi.dp(8), 0);
        int[] icons = {R.drawable.ic_map, R.drawable.ic_apps, R.drawable.ic_companies, R.drawable.ic_links};
        int[] names = {R.string.tab_map, R.string.tab_apps, R.string.tab_companies, R.string.tab_links};
        for (int i = 0; i < 4; i++) {
            final int tab = i;
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setContentDescription(getString(names[i]));
            item.addView(icon, new LinearLayout.LayoutParams(mUi.dp(24), mUi.dp(24)));
            android.widget.TextView label = mUi.text(getString(names[i]), 14, true, R.color.dm_on);
            label.setPadding(mUi.dp(8), 0, 0, 0);
            item.addView(label);
            item.setOnClickListener(v -> showTab(tab));
            bar.addView(item, new LinearLayout.LayoutParams(0, mUi.dp(52), 1f));
        }
        return bar;
    }

    private void showTab(int tab) {
        mTab = tab;
        for (int i = 0; i < mTabBar.getChildCount(); i++) {
            LinearLayout item = (LinearLayout) mTabBar.getChildAt(i);
            boolean on = i == tab;
            item.setBackground(on ? mUi.shape(R.color.dm_sc, 26, 26) : null);
            ImageView icon = (ImageView) item.getChildAt(0);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    mUi.color(on ? R.color.dm_osc : R.color.dm_onv)));
            item.getChildAt(1).setVisibility(on ? View.VISIBLE : View.GONE);
        }
        mHeader.setVisibility(tab == TAB_MAP ? View.VISIBLE : View.GONE);
        mGlobe.setVisibility(tab == TAB_MAP ? View.VISIBLE : View.INVISIBLE);
        mScroll.setVisibility(tab == TAB_MAP ? View.GONE : View.VISIBLE);
        mScroll.removeAllViews();
        if (tab == TAB_APPS) {
            mScroll.addView(mScreens.appsPage());
        } else if (tab == TAB_COMPANIES) {
            mScroll.addView(mScreens.companiesPage());
        } else if (tab == TAB_LINKS) {
            mScroll.addView(mScreens.linksPage());
        }
        applySelection(false);
    }

    private void showScreen(View content) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(mUi.color(R.color.dm_bg));
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(content);
        sv.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        mScreenHost.removeAllViews();
        mScreenHost.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(android.content.res.ColorStateList.valueOf(mUi.color(R.color.dm_on)));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription(getString(R.string.back));
        back.setOnClickListener(v -> closeScreen());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(mUi.dp(48), mUi.dp(48), Gravity.TOP | Gravity.START);
        bp.topMargin = mUi.dp(8);
        mScreenHost.addView(back, bp);
        mScreenHost.setVisibility(View.VISIBLE);
        updateBack();
    }

    private void closeScreen() {
        mScreenHost.removeAllViews();
        mScreenHost.setVisibility(View.GONE);
        updateBack();
    }

    private boolean screenOpen() {
        return mScreenHost.getVisibility() == View.VISIBLE;
    }

    // ------------------------------------------------------------- the choice and the sheet

    /** Draws the choice: the sheet to its height, its body, the globe clear of it, the header and the back key. */
    private void applySelection(boolean animate) {
        mGlobe.setSelection(mSel);
        boolean onMap = mTab == TAB_MAP;
        int target = onMap
                ? MapSelection.sheetHeight(mSel.sheet(), mBackdrop.getHeight(), mUi.dp(60)) : 0;
        if (animate) {
            mSheet.animateTo(target);
        } else {
            mSheet.jumpTo(target);
        }
        renderSheet(false);
        boolean open = onMap && mSel.sheet() != MapSelection.SHEET_NONE;
        mHeaderTitle.setVisibility(open ? View.GONE : View.VISIBLE);
        mHeaderBack.setVisibility(open ? View.VISIBLE : View.GONE);
        updateBack();
    }

    private void renderSheet(boolean keepScroll) {
        View v = null;
        switch (mSel.content()) {
            case MapSelection.CONTENT_APP:
                v = mScreens.appBody(mSel.app(), 8);
                break;
            case MapSelection.CONTENT_COMPANY:
                v = mScreens.companyBody(mSel.company(), 8);
                break;
            case MapSelection.CONTENT_RELATION:
                v = mScreens.relationBody(mSel.relation(), 8);
                break;
            case MapSelection.CONTENT_GROUP:
                v = mScreens.groupBody(mSel.group(), 8);
                break;
            default:
                break;
        }
        mSheet.setContent(v, keepScroll);
    }

    private void backFromSelection() {
        mSel.back();
        if (mSel.sheet() == MapSelection.SHEET_NONE) {
            mGlobe.zoomOut();
        }
        applySelection(true);
    }

    private boolean needsBack() {
        return screenOpen() || (mTab == TAB_MAP && mSel.sheet() != MapSelection.SHEET_NONE);
    }

    private void updateBack() {
        boolean need = needsBack();
        if (need && !mBackRegistered) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, mBack);
            mBackRegistered = true;
        } else if (!need && mBackRegistered) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(mBack);
            mBackRegistered = false;
        }
    }

    private void onBack() {
        if (screenOpen()) {
            closeScreen();
        } else if (mTab == TAB_MAP && mSel.sheet() != MapSelection.SHEET_NONE) {
            backFromSelection();
        }
    }

    // ---------------------------------------------------------------- SheetView.Host

    @Override
    public void onHandle() {
        mSel.toggleSheet();
        applySelection(true);
    }

    @Override
    public void onHeight(float px) {
        mGlobe.setSheetHeightPx(px);
    }

    // --------------------------------------------------------------- GlobeView.Listener

    @Override
    public void onApp(String pkg) {
        openApp(pkg);
    }

    @Override
    public void onCompany(String ownerId) {
        openCompany(ownerId);
    }

    @Override
    public void onGroup(List<String> ownerIds) {
        mSel.openGroup(ownerIds);
        mGlobe.turnToGroup(ownerIds);
        applySelection(true);
    }

    @Override
    public void onRelation(int index) {
        mSel.selectRelation(index);
        mGlobe.turnToRelation(index);
        applySelection(true);
    }

    @Override
    public void onNothing() {
        if (mSel.sheet() != MapSelection.SHEET_NONE) {
            backFromSelection();
        }
    }

    // ------------------------------------------------------------------- Screens.Nav

    @Override
    public void openApp(String pkg) {
        if (mTab == TAB_MAP && !screenOpen()) {
            mSel.selectApp(pkg);
            mGlobe.turnToApp(pkg);
            applySelection(true);
        } else {
            showScreen(mScreens.appDetail(pkg));
        }
    }

    @Override
    public void openCompany(String ownerId) {
        if (mTab == TAB_MAP && !screenOpen()) {
            mSel.selectCompany(ownerId);
            mGlobe.turnToCompany(ownerId);
            applySelection(true);
        } else {
            showScreen(mScreens.companyDetail(ownerId));
        }
    }

    private void openSettings() {
        DataMapSnapshot s = mLast;
        boolean collecting = s == null || s.collecting;
        showScreen(new SettingsScreen(mUi, this).build(collecting, s != null && s.historyOn,
                s == null ? 0 : s.historyDays, s == null ? 0 : s.invalidCount));
    }

    // ------------------------------------------------------------------- SettingsScreen.Actions

    @Override
    public void setCollecting(boolean collecting) {
        call(s -> s.setCollecting(collecting));
    }

    @Override
    public void setHistory(boolean enabled, int days) {
        call(s -> s.setHistory(enabled, days));
    }

    @Override
    public void clear() {
        call(IDataMapService::clear);
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
