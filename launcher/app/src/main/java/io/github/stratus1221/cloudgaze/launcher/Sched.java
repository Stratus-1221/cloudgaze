package io.github.stratus1221.cloudgaze.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** cloudgaze's bell schedule logic, the same rules as the website: A/B days, days off, special schedules. */
final class Sched {
    static final class Block { final String name; final int s, e; Block(String n, int s, int e) { name = n; this.s = s; this.e = e; } }
    static final class Status { String main = "", next = "", day = ""; boolean msg; double frac = -1; }

    private final JSONObject st;
    private final boolean h24;

    Sched(JSONObject settings, boolean h24) { st = settings; this.h24 = h24; }

    static String ymd(LocalDate d) { return d.toString(); }
    static boolean weekend(LocalDate d) { return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY; }

    JSONObject offOn(LocalDate d) {
        JSONArray a = st.optJSONArray("noSchool");
        if (a == null) return null;
        String k = ymd(d);
        for (int i = 0; i < a.length(); i++) {
            JSONObject n = a.optJSONObject(i);
            if (n == null) continue;
            String s = n.optString("start", ""), e = n.optString("end", "");
            if (e.isEmpty()) e = s;
            if (!s.isEmpty() && k.compareTo(s) >= 0 && k.compareTo(e) <= 0) return n;
        }
        return null;
    }

    boolean schoolDay(LocalDate d) { return !weekend(d) && offOn(d) == null; }

    LocalDate nextSchoolDay(LocalDate d) {
        for (int i = 0; i < 120; i++) { d = d.plusDays(1); if (schoolDay(d)) return d; }
        return null;
    }

    boolean ab() { return st.optBoolean("ab", true); }

    String letter(LocalDate date) {
        if (!ab()) return "A";
        String val = st.optString("day", "A");
        if (!st.optBoolean("autoAB", false)) return val;
        LocalDate a;
        try { a = LocalDate.parse(st.optString("anchorDate", "")); } catch (Exception e) { return val; }
        for (int i = 0; i < 400 && !schoolDay(a); i++) a = a.plusDays(1);
        int n = 0;
        if (!date.isBefore(a)) { for (LocalDate d = a; d.isBefore(date); ) { d = d.plusDays(1); if (schoolDay(d)) n++; } }
        else { for (LocalDate d = date; d.isBefore(a); d = d.plusDays(1)) if (schoolDay(d)) n++; }
        return n % 2 == 1 ? (val.equals("A") ? "B" : "A") : val;
    }

    private JSONObject scheduleFor(LocalDate date) {
        JSONArray list = st.optJSONArray("schedules");
        if (list == null || list.length() == 0) return null;
        JSONObject ov = st.optJSONObject("override");
        if (ov != null && ymd(date).equals(ov.optString("date"))) {
            for (int i = 0; i < list.length(); i++) if (ov.optString("id").equals(list.optJSONObject(i).optString("id"))) return list.optJSONObject(i);
        }
        int dow = date.getDayOfWeek().getValue() % 7; // Sunday = 0, like the website
        for (int i = 1; i < list.length(); i++) {
            JSONArray days = list.optJSONObject(i).optJSONArray("days");
            if (days != null) for (int j = 0; j < days.length(); j++) if (days.optInt(j, -1) == dow) return list.optJSONObject(i);
        }
        return list.optJSONObject(0);
    }

    static int toSec(String hm) {
        String[] p = hm.split(":");
        return Integer.parseInt(p[0].trim()) * 3600 + Integer.parseInt(p[1].trim()) * 60;
    }

