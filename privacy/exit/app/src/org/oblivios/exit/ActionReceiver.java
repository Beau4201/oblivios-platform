// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The buttons of the notification. The receiver is not exported: only the pending intents this app made reach it. It does
 * nothing but hand the choice to the runtime's thread.
 */
public final class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String pkg = intent.getStringExtra(Notifier.EXTRA_PACKAGE);
        int company = intent.getIntExtra(Notifier.EXTRA_COMPANY, 0);
        String action = intent.getAction();
        if (pkg == null || action == null) {
            return;
        }
        ExitRuntime rt = ExitRuntime.get(context);
        if (action.equals(Notifier.ACTION_REHIDE)) {
            Notifier.cancelPromotion(context, pkg, company);
            long uid = intent.getLongExtra(Notifier.EXTRA_UID, -1);
            if (uid >= 0 && company > 0) {
                rt.release(uid, company);
            }
            return;
        }
        Notifier.cancel(context, pkg, company);
        switch (action) {
            case Notifier.ACTION_SHOW:
                rt.setShowsAddress(pkg, company, true);
                break;
            case Notifier.ACTION_DIRECT:
                rt.setChoice(pkg, Choices.Choice.DIRECT);
                break;
            case Notifier.ACTION_KEEP:
                // Keeping the address hidden is what already happens; the window of the ladder holds the next notification.
                break;
            default:
                break;
        }
    }
}
