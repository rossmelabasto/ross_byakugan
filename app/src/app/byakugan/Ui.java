package app.byakugan;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/** Pequeños constructores de vistas con la paleta Catppuccin Mocha. */
final class Ui {
    static final int BG = Color.parseColor("#11111b"), CARD = Color.parseColor("#1e1e2e"), LINE = Color.parseColor("#313244"),
            TX = Color.parseColor("#cdd6f4"), MUT = Color.parseColor("#9399b2"), AC = Color.parseColor("#a6e3a1"), BAD = Color.parseColor("#f38ba8");

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static GradientDrawable box(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    static Button button(Context c, String label, boolean primary, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(primary ? BG : TX);
        b.setBackground(box(c, primary ? AC : CARD, primary ? 0 : LINE, 10));
        b.setOnClickListener(l);
        return b;
    }

    static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(MUT);
        e.setTextColor(TX);
        e.setSingleLine(true);
        e.setBackground(box(c, CARD, LINE, 10));
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        return e;
    }
}
