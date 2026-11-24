package io.github.stratus1221.cloudgaze.launcher;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The panel that slides down from the top: flashlight, camera, connections and quick shortcuts. */
final class Controls {
    interface Done { void run(); }

    private final Activity a;
    private final Done onClose;
    private final Handler h = new Handler(Looper.getMainLooper());
    final LinearLayout view;
    private final GridLayout grid;
    private final List<Runnable> refreshers = new ArrayList<>();
    private boolean open, torchOn;
    private String torchId;
    private boolean h24;
    private final LinearLayout notifList;
    private final ScrollView notifScroll;

    Controls(Activity a, Done onClose) {
        this.a = a;
        this.onClose = onClose;
        view = new LinearLayout(a);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackgroundColor(0xF7000000);
        view.setVisibility(View.GONE);
        view.setClickable(true);
        TextView head = Ui.text(a, "CONTROLS", 11, Ui.C4, Ui.REGULAR);
        head.setLetterSpacing(0.12f);
        head.setPadding(dp(4), 0, 0, dp(12));
        view.addView(head);
        grid = new GridLayout(a);
        grid.setColumnCount(3);
        view.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout nh = new LinearLayout(a);
        nh.setOrientation(LinearLayout.HORIZONTAL);
        nh.setPadding(dp(4), dp(22), dp(4), 0);
        TextView nt = Ui.text(a, "NOTIFICATIONS", 11, Ui.C4, Ui.REGULAR);
        nt.setLetterSpacing(0.12f);
        TextView clear = Ui.text(a, "Clear all", 12, Ui.C3, Ui.REGULAR);
        clear.setOnClickListener(v -> NotifService.clearAll());
        nh.addView(nt, new LinearLayout.LayoutParams(0, -2, 1f));
        nh.addView(clear);
        view.addView(nh);
        notifScroll = new ScrollView(a);
        notifScroll.setVerticalScrollBarEnabled(false);
        notifList = new LinearLayout(a);
        notifList.setOrientation(LinearLayout.VERTICAL);
        notifList.setPadding(dp(4), 0, dp(4), dp(6));
        notifScroll.addView(notifList);
        view.addView(notifScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView sys = Ui.button(a, "More phone settings");
        sys.setTextColor(0xFF999999);
        sys.setOnClickListener(v -> expandSystemPanel());
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = dp(14);
        view.addView(sys, sl);
        TextView hint = Ui.text(a, "Swipe up to close", 11, Ui.C4, Ui.REGULAR);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(14), 0, 0);
        view.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        view.setOnClickListener(v -> close());
        setupTorch();
        build();
    }

    private int dp(float v) { return Ui.dp(a, v); }

    boolean isOpen() { return open; }

    void setPadding(int top, int bottom) { view.setPadding(dp(16), top + dp(14), dp(16), bottom + dp(10)); }

    void setH24(boolean v) { h24 = v; }

    // ---------- tiles ----------

    private interface Status { String get(); }
    private interface On { boolean get(); }

