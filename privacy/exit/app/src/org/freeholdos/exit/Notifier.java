// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * The one notification per app and company and window (the dedupe is {@link Ladder}'s): "App X cannot reach host Y while it
 * is hidden", with two actions, or for an app on Tor "App X uses Tor, Tor may be why it fails". The company comes from the
 * mask list of the image; the daemon never reports a host name.
 */
final class Notifier {
    static final String CHANNEL = "exit_failures";
    static final String ACTION_SHOW = "org.freeholdos.exit.SHOW_ADDRESS";
    static final String ACTION_KEEP = "org.freeholdos.exit.KEEP_HIDING";
    static final String ACTION_DIRECT = "org.freeholdos.exit.SWITCH_TOR_OFF";
    static final String ACTION_REHIDE = "org.freeholdos.exit.HIDE_AGAIN";
    static final String EXTRA_PACKAGE = "package";
    static final String EXTRA_UID = "uid";
    static final String EXTRA_COMPANY = "company";
    private static final int CONSENT_ID = 1;

    private final Context ctx;
    private final CompanyNames names;

    Notifier(Context ctx, CompanyNames names) {
        this.ctx = ctx;
        this.names = names;
    }

    void post(String pkg, int company) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT));
        String app = label(pkg);
        Notification.Builder b = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_compartments)
                .setVisibility(Notification.VISIBILITY_SECRET) // names an app and a company: nothing of it on the lock screen
                .setAutoCancel(true);
        if (company == 0) {
            b.setContentTitle(ctx.getString(R.string.notif_tor_title, app))
                    .setContentText(ctx.getString(R.string.notif_tor_text))
                    .addAction(action(R.string.notif_tor_action, ACTION_DIRECT, pkg, company));
        } else {
            String host = names.nameOf(company);
            if (host == null) {
                host = ctx.getString(R.string.notif_unknown_company);
            }
            b.setContentTitle(ctx.getString(R.string.notif_mask_title, app, host))
                    .setContentText(ctx.getString(R.string.notif_mask_text, host))
                    .addAction(action(R.string.notif_action_show, ACTION_SHOW, pkg, company))
                    .addAction(action(R.string.notif_action_keep, ACTION_KEEP, pkg, company));
        }
        nm.notify(idOf(pkg, company), b.build());
    }

    /**
     * The speed guard passes a company on to an app (ADR-188): the app loads a lot from it, which Tor would slow down. One
     * notification per promotion (the daemon reports each as fresh once), with one action that hides the company again.
     */
    void postPromotion(String pkg, long uid, int company) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT));
        String host = names.nameOf(company);
        if (host == null) {
            host = ctx.getString(R.string.notif_unknown_company);
        }
        Intent i = new Intent(ctx, ActionReceiver.class).setAction(ACTION_REHIDE)
                .putExtra(EXTRA_PACKAGE, pkg).putExtra(EXTRA_COMPANY, company).putExtra(EXTRA_UID, uid);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, (pkg + ACTION_REHIDE + company + uid).hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        nm.notify(promotionId(pkg, company), new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_compartments)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setContentTitle(ctx.getString(R.string.notif_promo_title, label(pkg), host))
                .setContentText(ctx.getString(R.string.notif_promo_text, label(pkg), host))
                .addAction(new Notification.Action.Builder(null, ctx.getString(R.string.notif_action_rehide), pi).build())
                .setAutoCancel(true)
                .build());
    }

    /** The one question of the first start: opens the settings screen, where the consent card is. */
    void postConsent() {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(ctx, 0, new Intent(ctx, ExitSettingsActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        nm.notify(CONSENT_ID, new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_compartments)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setContentTitle(ctx.getString(R.string.consent_title))
                .setContentText(ctx.getString(R.string.notif_consent_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build());
    }

    private static int idOf(String pkg, int company) {
        return pkg.hashCode() * 31 + company;
    }

    private static int promotionId(String pkg, int company) {
        return idOf(pkg, company) ^ 0x50524f4d;
    }

    /** Takes the notification of a promotion down once the user has hidden the company again. */
    static void cancelPromotion(Context ctx, String pkg, int company) {
        ctx.getSystemService(NotificationManager.class).cancel(promotionId(pkg, company));
    }

    /** Takes the notification down once the user has chosen. */
    static void cancel(Context ctx, String pkg, int company) {
        ctx.getSystemService(NotificationManager.class).cancel(idOf(pkg, company));
    }

    private Notification.Action action(int label, String action, String pkg, int company) {
        Intent i = new Intent(ctx, ActionReceiver.class).setAction(action)
                .putExtra(EXTRA_PACKAGE, pkg).putExtra(EXTRA_COMPANY, company);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, (pkg + action + company).hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(null, ctx.getString(label), pi).build();
    }

    private String label(String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}
