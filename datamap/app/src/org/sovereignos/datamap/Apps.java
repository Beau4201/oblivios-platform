// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The names and icons of the apps of the current user, as the system gives them: no logo of any app is stored in
 * this package. (Company logos are another matter: ADR-159.)
 */
final class Apps {
    private final Context mContext;
    private final PackageManager mPm;
    // read on the main thread and warmed on the reader thread, so the maps are concurrent
    private final Map<String, String> mLabels = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Boolean> mInstalled = new java.util.concurrent.ConcurrentHashMap<>();
    /** The themed icon of each package once loaded; a package with no icon is in {@code mNoIcon}. */
    private final Map<String, Drawable> mIcons = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> mNoIcon = java.util.concurrent.ConcurrentHashMap.newKeySet();

    Apps(Context context) {
        mContext = context;
        mPm = context.getPackageManager();
    }

    /** The service refuses a longer list (DataMapService.MAX_PACKAGES); a phone with more keeps the first ones. */
    private static final int MAX_PACKAGES = 2000;

    /** Every installed package of this user: the list that publishers are looked up for. */
    String[] installedPackages() {
        List<String> out = new ArrayList<>();
        for (ApplicationInfo info : mPm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))) {
            if (out.size() == MAX_PACKAGES) {
                break;
            }
            out.add(info.packageName);
        }
        return out.toArray(new String[0]);
    }

    boolean isInstalled(String pkg) {
        Boolean known = mInstalled.get(pkg);
        if (known == null) {
            try {
                mPm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0));
                known = true;
            } catch (PackageManager.NameNotFoundException e) {
                known = false;
            }
            mInstalled.put(pkg, known);
        }
        return known;
    }

    String label(String pkg) {
        if ("android".equals(pkg)) {
            return mContext.getString(R.string.app_system);
        }
        String label = mLabels.get(pkg);
        if (label == null) {
            label = pkg;
            try {
                label = mPm.getApplicationLabel(
                        mPm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString();
            } catch (PackageManager.NameNotFoundException e) {
                // an app that is gone keeps its package name
            }
            mLabels.put(pkg, label);
        }
        return label;
    }

    /**
     * The icon of the app as the design page draws it (ADR-160): the app's own icon, the whole picture. An app with
     * no icon at all gives null, so that the caller draws the initials and not the platform's default robot.
     */
    Drawable icon(String pkg) {
        Drawable themed = mIcons.get(pkg);
        if (themed == null) {
            if (mNoIcon.contains(pkg) || "android".equals(pkg) || !isInstalled(pkg)) {
                return null;
            }
            try {
                ApplicationInfo info = mPm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0));
                Drawable raw = info.icon != 0 ? mPm.getApplicationIcon(pkg) : launcherIcon(pkg);
                // C13 (MAP-014): the launcher's themed icon where the app has a monochrome layer
                themed = raw == null ? null : ThemedIcons.of(mContext, raw);
            } catch (PackageManager.NameNotFoundException e) {
                themed = null;
            }
            if (themed == null) {
                mNoIcon.add(pkg);
                return null;
            }
            mIcons.put(pkg, themed);
        }
        // a copy for each caller: bounds and alpha are the caller's own
        return AppIcons.fullBleed(themed);
    }

    /** Loads the labels and icons of these packages now, so that the screens do not do it while they are built. */
    void warm(java.util.Collection<String> packages) {
        for (String pkg : packages) {
            label(pkg);
            icon(pkg);
        }
    }

    /** An app with no icon of its own may still have one on its launcher entry. */
    private Drawable launcherIcon(String pkg) {
        Intent launch = mPm.getLaunchIntentForPackage(pkg);
        if (launch == null || launch.getComponent() == null) {
            return null;
        }
        try {
            ActivityInfo activity = mPm.getActivityInfo(launch.getComponent(), PackageManager.ComponentInfoFlags.of(0));
            if (activity.icon == 0 && activity.applicationInfo.icon == 0) {
                return null;
            }
            return mPm.getActivityIcon(launch.getComponent());
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
