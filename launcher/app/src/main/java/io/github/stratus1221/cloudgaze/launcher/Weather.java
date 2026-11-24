package io.github.stratus1221.cloudgaze.launcher;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Weather from Open-Meteo (free, no account), the same source cloudgaze uses. Call these off the main thread. */
final class Weather {
    private Weather() {}

    static String describe(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Cloudy";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: return "Light snow";
            case 73: case 77: return "Snow";
            case 75: return "Heavy snow";
            case 80: case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85: case 86: return "Snow showers";
            case 95: case 96: case 99: return "Thunderstorm";
            default: return "";
        }
    }

    static String get(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(10000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            h.disconnect();
        }
    }

    /** "34° · Mostly clear · 39° / 27°" */
    static String now(SharedPreferences p) throws Exception {
        float lat = p.getFloat("lat", Float.NaN), lon = p.getFloat("lon", Float.NaN);
        if (Float.isNaN(lat)) return null;
        boolean f = "f".equals(p.getString("unit", defaultUnit()));
        String url = String.format(Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1%s",
            lat, lon, f ? "&temperature_unit=fahrenheit" : "");
        JSONObject j = new JSONObject(get(url));
        JSONObject cur = j.getJSONObject("current");
        JSONObject d = j.getJSONObject("daily");
        long t = Math.round(cur.getDouble("temperature_2m"));
        long hi = Math.round(d.getJSONArray("temperature_2m_max").getDouble(0));
        long lo = Math.round(d.getJSONArray("temperature_2m_min").getDouble(0));
        String desc = describe(cur.getInt("weather_code"));
        return t + "°" + (desc.isEmpty() ? "" : " · " + desc) + " · " + hi + "° / " + lo + "°";
    }

    /** Find a place by city name or US ZIP code. Returns {name, lat, lon} or null. */
    static String[] find(String q) throws Exception {
        q = q.trim();
        if (q.matches("\\d{5}")) {
            JSONObject j = new JSONObject(get("https://api.zippopotam.us/us/" + q));
            JSONObject pl = j.getJSONArray("places").getJSONObject(0);
            return new String[] { pl.getString("place name") + ", " + pl.getString("state abbreviation"), pl.getString("latitude"), pl.getString("longitude") };
        }
        JSONObject j = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(q, "UTF-8")));
        JSONArray r = j.optJSONArray("results");
        if (r == null || r.length() == 0) return null;
        JSONObject o = r.getJSONObject(0);
        String name = o.getString("name") + (o.has("admin1") ? ", " + o.getString("admin1") : "");
        return new String[] { name, String.valueOf(o.getDouble("latitude")), String.valueOf(o.getDouble("longitude")) };
    }

    static String defaultUnit() {
        String cc = Locale.getDefault().getCountry();
        return ("US".equals(cc) || "LR".equals(cc) || "MM".equals(cc)) ? "f" : "c";
    }
}
