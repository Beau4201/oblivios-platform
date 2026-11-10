// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

import android.app.Application;

/** A persistent app: the system starts this process at boot, before the first unlock, and again after any death. */
public final class ExitApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ExitRuntime.get(this).start();
    }
}