    List<Block> blocks(LocalDate date) {
        List<Block> out = new ArrayList<>();
        JSONObject sc = scheduleFor(date);
        if (sc == null) return out;
        String L = letter(date);
        boolean split = sc.optBoolean("split", false) && ab();
        JSONArray rows = split ? sc.optJSONArray(L.equals("A") ? "rowsA" : "rowsB") : sc.optJSONArray("rows");
        if (rows == null) return out;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i);
            if (r == null) continue;
            String s = r.optString("start", ""), e = r.optString("end", "");
            if (s.isEmpty() || e.isEmpty()) continue;
            String name;
            if (split) name = r.optString("name", "");
            else {
                name = ab() ? r.optString(L, "") : "";
                if (name.isEmpty()) name = r.optString("A", "");
            }
            try {
                int a = toSec(s), b = toSec(e);
                if (a < b) out.add(new Block(name.trim(), a, b));
            } catch (Exception ex) { /* skip a broken row */ }
        }
        Collections.sort(out, (x, y) -> x.s - y.s);
        return out;
    }

    static String left(int sec) {
        int h = sec / 3600, m = sec % 3600 / 60, s = sec % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    static String hm(int sec) {
        int mins = (sec + 59) / 60, h = mins / 60, m = mins % 60;
        return h > 0 ? String.format(Locale.ROOT, "%dh %02dm", h, m) : m + "m";
    }

    String clock(int sec) {
        int h = sec / 3600, m = sec % 3600 / 60;
        return h24 ? String.format(Locale.ROOT, "%02d:%02d", h, m) : String.format(Locale.ROOT, "%d:%02d", h % 12 == 0 ? 12 : h % 12, m);
    }

    Status status(LocalDateTime now) {
        Status out = new Status();
        LocalDate today = now.toLocalDate();
        String tag = ab() ? letter(today) + " Day  ·  " : "";
        JSONObject off = offOn(today);
        if (weekend(today) || off != null) {
            out.msg = true;
            out.main = off != null ? (off.optString("name", "").isEmpty() ? "No school today" : "No school  ·  " + off.optString("name")) : "Have a good weekend";
            return out;
        }
        List<Block> B = blocks(today);
        if (B.isEmpty()) return out;
        int t = now.getHour() * 3600 + now.getMinute() * 60 + now.getSecond();
        int first = B.get(0).s, last = B.get(B.size() - 1).e;
        if (t < first) {
            out.msg = true;
            int l = first - t;
            out.main = l <= 2 * 3600 ? tag + B.get(0).name + " in " + left(l) : tag + (B.get(0).name.isEmpty() ? "School" : B.get(0).name) + " at " + clock(first);
            return out;
        }
        if (t >= last) {
            out.msg = true;
            LocalDate tomorrow = today.plusDays(1);
            LocalDate nx = nextSchoolDay(today);
            JSONObject offT = offOn(tomorrow);
            if (weekend(tomorrow)) out.main = "Have a good weekend";
            else if (tomorrow.equals(nx)) out.main = "See you tomorrow" + (ab() ? "  ·  " + letter(nx) + " Day" : "");
            else out.main = offT != null && !offT.optString("name", "").isEmpty() ? "No school tomorrow  ·  " + offT.optString("name") : "No school tomorrow";
            return out;
        }
        int cur = -1, target = -1;
        for (int i = 0; i < B.size(); i++) {
            Block b = B.get(i);
            Block n = i + 1 < B.size() ? B.get(i + 1) : null;
            if (t >= b.s && t < b.e) {
                cur = i; target = i + 1;
                out.main = b.name + "  ·  " + left(b.e - t) + " left";
                out.next = n != null ? "Up next: " + n.name : "Up next: End of day";
                out.frac = (t - b.s) / (double) (b.e - b.s);
                break;
            }
            if (n != null && t >= b.e && t < n.s) {
                target = i + 1;
                out.main = "Passing  ·  " + left(n.s - t) + " to " + n.name;
                break;
            }
        }
        List<String> extras = new ArrayList<>();
        int lunch = -1;
        for (int i = 0; i < B.size(); i++) if (B.get(i).name.toLowerCase(Locale.ROOT).contains("lunch")) { lunch = i; break; }
        if (lunch >= 0 && t < B.get(lunch).s && target != lunch) extras.add("Lunch in " + hm(B.get(lunch).s - t));
        if (cur != B.size() - 1) extras.add((weekend(today.plusDays(1)) ? "Weekend" : "Out") + " in " + hm(last - t));
        out.day = String.join("   ·   ", extras);
        return out;
    }

    // ---------- homework ----------

    static final class Hw { String title, cls, label; int tone; } // tone: 0 normal, 1 soon, 2 late

    /** Unfinished homework from every Homework widget in your cloudgaze account, soonest first. */
    List<Hw> homework(LocalDateTime now, int max) {
        List<String> ids = new ArrayList<>();
        JSONArray lays = st.optJSONArray("layouts");
        if (lays != null) for (int i = 0; i < lays.length(); i++) {
            JSONArray items = lays.optJSONObject(i).optJSONArray("items");
            if (items != null) for (int j = 0; j < items.length(); j++) {
                JSONObject it = items.optJSONObject(j);
                if (it != null && "homework".equals(it.optString("type")) && !ids.contains(it.optString("id"))) ids.add(it.optString("id"));
            }
        }
        JSONObject data = st.optJSONObject("data");
        List<JSONObject> all = new ArrayList<>();
        if (data != null) for (String id : ids) {
            JSONObject d = data.optJSONObject(id);
            JSONArray list = d == null ? null : d.optJSONArray("list");
            if (list != null) for (int i = 0; i < list.length(); i++) {
                JSONObject a = list.optJSONObject(i);
                if (a != null && !a.optBoolean("done", false)) all.add(a);
            }
        }
        Collections.sort(all, (a, b) -> {
            String x = a.optString("due", ""), y = b.optString("due", "");
            if (x.isEmpty() != y.isEmpty()) return x.isEmpty() ? 1 : -1;
            return x.compareTo(y);
        });
        List<Hw> out = new ArrayList<>();
        for (JSONObject a : all) {
            if (out.size() >= max) break;
            Hw h = new Hw();
            h.title = a.optString("t", "");
            h.cls = a.optString("cls", "");
            String due = a.optString("due", "");
            h.label = "";
            if (!due.isEmpty()) {
                try { dueLabel(h, due, now); } catch (Exception e) { h.label = due; }
            }
            out.add(h);
        }
        return out;
    }

    private void dueLabel(Hw h, String due, LocalDateTime now) {
        boolean hasTime = due.length() > 10;
        LocalDateTime dt = hasTime ? LocalDateTime.parse(due.substring(0, 16)) : LocalDate.parse(due).atTime(23, 59, 59);
        long days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), dt.toLocalDate());
        String tm = hasTime ? " " + dt.format(DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "h:mm a", Locale.US)) : "";
        if (dt.isBefore(now)) { h.tone = 2; h.label = days == 0 ? "Due" + tm : days == -1 ? "Yesterday" : (-days) + "d late"; }
        else if (days == 0) { h.tone = 1; h.label = "Today" + tm; }
        else if (days == 1) { h.tone = 1; h.label = "Tomorrow"; }
        else if (days < 7) h.label = dt.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US);
        else h.label = dt.format(DateTimeFormatter.ofPattern("MMM d", Locale.US));
    }
}
