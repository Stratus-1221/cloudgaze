package io.github.stratus1221.cloudgaze.launcher;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.View;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Every app with an icon (including work-profile apps), plus favorites. */
final class Apps {
    static final class App {
        final String label, key, lower;
        final ComponentName cn;
        final UserHandle user;
        final LauncherActivityInfo info;
        final boolean work;
        Drawable icon;

        App(LauncherActivityInfo i, long serial, boolean work) {
            info = i;
            cn = i.getComponentName();
            user = i.getUser();
            label = String.valueOf(i.getLabel());
            lower = label.toLowerCase(Locale.ROOT);
            key = cn.flattenToString() + "#" + serial;
            this.work = work;
        }

        Drawable icon(int density) {
            if (icon == null) icon = info.getBadgedIcon(density);
            return icon;
        }

        String letter() {
            if (label.isEmpty()) return "#";
            char ch = Character.toUpperCase(label.charAt(0));
            return Character.isLetter(ch) ? String.valueOf(ch) : "#";
        }
    }

    private Apps() {}

    static List<App> load(Context c) {
        LauncherApps la = c.getSystemService(LauncherApps.class);
        UserManager um = c.getSystemService(UserManager.class);
        UserHandle me = android.os.Process.myUserHandle();
        List<App> out = new ArrayList<>();
        for (UserHandle u : um.getUserProfiles()) {
            long serial = um.getSerialNumberForUser(u);
            for (LauncherActivityInfo i : la.getActivityList(null, u)) {
                if (i.getComponentName().getPackageName().equals(c.getPackageName())) continue;
                out.add(new App(i, serial, !u.equals(me)));
            }
        }
        Collator col = Collator.getInstance();
        col.setStrength(Collator.PRIMARY);
        Collections.sort(out, (a, b) -> col.compare(a.label, b.label));
        return out;
    }

    /** Best matches first: name starts with it, then a word starts with it, then it appears anywhere. */
    static List<App> search(List<App> all, String q) {
        q = q.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return all;
        List<App> a = new ArrayList<>(), b = new ArrayList<>(), d = new ArrayList<>();
        for (App x : all) {
            if (x.lower.startsWith(q)) a.add(x);
            else if (x.lower.contains(" " + q)) b.add(x);
            else if (x.lower.contains(q)) d.add(x);
        }
        a.addAll(b); a.addAll(d);
        return a;
    }

    static void launch(Context c, App a, View from) {
        LauncherApps la = c.getSystemService(LauncherApps.class);
        try {
            Rect r = null;
            android.os.Bundle opts = null;
            if (from != null) {
                int[] p = new int[2];
                from.getLocationOnScreen(p);
                r = new Rect(p[0], p[1], p[0] + from.getWidth(), p[1] + from.getHeight());
                opts = ActivityOptions.makeClipRevealAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
            }
            la.startMainActivity(a.cn, a.user, r, opts);
        } catch (Exception e) {
            Toast.makeText(c, "Couldn’t open " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    static void appInfo(Context c, App a) {
        try { c.getSystemService(LauncherApps.class).startAppDetailsActivity(a.cn, a.user, null, null); }
        catch (Exception e) { Toast.makeText(c, "Couldn’t open app info", Toast.LENGTH_SHORT).show(); }
    }

    static void uninstall(Context c, App a) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.cn.getPackageName()));
        i.putExtra(Intent.EXTRA_USER, a.user);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (Exception e) { Toast.makeText(c, "Couldn’t uninstall " + a.label, Toast.LENGTH_SHORT).show(); }
    }

    // favorites are stored as app keys, one per line, in the order shown
    static List<String> favorites(SharedPreferences p) {
        List<String> out = new ArrayList<>();
        for (String s : p.getString("favorites", "").split("\n")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    static void setFavorites(SharedPreferences p, List<String> keys) {
        p.edit().putString("favorites", String.join("\n", keys)).apply();
    }

    static App find(List<App> all, String key) {
        for (App a : all) if (a.key.equals(key)) return a;
        return null;
    }
}
