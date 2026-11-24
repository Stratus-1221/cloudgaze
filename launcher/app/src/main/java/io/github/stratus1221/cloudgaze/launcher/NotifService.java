package io.github.stratus1221.cloudgaze.launcher;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads your notifications (only after you allow it in Android settings) so the launcher can show them:
 * dots on favorites, the notifications widget, and the list in the controls panel. Nothing leaves the phone.
 */
public class NotifService extends NotificationListenerService {
    static final class Item {
        String key, pkg, app, title, text;
        UserHandle user;
        long when;
        boolean clearable, autoCancel;
        PendingIntent intent;
    }

    private static NotifService me;
    private static List<Item> items = new ArrayList<>();
    private static final List<Runnable> watchers = new ArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    static List<Item> items() { return items; }
    static boolean connected() { return me != null; }
    static void watch(Runnable r) { if (!watchers.contains(r)) watchers.add(r); }

    static boolean allowed(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(new ComponentName(c, NotifService.class).flattenToString());
    }

    static Intent accessSettings() {
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** How many clearable notifications each app has, keyed by package name + user. */
    static Map<String, Integer> counts() {
        Map<String, Integer> m = new HashMap<>();
        for (Item i : items) if (i.clearable) { String k = i.pkg + "#" + i.user.hashCode(); m.put(k, m.getOrDefault(k, 0) + 1); }
        return m;
    }

    static void dismiss(Item i) { if (me != null && i.clearable) try { me.cancelNotification(i.key); } catch (Exception e) {} }

    static void clearAll() { if (me != null) try { me.cancelAllNotifications(); } catch (Exception e) {} }

    static void open(Context c, Item i) {
        try {
            if (i.intent != null) {
                ActivityOptions o = ActivityOptions.makeBasic();
                if (Build.VERSION.SDK_INT >= 33) o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                i.intent.send(c, 0, null, null, null, null, o.toBundle());
            } else {
                Intent l = c.getPackageManager().getLaunchIntentForPackage(i.pkg);
                if (l != null) c.startActivity(l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            if (i.autoCancel) dismiss(i);
        } catch (Exception e) { /* the app took it back */ }
    }

    static String ago(long when) {
        long m = (System.currentTimeMillis() - when) / 60000;
        if (m < 1) return "now";
        if (m < 60) return m + "m";
        if (m < 24 * 60) return (m / 60) + "h";
        return (m / (24 * 60)) + "d";
    }

    @Override public void onListenerConnected() { me = this; rebuild(); }
    @Override public void onListenerDisconnected() { me = null; items = new ArrayList<>(); tell(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { rebuild(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { rebuild(); }

    private void rebuild() {
        List<Item> out = new ArrayList<>();
        StatusBarNotification[] all;
        try { all = getActiveNotifications(); } catch (Exception e) { return; }
        if (all == null) return;
        PackageManager pm = getPackageManager();
        Map<String, String> labels = new HashMap<>();
        for (StatusBarNotification s : all) {
            Notification n = s.getNotification();
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
            boolean media = Notification.CATEGORY_TRANSPORT.equals(n.category);
            if (!s.isClearable() && !media) continue; // skip "running in background" style notices
            Bundle x = n.extras;
            Item i = new Item();
            i.key = s.getKey();
            i.pkg = s.getPackageName();
            i.user = s.getUser();
            i.when = s.getPostTime();
            i.clearable = s.isClearable();
            i.autoCancel = (n.flags & Notification.FLAG_AUTO_CANCEL) != 0;
            i.intent = n.contentIntent;
            CharSequence t = x.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence b = x.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (b == null) b = x.getCharSequence(Notification.EXTRA_TEXT);
            i.title = t == null ? "" : t.toString();
            i.text = b == null ? "" : b.toString().replace('\n', ' ');
            if (i.title.isEmpty() && i.text.isEmpty()) continue;
            String label = labels.get(i.pkg);
            if (label == null) {
                try { label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(i.pkg, 0))); } catch (Exception e) { label = i.pkg; }
                labels.put(i.pkg, label);
            }
            i.app = label;
            out.add(i);
        }
        Collections.sort(out, (p, q) -> Long.compare(q.when, p.when));
        items = out;
        tell();
    }

    private static void tell() {
        main.post(() -> { for (Runnable r : new ArrayList<>(watchers)) r.run(); });
    }
}