    private void tile(String name, Status sub, On on, View.OnClickListener tap) {
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(13), dp(14), dp(10), dp(13));
        t.setMinimumHeight(dp(78));
        TextView n = Ui.text(a, name, 15, 0xFFDDDDDD, Ui.REGULAR);
        TextView s = Ui.text(a, "", 12, Ui.C3, Ui.REGULAR);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(6);
        t.addView(n);
        t.addView(s, sp);
        t.setClickable(true);
        t.setOnClickListener(v -> { tap.onClick(v); h.postDelayed(this::refresh, 400); });
        GridLayout.LayoutParams gl = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        gl.width = 0;
        gl.setMargins(dp(4), dp(4), dp(4), dp(4));
        grid.addView(t, gl);
        refreshers.add(() -> {
            boolean lit = on != null && on.get();
            t.setBackground(Ui.box(a, lit ? 0xFF262626 : 0xFF0D0D0D, lit ? 0xFF4A4A4A : Ui.LINE, 16));
            n.setTextColor(lit ? 0xFFFFFFFF : 0xFFCCCCCC);
            String st = sub == null ? "" : sub.get();
            s.setText(st);
            s.setVisibility(st.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    private void build() {
        tile("Flashlight", () -> torchId == null ? "Not on this phone" : torchOn ? "On" : "Off", () -> torchOn, v -> toggleTorch());
        tile("Camera", null, null, v -> go(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)));
        tile("Wi-Fi", this::wifiText, this::wifiOn, v -> go(panel(Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)));
        tile("Mobile data", this::cellText, this::cellOn, v -> go(panel(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_DATA_ROAMING_SETTINGS)));
        tile("Bluetooth", this::btText, this::btOn, v -> go(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        tile("Airplane", () -> airplane() ? "On" : "Off", this::airplane, v -> go(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)));
        tile("Do not disturb", this::dndText, this::dndOn, v -> toggleDnd());
        tile("Sound", null, null, v -> go(panel(Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS)));
        tile("Battery", this::batteryText, null, v -> go(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)));
        tile("Alarm", this::alarmText, null, v -> go(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        tile("Timer", null, null, v -> go(new Intent(AlarmClock.ACTION_SHOW_TIMERS)));
        tile("Calculator", null, null, v -> go(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)));
        tile("Settings", null, null, v -> go(new Intent(Settings.ACTION_SETTINGS)));
    }

    private Intent panel(String action, String fallback) {
        return Build.VERSION.SDK_INT >= 29 ? new Intent(action) : new Intent(fallback);
    }

    private void go(Intent i) {
        try { a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { Toast.makeText(a, "No app for that on this phone", Toast.LENGTH_SHORT).show(); }
    }

    // ---------- flashlight ----------

    private void setupTorch() {
        CameraManager cm = a.getSystemService(CameraManager.class);
        try {
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) { torchId = id; break; }
            }
            cm.registerTorchCallback(new CameraManager.TorchCallback() {
                @Override public void onTorchModeChanged(String id, boolean enabled) {
                    if (id.equals(torchId)) { torchOn = enabled; if (open) refresh(); }
                }
            }, h);
        } catch (Exception e) { torchId = null; }
    }

    private void toggleTorch() {
        if (torchId == null) return;
        try { a.getSystemService(CameraManager.class).setTorchMode(torchId, !torchOn); }
        catch (Exception e) { Toast.makeText(a, "The flashlight is busy (is the camera open?)", Toast.LENGTH_SHORT).show(); }
    }

    // ---------- connections ----------

    private NetworkCapabilities caps() {
        ConnectivityManager cm = a.getSystemService(ConnectivityManager.class);
        Network n = cm.getActiveNetwork();
        return n == null ? null : cm.getNetworkCapabilities(n);
    }

    private boolean wifiOn() {
        try { return a.getSystemService(WifiManager.class).isWifiEnabled(); } catch (Exception e) { return false; }
    }

    private String wifiText() {
        if (!wifiOn()) return "Off";
        NetworkCapabilities c = caps();
        return c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Connected" : "On, not connected";
    }

    private boolean cellOn() {
        NetworkCapabilities c = caps();
        return c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    private String cellText() {
        String carrier = "";
        try { carrier = a.getSystemService(TelephonyManager.class).getNetworkOperatorName(); } catch (Exception e) {}
        if (airplane()) return "Airplane mode";
        if (cellOn()) return carrier.isEmpty() ? "In use" : carrier;
        return carrier.isEmpty() ? "No service" : carrier;
    }

    private boolean btOn() {
        try {
            BluetoothAdapter b = a.getSystemService(BluetoothManager.class).getAdapter();
            return b != null && b.isEnabled();
        } catch (Exception e) { return false; }
    }

    private String btText() { return btOn() ? "On" : "Off"; }

    private boolean airplane() {
        return Settings.Global.getInt(a.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
    }

    // ---------- do not disturb ----------

    private boolean dndOn() {
        NotificationManager nm = a.getSystemService(NotificationManager.class);
        return nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL;
    }

    private String dndText() { return dndOn() ? "On" : "Off"; }

    private void toggleDnd() {
        NotificationManager nm = a.getSystemService(NotificationManager.class);
        if (!nm.isNotificationPolicyAccessGranted()) {
            Toast.makeText(a, "Allow cloudgaze to change Do not disturb, then come back", Toast.LENGTH_LONG).show();
            go(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            return;
        }
        nm.setInterruptionFilter(dndOn() ? NotificationManager.INTERRUPTION_FILTER_ALL : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
    }

    // ---------- battery and alarm ----------

    private String batteryText() {
        BatteryManager b = a.getSystemService(BatteryManager.class);
        int pct = b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return pct + "%" + (b.isCharging() ? " · charging" : "");
    }

    private String alarmText() {
        AlarmManager.AlarmClockInfo next = a.getSystemService(AlarmManager.class).getNextAlarmClock();
        if (next == null) return "None set";
        java.time.ZonedDateTime t = Instant.ofEpochMilli(next.getTriggerTime()).atZone(ZoneId.systemDefault());
        boolean soon = next.getTriggerTime() - System.currentTimeMillis() < 20 * 3600 * 1000L;
        String time = t.format(DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "h:mm a", Locale.getDefault()));
        return soon ? time : t.format(DateTimeFormatter.ofPattern("EEE ", Locale.getDefault())) + time;
    }

    // ---------- open / close ----------

    void refresh() { for (Runnable r : refreshers) r.run(); }

    void refreshNotifs() { if (open) NotifUi.fill(a, notifList, 30, 1f, this::close); }

    /** Swiping up closes the panel, unless you're scrolling up through a long notification list. */
    boolean canClose() { return !notifScroll.canScrollVertically(1); }

    void show() {
        if (open) return;
        open = true;
        refresh();
        refreshNotifs();
        notifScroll.scrollTo(0, 0);
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.setTranslationY(-dp(60));
        view.animate().alpha(1f).translationY(0).setDuration(170).start();
        tick();
    }

    private void tick() {
        if (!open) return;
        refresh();
        h.postDelayed(this::tick, 2000);
    }

    void close() {
        if (!open) return;
        open = false;
        view.animate().alpha(0f).translationY(-dp(40)).setDuration(140).withEndAction(() -> view.setVisibility(View.GONE)).start();
        onClose.run();
    }

    /** Opens Android's own quick settings, for anything this panel doesn't have. */
    private void expandSystemPanel() {
        try {
            Object sb = a.getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel").invoke(sb);
        } catch (Exception e) {
            go(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
