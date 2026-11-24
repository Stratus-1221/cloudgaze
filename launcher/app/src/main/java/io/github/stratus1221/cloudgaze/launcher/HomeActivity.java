package io.github.stratus1221.cloudgaze.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SectionIndexer;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends Activity {
    private SharedPreferences prefs;
    private List<Apps.App> apps = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());

    private FrameLayout root, drawerBody;
    private LinearLayout home, favBox, drawer;
    private TextView clock, secs, date, weather, hint, empty;
    private EditText search;
    private ListView list;
    private GridView grid;
    private RowAdapter rows;
    private GridAdapter cells;
    private boolean drawerOpen;
    private GestureDetector gestures;
    private LauncherApps.Callback appsChanged;
    private long lastWeather;

    // ---------- setup ----------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        buildHome();
        buildDrawer();
        setContentView(root);
        edgeToEdge();

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null) return false;
                float dy = b.getY() - a.getY();
                boolean vertical = Math.abs(vy) > Math.abs(vx) * 1.3f;
                if (!drawerOpen && vertical && dy < -Ui.dp(HomeActivity.this, 50) && vy < -500) { openDrawer(); return true; }
                if (drawerOpen && vertical && dy > Ui.dp(HomeActivity.this, 70) && vy > 700 && listAtTop()) { closeDrawer(); return true; }
                return false;
            }
        });

        apps = Apps.load(this);
        appsChanged = new LauncherApps.Callback() {
            @Override public void onPackageRemoved(String p, UserHandle u) { reloadApps(); }
            @Override public void onPackageAdded(String p, UserHandle u) { reloadApps(); }
            @Override public void onPackageChanged(String p, UserHandle u) { reloadApps(); }
            @Override public void onPackagesAvailable(String[] p, UserHandle u, boolean r) { reloadApps(); }
            @Override public void onPackagesUnavailable(String[] p, UserHandle u, boolean r) { reloadApps(); }
        };
        getSystemService(LauncherApps.class).registerCallback(appsChanged, h);
    }

    @Override
    protected void onDestroy() {
        getSystemService(LauncherApps.class).unregisterCallback(appsChanged);
        super.onDestroy();
    }

    private void edgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int top, bottom, ime = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets s = ins.getInsets(WindowInsets.Type.systemBars());
                top = s.top; bottom = s.bottom;
                ime = ins.getInsets(WindowInsets.Type.ime()).bottom;
            } else {
                top = ins.getSystemWindowInsetTop(); bottom = ins.getSystemWindowInsetBottom();
            }
            home.setPadding(dp(30), top + dp(20), dp(30), bottom + dp(18));
            drawer.setPadding(dp(18), top + dp(14), dp(18), Math.max(bottom, ime));
            return ins;
        });
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private void reloadApps() {
        apps = Apps.load(this);
        renderFavorites();
        if (drawerOpen) refreshDrawer();
    }

    // ---------- home: clock, date, weather, favorites ----------

    private void buildHome() {
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setLongClickable(true);
        home.setOnLongClickListener(v -> { openSettings(); return true; });
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));

        home.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 0.55f));

        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.HORIZONTAL);
        time.setGravity(Gravity.CENTER_HORIZONTAL);
        time.setBaselineAligned(true);
        clock = Ui.text(this, "", 84, Ui.C1, Ui.THIN);
        secs = Ui.text(this, "", 26, Ui.C3, Ui.THIN);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.leftMargin = dp(6);
        time.addView(clock);
        time.addView(secs, sp);
        time.setOnClickListener(v -> open(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        home.addView(time, new LinearLayout.LayoutParams(-1, -2));

        date = Ui.text(this, "", 13, 0xFF8A8A8A, Ui.LIGHT);
        date.setLetterSpacing(0.14f);
        date.setGravity(Gravity.CENTER);
        date.setOnClickListener(v -> open(new Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = dp(16);
        home.addView(date, dl);

        weather = Ui.text(this, "", 15, 0xFFAAAAAA, Ui.LIGHT);
        weather.setGravity(Gravity.CENTER);
        weather.setLetterSpacing(0.03f);
        weather.setPadding(0, dp(10), 0, dp(10));
        weather.setOnClickListener(v -> {
            if (Float.isNaN(prefs.getFloat("lat", Float.NaN))) openSettings();
            else loadWeather(true);
        });
        home.addView(weather, new LinearLayout.LayoutParams(-1, -2));

        home.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f));

        favBox = new LinearLayout(this);
        favBox.setOrientation(LinearLayout.VERTICAL);
        home.addView(favBox, new LinearLayout.LayoutParams(-1, -2));

        hint = Ui.text(this, "swipe up for apps · hold for settings", 11, Ui.C4, Ui.REGULAR);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = dp(28);
        home.addView(hint, hl);
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            boolean showSecs = prefs.getBoolean("seconds", true);
            String mode = prefs.getString("clock", "");
            boolean h24 = mode.isEmpty() ? DateFormat.is24HourFormat(HomeActivity.this) : mode.equals("24");
            LocalTime t = LocalTime.now();
            int hr = h24 ? t.getHour() : (t.getHour() % 12 == 0 ? 12 : t.getHour() % 12);
            clock.setText(String.format(Locale.getDefault(), h24 ? "%02d:%02d" : "%d:%02d", hr, t.getMinute()));
            secs.setText(showSecs ? String.format(Locale.getDefault(), "%02d", t.getSecond()) : "");
            secs.setVisibility(showSecs ? View.VISIBLE : View.GONE);
            date.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())).toUpperCase(Locale.getDefault()));
            if (System.currentTimeMillis() - lastWeather > 30 * 60 * 1000L) loadWeather(false);
            long now = System.currentTimeMillis();
            h.postDelayed(this, showSecs ? 1000 - now % 1000 : 60000 - now % 60000);
        }
    };

    private void loadWeather(boolean force) {
        if (Float.isNaN(prefs.getFloat("lat", Float.NaN))) {
            weather.setText("Tap to set your weather location");
            weather.setTextColor(Ui.C4);
            return;
        }
        weather.setTextColor(0xFFAAAAAA);
        String cached = prefs.getString("wxText", "");
        if (!cached.isEmpty() && weather.getText().length() == 0) weather.setText(cached);
        if (!force && System.currentTimeMillis() - prefs.getLong("wxAt", 0) < 20 * 60 * 1000L) { lastWeather = prefs.getLong("wxAt", 0); return; }
        lastWeather = System.currentTimeMillis();
        new Thread(() -> {
            try {
                String s = Weather.now(prefs);
                if (s == null) return;
                prefs.edit().putString("wxText", s).putLong("wxAt", System.currentTimeMillis()).apply();
                runOnUiThread(() -> weather.setText(s));
            } catch (Exception e) {
                // offline: keep showing the last weather
            }
        }).start();
    }

    private void renderFavorites() {
        favBox.removeAllViews();
        List<String> keys = Apps.favorites(prefs);
        boolean any = false;
        for (String k : keys) {
            Apps.App a = Apps.find(apps, k);
            if (a == null) continue;
            any = true;
            TextView t = Ui.text(this, a.label, 21, 0xFFCFCFCF, Ui.LIGHT);
            t.setPadding(0, dp(9), 0, dp(9));
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setOnClickListener(v -> Apps.launch(this, a, v));
            t.setOnLongClickListener(v -> { favoriteMenu(a, v); return true; });
            favBox.addView(t, new LinearLayout.LayoutParams(-2, -2));
        }
        if (!any) {
            TextView t = Ui.text(this, "Your favorite apps go here.\nHold an app in the app list to add it.", 13, Ui.C4, Ui.LIGHT);
            t.setLineSpacing(dp(3), 1f);
            favBox.addView(t);
        }
    }

    private void favoriteMenu(Apps.App a, View v) {
        PopupMenu m = new PopupMenu(this, v, Gravity.START);
        Menu menu = m.getMenu();
        menu.add(0, 1, 0, "Move up");
        menu.add(0, 2, 1, "Move down");
        menu.add(0, 3, 2, "Remove from favorites");
        menu.add(0, 4, 3, "App info");
        m.setOnMenuItemClickListener(item -> {
            List<String> f = Apps.favorites(prefs);
            int i = f.indexOf(a.key);
            switch (item.getItemId()) {
                case 1: if (i > 0) { f.remove(i); f.add(i - 1, a.key); } break;
                case 2: if (i >= 0 && i < f.size() - 1) { f.remove(i); f.add(i + 1, a.key); } break;
                case 3: f.remove(a.key); break;
                case 4: Apps.appInfo(this, a); return true;
            }
            Apps.setFavorites(prefs, f);
            renderFavorites();
            return true;
        });
        m.show();
    }

    // ---------- app drawer: list, search only, or icon grid ----------

    private String drawerStyle() { return prefs.getString("drawer", "list"); }

    private void buildDrawer() {
        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(0xF7000000);
        drawer.setVisibility(View.GONE);
        drawer.setClickable(true);
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));

        search = new EditText(this);
        search.setHint("Search apps");
        search.setHintTextColor(0xFF666666);
        search.setTextColor(Ui.C1);
        search.setTextSize(16);
        search.setTypeface(Ui.REGULAR);
        search.setSingleLine(true);
        search.setBackground(Ui.box(this, 0, 0xFF2A2A2A, 10));
        search.setPadding(dp(14), dp(11), dp(14), dp(11));
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_FILTER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { if (drawerOpen) refreshDrawer(); }
        });
        search.setOnEditorActionListener((v, id, ev) -> {
            boolean go = id == EditorInfo.IME_ACTION_GO || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN);
            if (!go) return false;
            List<Apps.App> hits = Apps.search(apps, search.getText().toString());
            if (!hits.isEmpty() && search.getText().length() > 0) open(hits.get(0), search);
            return true;
        });
        drawer.addView(search, new LinearLayout.LayoutParams(-1, -2));

        drawerBody = new FrameLayout(this);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, 0, 1f);
        bl.topMargin = dp(6);
        drawer.addView(drawerBody, bl);

        rows = new RowAdapter();
        list = new ListView(this);
        list.setAdapter(rows);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(0, 0, dp(14), dp(24));
        list.setOnItemClickListener((p, v, pos, id) -> { Object o = rows.getItem(pos); if (o instanceof Apps.App) open((Apps.App) o, v); });
        list.setOnItemLongClickListener((p, v, pos, id) -> { Object o = rows.getItem(pos); if (o instanceof Apps.App) appMenu((Apps.App) o, v); return true; });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView v, int s) { if (s != SCROLL_STATE_IDLE) hideKeyboard(); }
            public void onScroll(AbsListView v, int a, int b, int c) {}
        });
        drawerBody.addView(list, new FrameLayout.LayoutParams(-1, -1));

        cells = new GridAdapter();
        grid = new GridView(this);
        grid.setAdapter(cells);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(18));
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalScrollBarEnabled(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(16), 0, dp(24));
        grid.setOnItemClickListener((p, v, pos, id) -> open(cells.getItem(pos), v));
        grid.setOnItemLongClickListener((p, v, pos, id) -> { appMenu(cells.getItem(pos), v); return true; });
        drawerBody.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        empty = Ui.text(this, "Type to find an app", 14, Ui.C4, Ui.LIGHT);
        empty.setPadding(dp(4), dp(18), 0, 0);
        drawerBody.addView(empty, new FrameLayout.LayoutParams(-1, -2));
    }

    private void refreshDrawer() {
        String style = drawerStyle(), q = search.getText().toString();
        boolean typed = !q.trim().isEmpty();
        List<Apps.App> hits = Apps.search(apps, q);
        boolean showEmpty = style.equals("search") && !typed;
        boolean useGrid = style.equals("grid");
        empty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (typed && hits.isEmpty()) { empty.setText("No apps match “" + q.trim() + "”"); empty.setVisibility(View.VISIBLE); }
        else empty.setText("Type to find an app");
        grid.setVisibility(useGrid && !showEmpty ? View.VISIBLE : View.GONE);
        list.setVisibility(!useGrid && !showEmpty ? View.VISIBLE : View.GONE);
        if (useGrid) cells.set(hits);
        else {
            boolean letters = style.equals("list") && !typed;
            rows.set(hits, letters, typed);
            list.setFastScrollEnabled(letters);
            list.setFastScrollAlwaysVisible(letters);
        }
    }

    private boolean listAtTop() {
        if (list.getVisibility() == View.VISIBLE) return !list.canScrollVertically(-1);
        if (grid.getVisibility() == View.VISIBLE) return !grid.canScrollVertically(-1);
        return true;
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        if (!prefs.getBoolean("learned", false)) prefs.edit().putBoolean("learned", true).apply();
        hint.setVisibility(View.GONE);
        search.setText("");
        refreshDrawer();
        list.setSelection(0);
        grid.setSelection(0);
        drawer.setVisibility(View.VISIBLE);
        drawer.setAlpha(0f);
        drawer.setTranslationY(dp(60));
        drawer.animate().alpha(1f).translationY(0).setDuration(170).start();
        if (drawerStyle().equals("search")) {
            search.requestFocus();
            h.postDelayed(() -> getSystemService(InputMethodManager.class).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT), 120);
        } else {
            search.clearFocus();
        }
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        hideKeyboard();
        search.clearFocus();
        drawer.animate().alpha(0f).translationY(dp(40)).setDuration(140).withEndAction(() -> drawer.setVisibility(View.GONE)).start();
    }

    private void hideKeyboard() {
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private void open(Apps.App a, View from) {
        hideKeyboard();
        Apps.launch(this, a, from);
        h.postDelayed(this::closeDrawer, 350);
    }

    private void appMenu(Apps.App a, View v) {
        PopupMenu m = new PopupMenu(this, v, Gravity.START);
        boolean fav = Apps.favorites(prefs).contains(a.key);
        m.getMenu().add(0, 1, 0, fav ? "Remove from favorites" : "Add to favorites");
        m.getMenu().add(0, 2, 1, "App info");
        m.getMenu().add(0, 3, 2, "Uninstall");
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: {
                    List<String> f = Apps.favorites(prefs);
                    if (fav) f.remove(a.key); else f.add(a.key);
                    Apps.setFavorites(prefs, f);
                    renderFavorites();
                    break;
                }
                case 2: Apps.appInfo(this, a); break;
                case 3: Apps.uninstall(this, a); break;
            }
            return true;
        });
        m.show();
    }

    /** The list style: app names with letter headers and a letter strip to jump. */
    private class RowAdapter extends BaseAdapter implements SectionIndexer {
        private final List<Object> items = new ArrayList<>();
        private String[] sections = new String[0];
        private int[] sectionAt = new int[0];
        private boolean firstBright;

        void set(List<Apps.App> apps, boolean letters, boolean typed) {
            items.clear();
            List<String> secs = new ArrayList<>();
            List<Integer> at = new ArrayList<>();
            String last = null;
            for (Apps.App a : apps) {
                if (letters && !a.letter().equals(last)) {
                    last = a.letter();
                    secs.add(last);
                    at.add(items.size());
                    items.add(last);
                }
                items.add(a);
            }
            sections = secs.toArray(new String[0]);
            sectionAt = new int[at.size()];
            for (int i = 0; i < at.size(); i++) sectionAt[i] = at.get(i);
            firstBright = typed;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int p) { return items.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int p) { return items.get(p) instanceof String ? 0 : 1; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int p) { return !(items.get(p) instanceof String); }

        @Override
        public View getView(int p, View v, ViewGroup parent) {
            Object o = items.get(p);
            if (o instanceof String) {
                TextView t = v instanceof TextView ? (TextView) v : Ui.text(HomeActivity.this, "", 11, Ui.C4, Ui.REGULAR);
                t.setLetterSpacing(0.12f);
                t.setPadding(dp(2), dp(18), 0, dp(4));
                t.setText((String) o);
                return t;
            }
            Apps.App a = (Apps.App) o;
            TextView t = v instanceof TextView ? (TextView) v : Ui.text(HomeActivity.this, "", 18, 0xFFCCCCCC, Ui.LIGHT);
            boolean top = firstBright && p == 0;
            t.setTextSize(top ? 21 : 18);
            t.setTextColor(top ? Ui.C1 : 0xFFC4C4C4);
            t.setPadding(dp(2), dp(10), 0, dp(10));
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setText(a.work ? a.label + "  · work" : a.label);
            return t;
        }

        @Override public Object[] getSections() { return sections; }
        @Override public int getPositionForSection(int s) {
            if (sectionAt.length == 0) return 0;
            return sectionAt[Math.max(0, Math.min(sectionAt.length - 1, s))];
        }
        @Override public int getSectionForPosition(int p) {
            int s = 0;
            for (int i = 0; i < sectionAt.length; i++) if (sectionAt[i] <= p) s = i;
            return s;
        }
    }

    /** The grid style: icons with names underneath. */
    private class GridAdapter extends BaseAdapter {
        private List<Apps.App> items = new ArrayList<>();
        void set(List<Apps.App> a) { items = a; notifyDataSetChanged(); }
        @Override public int getCount() { return items.size(); }
        @Override public Apps.App getItem(int p) { return items.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int p, View v, ViewGroup parent) {
            LinearLayout cell;
            ImageView img;
            TextView name;
            if (v instanceof LinearLayout) {
                cell = (LinearLayout) v;
                img = (ImageView) cell.getChildAt(0);
                name = (TextView) cell.getChildAt(1);
            } else {
                Context c = HomeActivity.this;
                cell = new LinearLayout(c);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                img = new ImageView(c);
                cell.addView(img, new LinearLayout.LayoutParams(dp(50), dp(50)));
                name = Ui.text(c, "", 11.5f, Ui.C2, Ui.REGULAR);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                name.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
                nl.topMargin = dp(7);
                cell.addView(name, nl);
            }
            Apps.App a = items.get(p);
            img.setImageDrawable(a.icon(getResources().getDisplayMetrics().densityDpi));
            name.setText(a.label);
            return cell;
        }
    }

    // ---------- the rest ----------

    private void open(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { /* no app for it */ }
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        gestures.onTouchEvent(e);
        return super.dispatchTouchEvent(e);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hint.setVisibility(prefs.getBoolean("learned", false) ? View.GONE : View.VISIBLE);
        renderFavorites();
        h.removeCallbacks(ticker);
        ticker.run();
        loadWeather(false);
        if (drawerOpen) refreshDrawer();
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        closeDrawer(); // pressing home while the app list is open goes back to the clock
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) closeDrawer();
    }
}
