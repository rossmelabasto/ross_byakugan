package app.byakugan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Constructores de vistas con la paleta Catppuccin Mocha. */
final class Ui {
    static final int BG = Color.parseColor("#11111b"), CARD = Color.parseColor("#1e1e2e"), LINE = Color.parseColor("#313244"),
            TX = Color.parseColor("#cdd6f4"), SUB = Color.parseColor("#a6adc8"), MUT = Color.parseColor("#7f849c"),
            AC = Color.parseColor("#a6e3a1"), AC2 = Color.parseColor("#94e2d5"), VI = Color.parseColor("#cba6f7"),
            BLUE = Color.parseColor("#89b4fa"), BAD = Color.parseColor("#f38ba8");

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static int planColor(String plan) { return "unlimited".equals(plan) ? VI : "pro".equals(plan) ? BLUE : SUB; }

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

    /** Fondo degradado verde → turquesa (logo y botón principal). */
    static GradientDrawable gradient(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{AC, AC2});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static Button button(Context c, String label, boolean primary, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? BG : TX);
        b.setBackground(primary ? gradient(c, 12) : box(c, CARD, LINE, 12));
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        return b;
    }

    static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(MUT);
        e.setTextColor(TX);
        e.setSingleLine(true);
        e.setBackground(box(c, CARD, LINE, 12));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        return e;
    }

    /** Etiqueta redondeada tipo "pill". */
    static TextView pill(Context c, String s, int color) {
        TextView t = text(c, s, 11, color, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 9), dp(c, 2), dp(c, 9), dp(c, 2));
        GradientDrawable g = new GradientDrawable();
        g.setColor((color & 0x00ffffff) | 0x22000000);
        g.setStroke(dp(c, 1), (color & 0x00ffffff) | 0x66000000);
        g.setCornerRadius(dp(c, 99));
        t.setBackground(g);
        return t;
    }

    /** Círculo con iniciales. */
    static TextView avatar(Context c, String name, int color, int sizeDp) {
        String ini = name == null || name.trim().isEmpty() ? "?" : name.trim().substring(0, Math.min(2, name.trim().length())).toUpperCase();
        TextView t = text(c, ini, sizeDp / 2.6f, BG, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, sizeDp * 0.3f));
        t.setBackground(g);
        t.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
    }

    /** Barra de progreso redondeada; frac 0..1, o `infinite` para el degradado violeta del plan ilimitado. */
    static final class Bar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float frac;
        private boolean infinite;

        Bar(Context c) { super(c); }

        void set(float frac, boolean infinite) { this.frac = Math.max(0f, Math.min(1f, frac)); this.infinite = infinite; invalidate(); }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(View.getDefaultSize(0, w), dp(getContext(), 8)); }

        @Override protected void onDraw(Canvas cv) {
            float rad = getHeight() / 2f;
            p.setShader(null);
            p.setColor(LINE);
            r.set(0, 0, getWidth(), getHeight());
            cv.drawRoundRect(r, rad, rad, p);
            float w = infinite ? getWidth() : getWidth() * frac;
            if (w <= 0) return;
            int a = infinite ? VI : frac > 0.9f ? Color.parseColor("#f9e2af") : AC;
            int b = infinite ? BLUE : frac > 0.9f ? BAD : AC2;
            p.setShader(new LinearGradient(0, 0, getWidth(), 0, a, b, Shader.TileMode.CLAMP));
            r.set(0, 0, Math.max(w, getHeight()), getHeight());
            cv.drawRoundRect(r, rad, rad, p);
        }
    }

    /** Separa "Respuesta: X / Explicación: Y" del modelo; devuelve {respuesta, explicación}. */
    private static final Pattern ANS = Pattern.compile("(?is)^\\s*Respuesta:\\s*(.*?)(?:\\n+\\s*Explicaci[oó]n:\\s*(.*))?$");

    static String[] splitAnswer(String t) {
        String s = t == null ? "" : t.replace("**", "").trim();
        Matcher m = ANS.matcher(s);
        if (m.matches()) return new String[]{m.group(1).trim(), m.group(2) == null ? "" : m.group(2).trim()};
        return new String[]{s, ""};
    }
}
