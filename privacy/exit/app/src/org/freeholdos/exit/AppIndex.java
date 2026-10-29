// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The launchable apps of this user with label and icon, read off the main thread and handed to a page when they are there.
 * The pages of Compartments list apps by these names; nothing here is stored.
 */
final class AppIndex {
    static final AppIndex EMPTY = new AppIndex(Collections.<Presenter.Listed>emptyList(), Collections.<String, Drawable>emptyMap());

    interface Callback {
        void onLoaded(AppIndex index);
    }

    final List<Presenter.Listed> listed;
    final Map<String, Drawable> icons;

    private AppIndex(List<Presenter.Listed> listed, Map<String, Drawable> icons) {
        this.listed = listed;
        this.icons = icons;
    }

    /** The label of a package, or the package name when the app is not listed. */
    String label(String pkg) {
        for (Presenter.Listed l : listed) {
            if (l.pkg.equals(pkg)) {
                return l.label;
            }
        }
        return pkg;
    }

    /** The label of the app that has this uid, or {@code fallback}. */
    String labelOfUid(long uid, String fallback) {
        for (Presenter.Listed l : listed) {
            if (l.uid == uid) {
                return l.label;
            }
        }
        return fallback;
    }

    /** Reads the apps on a thread of its own and calls {@code cb} on the thread of {@code main} when it is done. */
    static void load(Context ctx, Handler main, Callback cb) {
        final PackageManager pm = ctx.getPackageManager();
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
            main.post(() -> cb.onLoaded(new AppIndex(out, ic)));
        }, "exit-apps").start();
    }
}
