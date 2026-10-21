// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SearchView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The "Compartments" entry under Network &amp; internet (the {@code IA_SETTINGS} tile of the manifest): the status, the
 * one-time consent, the hidden-address switch, the apps with their Direct/Tor choice, the hosts the user showed an address
 * to, and the warnings. Stock widgets in the platform's Settings theme; the activity only draws what {@link Presenter}
 * lists and posts each change to {@link ExitRuntime}.
 */
public final class ExitSettingsActivity extends Activity implements ExitRuntime.Listener {
    private enum Page { MAIN, APPS, OVERRIDES }

    private final Handler main = new Handler(Looper.getMainLooper());
    private ExitRuntime rt;
    private CompanyNames names;
    private LinearLayout content;
    private Page page = Page.MAIN;
    private String query = "";
    /** The launchable apps of this user with icon and label, loaded once in the background. */
    private List<Presenter.Listed> listed = new ArrayList<>();
    private final Map<String, Drawable> icons = new HashMap<>();
    private LinearLayout appList;
    private boolean consentChoice = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle(R.string.app_name);
        rt = ExitRuntime.get(this);
        names = rt.companyNames();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        loadApps();
    }

    @Override
    protected void onStart() {
        super.onStart();
        rt.setListener(this);
        render();
    }

    @Override
    protected void onStop() {
        rt.setListener(null);
        super.onStop();
    }

    @Override
    public void onChanged(ExitRuntime.Snapshot s) {
        main.post(() -> {
            if (page == Page.APPS && appList != null) {
                fillAppList(s);
            } else {
                render();
            }
        });
    }

    @Override
    public boolean onNavigateUp() {
        if (page != Page.MAIN) {
            goBack();
        } else {
            finish();
        }
        return true;
    }

    /** The back gesture goes from a sub-page to the main page; on the main page it is not ours. */
    private final OnBackInvokedCallback back = this::goBack;
    private boolean backRegistered;

    private void goBack() {
        page = Page.MAIN;
        query = "";
        render();
    }

    private void updateBackCallback() {
        boolean want = page != Page.MAIN;
        if (want && !backRegistered) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, back);
        } else if (!want && backRegistered) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);
        }
        backRegistered = want;
    }

    // ---- pages ----------------------------------------------------------------------------------------------------

    private void render() {
        updateBackCallback();
        content.removeAllViews();
        appList = null;
        ExitRuntime.Snapshot s = rt.snapshot();
        if (!rt.runsHere() || s == null) {
            content.addView(summary(getString(R.string.owner_only)));
            return;
        }
        switch (page) {
            case APPS:
                renderApps(s);
                break;
            case OVERRIDES:
                renderOverrides(s);
                break;
            default:
                renderMain(s);
                break;
        }
    }

    private void renderMain(ExitRuntime.Snapshot s) {
        content.addView(title(statusText(s.status)));
        if (s.status.lastError != null && s.status.phase != Status.Phase.RUNNING) {
            content.addView(summary(getString(R.string.status_error, s.status.lastError)));
        }
        for (Policy.Warning w : s.status.warnings) {
            content.addView(summary(getString(warningText(w))));
        }
        if (s.choices.damaged()) {
            content.addView(summary(getString(R.string.warn_damaged)));
        }

        if (s.consent == Consent.UNSET) {
            content.addView(heading(getString(R.string.consent_title)));
            content.addView(summary(getString(R.string.consent_text)));
            Switch choice = new Switch(this);
            choice.setText(R.string.consent_switch);
            choice.setChecked(consentChoice);
            choice.setOnCheckedChangeListener((v, on) -> consentChoice = on);
            content.addView(choice);
            Button go = new Button(this);
            go.setText(R.string.consent_confirm);
            go.setOnClickListener(v -> rt.setConsent(consentChoice ? Consent.ON : Consent.OFF));
            content.addView(go);
        } else {
            Switch masked = new Switch(this);
            masked.setText(R.string.masked_title);
            masked.setChecked(s.consent == Consent.ON);
            masked.setOnCheckedChangeListener((v, on) -> {
                if (on != (rt.snapshot().consent == Consent.ON)) {
                    rt.setConsent(on ? Consent.ON : Consent.OFF);
                }
            });
            content.addView(masked);
            content.addView(summary(getString(R.string.masked_summary)));
        }

        content.addView(heading(getString(R.string.apps_title)));
        content.addView(row(getString(R.string.apps_title), getString(R.string.apps_summary, s.choices.torApps().size()),
                v -> {
                    page = Page.APPS;
                    render();
                }));
        content.addView(row(getString(R.string.overrides_title),
                getString(R.string.overrides_summary, Presenter.overrideRows(s.choices, names).size()), v -> {
                    page = Page.OVERRIDES;
                    render();
                }));
    }

    private void renderApps(ExitRuntime.Snapshot s) {
        content.addView(title(getString(R.string.apps_title)));
        SearchView search = new SearchView(this);
        search.setIconifiedByDefault(false);
        search.setQueryHint(getString(R.string.apps_search_hint));
        search.setQuery(query, false);
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String q) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String q) {
                query = q;
                ExitRuntime.Snapshot now = rt.snapshot();
                if (now != null && appList != null) {
                    fillAppList(now);
                }
                return true;
            }
        });
        content.addView(search);
        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        content.addView(appList);
        fillAppList(s);
    }

    private void fillAppList(ExitRuntime.Snapshot s) {
        appList.removeAllViews();
        for (Presenter.AppRow r : Presenter.appRows(s.choices, listed, query)) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(0, dp(8), 0, dp(8));
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(icons.get(r.pkg));
            line.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(16), 0, dp(16), 0);
            TextView label = new TextView(this);
            label.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
            label.setText(r.label);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(label);
            TextView hint = new TextView(this);
            hint.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
            hint.setText(r.tor ? R.string.app_tor_hint : R.string.app_direct_hint);
            text.addView(hint);
            line.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Switch sw = new Switch(this);
            sw.setChecked(r.tor);
            sw.setContentDescription(getString(R.string.app_tor_switch, r.label));
            sw.setOnClickListener(v -> choose(r.pkg, sw));
            line.addView(sw);
            line.setOnClickListener(v -> sw.performClick());
            appList.addView(line);
        }
    }

    private void choose(String pkg, Switch sw) {
        ExitRuntime.Snapshot now = rt.snapshot();
        Choices.Choice c = Presenter.toggled(now.choices, pkg);
        if (c == Choices.Choice.TOR && now.choices.torApps().size() >= Choices.MAX_TOR_APPS
                && now.choices.choiceOf(pkg) != Choices.Choice.TOR) {
            sw.setChecked(false);
            Toast.makeText(this, getString(R.string.apps_limit, Choices.MAX_TOR_APPS), Toast.LENGTH_LONG).show();
            return;
        }
        rt.setChoice(pkg, c);
    }

    private void renderOverrides(ExitRuntime.Snapshot s) {
        content.addView(title(getString(R.string.overrides_title)));
        List<Presenter.OverrideRow> rows = Presenter.overrideRows(s.choices, names);
        if (rows.isEmpty()) {
            content.addView(summary(getString(R.string.overrides_empty)));
        }
        for (Presenter.OverrideRow r : rows) {
            String company = r.companyName != null ? r.companyName : getString(R.string.notif_unknown_company);
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(this);
            t.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
            t.setText(getString(R.string.override_row, label(r.pkg), company));
            line.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button remove = new Button(this);
            remove.setText(R.string.override_remove);
            remove.setOnClickListener(v -> rt.setShowsAddress(r.pkg, r.company, false));
            line.addView(remove);
            content.addView(line);
        }
    }

    // ---- small helpers ---------------------------------------------------------------------------------------------

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
        t.setText(text);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    private TextView heading(String text) {
        TextView t = new TextView(this);
        t.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
        t.setText(text);
        t.setPadding(0, dp(24), 0, dp(4));
        return t;
    }

    private TextView summary(String text) {
        TextView t = new TextView(this);
        t.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
        t.setText(text);
        t.setPadding(0, dp(4), 0, dp(8));
        return t;
    }

    private View row(String title, String summary, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, dp(12), 0, dp(12));
        TextView t = new TextView(this);
        t.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
        t.setText(title);
        r.addView(t);
        TextView s = new TextView(this);
        s.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
        s.setText(summary);
        r.addView(s);
        r.setOnClickListener(click);
        return r;
    }

    private String statusText(Status st) {
        switch (st.phase) {
            case RUNNING:
                return getString(R.string.status_running);
            case CONNECTING:
                return getString(R.string.status_connecting);
            case PAUSED:
                return getString(R.string.status_paused);
            default:
                return getString(R.string.status_idle);
        }
    }

    private static int warningText(Policy.Warning w) {
        switch (w) {
            case VPN_ACTIVE:
                return R.string.warn_vpn;
            case PRIVATE_DNS_LIMITED:
                return R.string.warn_dns;
            case MASK_TIER_OFF_TOO_MANY_APPS:
                return R.string.warn_masks_off;
            default:
                return R.string.warn_too_many;
        }
    }

    private String label(String pkg) {
        for (Presenter.Listed l : listed) {
            if (l.pkg.equals(pkg)) {
                return l.label;
            }
        }
        return pkg;
    }

    /** The launchable apps with label and icon; read off the main thread, drawn when they are there. */
    private void loadApps() {
        final PackageManager pm = getPackageManager();
        new Thread(() -> {
            List<Presenter.Listed> out = new ArrayList<>();
            Map<String, Drawable> ic = new HashMap<>();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!ic.containsKey(pkg)) {
                    out.add(new Presenter.Listed(pkg, String.valueOf(ri.loadLabel(pm)), ri.activityInfo.applicationInfo.uid));
                    ic.put(pkg, ri.loadIcon(pm));
                }
            }
            main.post(() -> {
                listed = out;
                icons.putAll(ic);
                if (page == Page.APPS || page == Page.OVERRIDES) {
                    render();
                }
            });
        }, "exit-apps").start();
    }
}
