package io.github.stratus1221.cloudgaze.launcher;

import android.app.Activity;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Draws notification cards, shared by the home widget and the controls panel. Tap opens, swipe sideways clears. */
final class NotifUi {
    private NotifUi() {}

    static View card(Activity a, NotifService.Item n, float z, Runnable after) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.box(a, 0xFF070707, 0xFF1E1E1E, 12));
        c.setPadding(Ui.dp(a, 12), Ui.dp(a, 9), Ui.dp(a, 12), Ui.dp(a, 10));
        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView app = Ui.text(a, n.app + (n.title.isEmpty() ? "" : "  ·  " + n.title), 11.5f * z, 0xFF888888, Ui.REGULAR);
        app.setSingleLine(true);
        app.setEllipsize(TextUtils.TruncateAt.END);
        TextView when = Ui.text(a, NotifService.ago(n.when), 11 * z, Ui.C4, Ui.REGULAR);
        top.addView(app, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(when);
        c.addView(top);
        if (!n.text.isEmpty()) {
            TextView m = Ui.text(a, n.text, 14 * z, 0xFFDDDDDD, Ui.LIGHT);
            m.setMaxLines(2);
            m.setEllipsize(TextUtils.TruncateAt.END);
            m.setPadding(0, Ui.dp(a, 4), 0, 0);
            m.setLineSpacing(Ui.dp(a, 1), 1f);
            c.addView(m);
        }
        c.setOnClickListener(v -> { NotifService.open(a, n); if (after != null) after.run(); });
        if (n.clearable) swipeAway(c, () -> NotifService.dismiss(n));
        return c;
    }

    /** Lets a card be flicked left or right to clear it. */
    private static void swipeAway(View v, Runnable gone) {
        final float[] start = new float[2];
        final boolean[] dragging = new boolean[1];
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = e.getRawX(); start[1] = e.getRawY(); dragging[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - start[0], dy = e.getRawY() - start[1];
                    if (!dragging[0] && Math.abs(dx) > Ui.dp(view.getContext(), 14) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        dragging[0] = true;
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        view.setPressed(false);
                    }
                    if (dragging[0]) {
                        view.setTranslationX(dx);
                        view.setAlpha(Math.max(0.2f, 1 - Math.abs(dx) / view.getWidth()));
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!dragging[0]) return false;
                    float dx = e.getRawX() - start[0];
                    if (Math.abs(dx) > view.getWidth() * 0.35f) {
                        view.animate().translationX(Math.signum(dx) * view.getWidth()).alpha(0).setDuration(150).withEndAction(gone::run).start();
                    } else {
                        view.animate().translationX(0).alpha(1).setDuration(150).start();
                    }
                    return true;
            }
            return false;
        });
    }

    /** Fills a list with cards, or with a note when there's nothing or access isn't allowed yet. */
    static void fill(Activity a, LinearLayout box, int max, float z, Runnable after) {
        box.removeAllViews();
        if (!NotifService.allowed(a)) {
            TextView t = Ui.text(a, "Allow notification access to see them here", 14 * z, Ui.C3, Ui.LIGHT);
            t.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
            TextView b = Ui.button(a, "Allow");
            b.setOnClickListener(v -> { try { a.startActivity(NotifService.accessSettings()); } catch (Exception e) {} });
            box.addView(t);
            box.addView(b, new LinearLayout.LayoutParams(-2, -2));
            return;
        }
        List<NotifService.Item> items = NotifService.items();
        if (items.isEmpty()) {
            TextView t = Ui.text(a, "Nothing new", 14 * z, Ui.C4, Ui.LIGHT);
            t.setPadding(0, Ui.dp(a, 8), 0, 0);
            box.addView(t);
            return;
        }
        int n = 0;
        for (NotifService.Item i : items) {
            if (n++ >= max) break;
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
            l.topMargin = Ui.dp(a, 8);
            box.addView(card(a, i, z, after), l);
        }
        if (items.size() > max) {
            TextView more = Ui.text(a, "+" + (items.size() - max) + " more", 12, Ui.C4, Ui.REGULAR);
            more.setPadding(Ui.dp(a, 4), Ui.dp(a, 8), 0, 0);
            box.addView(more);
        }
    }
}
