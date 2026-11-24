package io.github.stratus1221.cloudgaze.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers so every screen shares cloudgaze's look: black, thin type, quiet greys. */
final class Ui {
    static final int C1 = 0xFFE6E6E6, C2 = 0xFF9A9A9A, C3 = 0xFF777777, C4 = 0xFF555555, LINE = 0xFF222222, PANEL = 0xFF0B0B0B;
    static final Typeface THIN = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    static GradientDrawable box(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static TextView button(Context c, String s) {
        TextView b = text(c, s, 15, 0xFFBBBBBB, REGULAR);
        b.setBackground(box(c, 0, LINE, 8));
        b.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9));
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        return b;
    }

    interface Pick { void on(String value); }

    /** A row of choices like cloudgaze's segmented buttons. */
    static LinearLayout seg(Context c, String[][] choices, String current, Pick pick) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(box(c, 0, LINE, 8));
        row.setPadding(dp(c, 1), dp(c, 1), dp(c, 1), dp(c, 1));
        TextView[] all = new TextView[choices.length];
        for (int i = 0; i < choices.length; i++) {
            final String v = choices[i][0];
            TextView b = text(c, choices[i][1], 14, 0xFF777777, REGULAR);
            b.setPadding(dp(c, 13), dp(c, 8), dp(c, 13), dp(c, 8));
            b.setGravity(Gravity.CENTER);
            all[i] = b;
            b.setOnClickListener(x -> {
                for (TextView o : all) { o.setBackground(null); o.setTextColor(0xFF777777); }
                b.setBackground(box(c, 0xFF1A1A1A, 0, 7));
                b.setTextColor(0xFFDDDDDD);
                pick.on(v);
            });
            if (v.equals(current)) { b.setBackground(box(c, 0xFF1A1A1A, 0, 7)); b.setTextColor(0xFFDDDDDD); }
            row.addView(b);
        }
        return row;
    }
}
