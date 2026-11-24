package io.github.stratus1221.cloudgaze.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * What the launcher should show. When you're signed in and have set the launcher up in cloudgaze on your
 * computer, that wins; otherwise it uses the settings on the phone.
 */
final class Config {
    final JSONObject acct;       // your cloudgaze settings, or null
    final boolean fromAccount;   // launcher setup came from cloudgaze
    final boolean h24, seconds;
    final String drawer;
    final JSONArray home;
    final double lat, lon;
    final boolean fahrenheit;
    final String key;            // changes whenever the home screen needs rebuilding

    static final String[][] WIDGETS = {
        { "clock", "Clock" }, { "date", "Date" }, { "weather", "Weather" }, { "classes", "Class countdown" },
        { "abday", "A/B day" }, { "homework", "Homework" }, { "space", "Empty space" }, { "favorites", "Favorite apps" },
    };

    Config(Context c, SharedPreferences p) {
        acct = Account.cached(p);
        JSONObject L = acct == null ? null : acct.optJSONObject("launcher");
        fromAccount = L != null;
        String clockMode = L != null ? L.optString("clock", "") : p.getString("clock", "");
        h24 = clockMode.isEmpty() ? android.text.format.DateFormat.is24HourFormat(c) : clockMode.equals("24");
        seconds = L != null ? L.optBoolean("seconds", true) : p.getBoolean("seconds", true);
        drawer = L != null ? L.optString("drawer", "list") : p.getString("drawer", "list");
        JSONArray h = L != null ? L.optJSONArray("home") : null;
        home = h != null && h.length() > 0 ? h : defaultHome();

        JSONObject loc = acct == null ? null : acct.optJSONObject("loc");
        if (loc != null && loc.has("lat")) {
            lat = loc.optDouble("lat"); lon = loc.optDouble("lon");
            fahrenheit = !"C".equalsIgnoreCase(acct.optString("unit", "F"));
        } else {
            lat = p.getFloat("lat", Float.NaN); lon = p.getFloat("lon", Float.NaN);
            fahrenheit = "f".equals(p.getString("unit", Weather.defaultUnit()));
        }
        key = home.toString() + h24 + seconds + (acct != null);
    }

    boolean hasWeatherPlace() { return !Double.isNaN(lat) && !Double.isNaN(lon); }

    String weatherKey() { return String.format(java.util.Locale.ROOT, "%.3f,%.3f,%b", lat, lon, fahrenheit); }

    static JSONArray defaultHome() {
        JSONArray a = new JSONArray();
        String[] on = { "clock", "date", "weather", "space", "favorites" };
        for (String[] w : WIDGETS) {
            boolean shown = false;
            for (String s : on) if (s.equals(w[0])) shown = true;
            try { a.put(new JSONObject().put("type", w[0]).put("on", shown).put("size", 1)); } catch (Exception e) {}
        }
        // put the empty space just above favorites
        JSONArray out = new JSONArray();
        for (String t : new String[] { "clock", "date", "weather", "classes", "abday", "homework", "space", "favorites" })
            for (int i = 0; i < a.length(); i++) if (t.equals(a.optJSONObject(i).optString("type"))) out.put(a.optJSONObject(i));
        return out;
    }
}
