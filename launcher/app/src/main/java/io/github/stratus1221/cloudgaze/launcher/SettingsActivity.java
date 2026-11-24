package io.github.stratus1221.cloudgaze.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Launcher settings, laid out like cloudgaze's settings panel. */
public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout box;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF000000);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(box);
        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        scroll.setOnApplyWindowInsetsListener((v, ins) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets s = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                top = s.top; bottom = s.bottom;
            } else { top = ins.getSystemWindowInsetTop(); bottom = ins.getSystemWindowInsetBottom(); }
            box.setPadding(dp(22), top + dp(22), dp(22), bottom + dp(30));
            return ins;
        });
        build();
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private void build() {
        TextView title = Ui.text(this, "Launcher settings", 24, Ui.C1, Ui.LIGHT);
        box.addView(title);
        gap(22);

        TextView makeHome = Ui.button(this, "Make cloudgaze your home screen");
        makeHome.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
        box.addView(makeHome, new LinearLayout.LayoutParams(-1, -2));
        note("Pick cloudgaze as your default home app. Press the home button to come back here any time.");

        head("App list");
        row("Style", Ui.seg(this, new String[][] { { "list", "List" }, { "search", "Search" }, { "grid", "Icons" } },
            prefs.getString("drawer", "list"), v -> prefs.edit().putString("drawer", v).apply()));
        note("List: every app A–Z with a letter strip. Search: the keyboard opens right away, type a few letters. Icons: a grid of app icons. Swipe up on the home screen to open it.");

        head("Clock");
        String clockNow = prefs.getString("clock", "");
        if (clockNow.isEmpty()) clockNow = android.text.format.DateFormat.is24HourFormat(this) ? "24" : "12";
        row("Hours", Ui.seg(this, new String[][] { { "12", "12h" }, { "24", "24h" } }, clockNow, v -> prefs.edit().putString("clock", v).apply()));
        row("Seconds", Ui.seg(this, new String[][] { { "on", "On" }, { "off", "Off" } }, prefs.getBoolean("seconds", true) ? "on" : "off",
            v -> prefs.edit().putBoolean("seconds", v.equals("on")).apply()));

        head("Weather");
        row("Units", Ui.seg(this, new String[][] { { "f", "°F" }, { "c", "°C" } }, prefs.getString("unit", Weather.defaultUnit()),
            v -> prefs.edit().putString("unit", v).putLong("wxAt", 0).putString("wxText", "").apply()));

        TextView place = Ui.text(this, placeText(), 14, Ui.C3, Ui.REGULAR);
        LinearLayout findRow = new LinearLayout(this);
        findRow.setOrientation(LinearLayout.HORIZONTAL);
        findRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText where = new EditText(this);
        where.setHint("City or ZIP code");
        where.setHintTextColor(0xFF555555);
        where.setTextColor(Ui.C1);
        where.setTextSize(16);
        where.setSingleLine(true);
        where.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        where.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        where.setBackground(Ui.box(this, 0, Ui.LINE, 8));
        where.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView find = Ui.button(this, "Find");
        Runnable go = () -> {
            String q = where.getText().toString();
            if (q.trim().isEmpty()) return;
            place.setText("Looking…");
            new Thread(() -> {
                String msg;
                try {
                    String[] r = Weather.find(q);
                    if (r == null) msg = "Couldn’t find “" + q.trim() + "”. Try a nearby city.";
                    else {
                        prefs.edit().putString("place", r[0]).putFloat("lat", Float.parseFloat(r[1])).putFloat("lon", Float.parseFloat(r[2]))
                            .putLong("wxAt", 0).putString("wxText", "").apply();
                        msg = placeText();
                    }
                } catch (Exception e) { msg = "Couldn’t look that up. Check your internet."; }
                String m = msg;
                runOnUiThread(() -> place.setText(m));
            }).start();
        };
        find.setOnClickListener(v -> go.run());
        where.setOnEditorActionListener((v, id, ev) -> { go.run(); return true; });
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.rightMargin = dp(10);
        findRow.addView(where, wl);
        findRow.addView(find);
        gap(10);
        box.addView(findRow, new LinearLayout.LayoutParams(-1, -2));
        gap(10);
        box.addView(place);

        head("Favorites");
        note("Hold any app in the app list and choose Add to favorites. Hold a favorite on the home screen to move or remove it.");

        head("Tips");
        note("Tap the clock to open your alarms. Tap the date to open your calendar. Hold empty space on the home screen to come back here.");

        gap(30);
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) {}
        TextView foot = Ui.text(this, "cloudgaze launcher " + ver + " · made by Stratus1221", 12, Ui.C4, Ui.REGULAR);
        foot.setGravity(Gravity.CENTER);
        box.addView(foot, new LinearLayout.LayoutParams(-1, -2));
    }

    private String placeText() {
        String p = prefs.getString("place", "");
        return p.isEmpty() ? "No location yet" : "Showing weather for " + p;
    }

    private void head(String s) {
        gap(30);
        TextView t = Ui.text(this, s.toUpperCase(), 11, Ui.C3, Ui.REGULAR);
        t.setLetterSpacing(0.12f);
        box.addView(t);
        gap(6);
    }

    private void row(String label, View control) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, dp(8));
        r.addView(Ui.text(this, label, 16, 0xFFAAAAAA, Ui.REGULAR), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(control);
        box.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    private void note(String s) {
        TextView t = Ui.text(this, s, 13, Ui.C4, Ui.REGULAR);
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(0, dp(8), 0, 0);
        box.addView(t);
    }

    private void gap(int d) {
        box.addView(new View(this), new LinearLayout.LayoutParams(1, dp(d)));
    }
}
