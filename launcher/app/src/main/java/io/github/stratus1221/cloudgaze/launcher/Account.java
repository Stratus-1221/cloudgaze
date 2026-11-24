package io.github.stratus1221.cloudgaze.launcher;

import android.content.SharedPreferences;

import org.json.JSONObject;

import java.net.URLEncoder;

/**
 * Signs in to your cloudgaze account (the same email and password as the website) and reads your settings,
 * so the launcher can follow what you set up on your computer. It only reads; it never changes your account.
 */
final class Account {
    private static final String KEY = "AIzaSyBUq2Go-u4_ZlzkN3OA_5enLzsxsDwDlfE";
    private static final String PROJECT = "glance-b8fd4";

    private Account() {}

    static boolean signedIn(SharedPreferences p) { return !p.getString("acct_refresh", "").isEmpty(); }
    static String email(SharedPreferences p) { return p.getString("acct_email", ""); }

    static void signIn(SharedPreferences p, String email, String password) throws Exception {
        JSONObject body = new JSONObject().put("email", email.trim()).put("password", password).put("returnSecureToken", true);
        JSONObject r;
        try {
            r = new JSONObject(Net.request("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + KEY,
                "POST", "application/json", body.toString(), null));
        } catch (Net.Fail f) {
            String m = "";
            try { m = new JSONObject(f.body).getJSONObject("error").getString("message"); } catch (Exception e) {}
            if (m.contains("INVALID") || m.contains("EMAIL_NOT_FOUND") || m.contains("PASSWORD")) throw new Exception("That email or password isn’t right.");
            if (m.contains("TOO_MANY")) throw new Exception("Too many tries. Wait a bit and try again.");
            throw new Exception("Couldn’t sign in (" + (m.isEmpty() ? f.code : m) + ").");
        }
        p.edit()
            .putString("acct_email", r.optString("email", email.trim()))
            .putString("acct_uid", r.getString("localId"))
            .putString("acct_refresh", r.getString("refreshToken"))
            .putString("acct_id", r.getString("idToken"))
            .putLong("acct_idExp", System.currentTimeMillis() + (Long.parseLong(r.optString("expiresIn", "3600")) - 120) * 1000L)
            .apply();
    }

    static void signOut(SharedPreferences p) {
        p.edit().remove("acct_email").remove("acct_uid").remove("acct_refresh").remove("acct_id").remove("acct_idExp")
            .remove("acct_settings").remove("acct_at").apply();
    }

    private static String idToken(SharedPreferences p) throws Exception {
        if (System.currentTimeMillis() < p.getLong("acct_idExp", 0)) return p.getString("acct_id", "");
        String form = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(p.getString("acct_refresh", ""), "UTF-8");
        JSONObject r = new JSONObject(Net.request("https://securetoken.googleapis.com/v1/token?key=" + KEY,
            "POST", "application/x-www-form-urlencoded", form, null));
        p.edit()
            .putString("acct_id", r.getString("id_token"))
            .putString("acct_refresh", r.getString("refresh_token"))
            .putLong("acct_idExp", System.currentTimeMillis() + (Long.parseLong(r.optString("expires_in", "3600")) - 120) * 1000L)
            .apply();
        return r.getString("id_token");
    }

    /** Downloads your cloudgaze settings and keeps a copy for when you're offline. */
    static JSONObject fetch(SharedPreferences p) throws Exception {
        String uid = p.getString("acct_uid", "");
        String url = "https://firestore.googleapis.com/v1/projects/" + PROJECT + "/databases/(default)/documents/users/" + uid + "?mask.fieldPaths=settings";
        JSONObject doc = new JSONObject(Net.request(url, "GET", null, null, idToken(p)));
        String json = doc.getJSONObject("fields").getJSONObject("settings").getString("stringValue");
        JSONObject s = new JSONObject(json);
        p.edit().putString("acct_settings", json).putLong("acct_at", System.currentTimeMillis()).apply();
        return s;
    }

    /** The last copy of your settings, or null when signed out. */
    static JSONObject cached(SharedPreferences p) {
        if (!signedIn(p)) return null;
        try { String s = p.getString("acct_settings", ""); return s.isEmpty() ? null : new JSONObject(s); }
        catch (Exception e) { return null; }
    }
}
