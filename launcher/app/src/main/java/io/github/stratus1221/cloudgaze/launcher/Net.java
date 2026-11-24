package io.github.stratus1221.cloudgaze.launcher;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Tiny HTTP helper. Throws Net.Fail with the server's reply when the request isn't OK. */
final class Net {
    static final class Fail extends Exception {
        final int code; final String body;
        Fail(int code, String body) { super("HTTP " + code); this.code = code; this.body = body; }
    }

    private Net() {}

    static String request(String url, String method, String contentType, String body, String bearer) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(12000);
        h.setReadTimeout(15000);
        h.setRequestMethod(method);
        if (bearer != null) h.setRequestProperty("Authorization", "Bearer " + bearer);
        if (body != null) {
            h.setDoOutput(true);
            h.setRequestProperty("Content-Type", contentType);
            try (OutputStream o = h.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        try {
            int code = h.getResponseCode();
            InputStream in = code < 400 ? h.getInputStream() : h.getErrorStream();
            String text = read(in);
            if (code >= 400) throw new Fail(code, text);
            return text;
        } finally {
            h.disconnect();
        }
    }

    static String get(String url) throws Exception { return request(url, "GET", null, null, null); }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }
}
