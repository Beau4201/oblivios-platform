// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The names and icons of the apps of the current user, as the system gives them: the icon is the one the
 * launcher shows (MAP-014), and no logo of any app is stored in this package.
 */
final class Apps {
    private final Context mContext;
    private final PackageManager mPm;
    private final Map<String, String> mLabels = new HashMap<>();
    private final Map<String, Boolean> mInstalled = new HashMap<>();

    Apps(Context context) {
        mContext = context;
        mPm = context.getPackageManager();
    }

    /** Every installed package of this user: the list that publishers are looked up for. */
    String[] installedPackages() {
        List<String> out = new ArrayList<>();
        for (ApplicationInfo info : mPm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))) {
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

    /** The icon of the app, or null if it has none (the caller then draws the initials). */
    Drawable icon(String pkg) {
        if ("android".equals(pkg) || !isInstalled(pkg)) {
            return null;
        }
        try {
            return mPm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
