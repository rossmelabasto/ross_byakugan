package app.byakugan;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Pantalla principal: activación con código + términos, y estado/cuota/historial una vez activada. */
public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Api api;
    private LinearLayout col;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        api = new Api(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        col.setPadding(p, p, p, p);
        scroll.addView(col);
        scroll.setOnApplyWindowInsetsListener((v, ins) -> {
            col.setPadding(p, ins.getSystemWindowInsetTop() + p, p, ins.getSystemWindowInsetBottom() + p);
            return ins;
        });
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        if (api.enrolled()) showStatus(); else showEnroll("");
    }

    private String terms() {
        try (InputStream in = getAssets().open("terms.txt")) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
            return o.toString(StandardCharsets.UTF_8.name()).replace("**", "").replaceAll("(?m)^# ", "").trim();
        } catch (Exception e) { return "No se pudieron cargar los términos."; }
    }

    private static String ago(long t) {
        long s = (System.currentTimeMillis() - t) / 1000;
        if (s < 90) return "ahora";
        if (s < 3600) return "hace " + Math.round(s / 60.0) + " min";
        if (s < 86400) return "hace " + Math.round(s / 3600.0) + " h";
        return "hace " + Math.round(s / 86400.0) + " d";
    }

    private View gap(float dp) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(this, dp))); return v; }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.box(this, Ui.CARD, Ui.LINE, 16));
        int pp = Ui.dp(this, 16);
        c.setPadding(pp, pp, pp, pp);
        return c;
    }

    private void add(View v, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, topDp);
        col.addView(v, lp);
    }

    private void addBtn(String label, boolean primary, View.OnClickListener l, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50));
        lp.topMargin = Ui.dp(this, topDp);
        col.addView(Ui.button(this, label, primary, l), lp);
    }

    private ImageView logo(int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.ic_eye_dark);
        iv.setBackground(Ui.gradient(this, sizeDp * 0.3f));
        int pad = Ui.dp(this, sizeDp * 0.08f);
        iv.setPadding(pad, pad, pad, pad);
        iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp)));
        return iv;
    }

    private void head(String subtitle, boolean big) {
        col.removeAllViews();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(logo(big ? 56 : 40));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        t.addView(Ui.text(this, "Byakugan", big ? 26 : 22, Ui.TX, true));
        t.addView(Ui.text(this, subtitle, 13, Ui.MUT, false));
        row.addView(t);
        col.addView(row);
    }

    private void showEnroll(String error) {
        head("Asistente de estudio", true);
        add(Ui.text(this, "Activa tu acceso con el código de invitación que te dieron.", 15, Ui.SUB, false), 22);
        EditText code = Ui.input(this, "BYA-XXXX-XXXX");
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setTextSize(18);
        EditText name = Ui.input(this, "Tu nombre o alias (opcional)");
        add(code, 16);
        add(name, 10);

        TextView toggle = Ui.text(this, "Términos y condiciones  ▾", 14, Ui.BLUE, true);
        TextView t = Ui.text(this, terms(), 12.5f, Ui.SUB, false);
        t.setBackground(Ui.box(this, Ui.CARD, Ui.LINE, 12));
        int pp = Ui.dp(this, 14);
        t.setPadding(pp, pp, pp, pp);
        t.setVisibility(View.GONE);
        toggle.setOnClickListener(v -> {
            boolean open = t.getVisibility() == View.VISIBLE;
            t.setVisibility(open ? View.GONE : View.VISIBLE);
            toggle.setText(open ? "Términos y condiciones  ▾" : "Términos y condiciones  ▴");
        });
        add(toggle, 18);
        add(t, 8);
        CheckBox ok = new CheckBox(this);
        ok.setText("Leí y acepto los términos");
        ok.setTextColor(Ui.TX);
        ok.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.AC));
        add(ok, 8);
        TextView err = Ui.text(this, error, 13, Ui.BAD, false);
        add(err, 6);
        addBtn("Activar", true, v -> {
            if (!ok.isChecked()) { err.setText("Debes aceptar los términos."); return; }
            if (code.getText().toString().trim().isEmpty()) { err.setText("Escribe tu código de invitación."); return; }
            err.setTextColor(Ui.SUB);
            err.setText("Activando…");
            io.execute(() -> {
                try {
                    api.enroll(code.getText().toString().trim(), name.getText().toString().trim());
                    runOnUiThread(this::showStatus);
                } catch (Api.ApiException e) {
                    runOnUiThread(() -> { err.setTextColor(Ui.BAD); err.setText(e.getMessage()); });
                } catch (Exception e) {
                    runOnUiThread(() -> { err.setTextColor(Ui.BAD); err.setText("Sin conexión con el servidor."); });
                }
            });
        }, 10);
    }

    private void showStatus() {
        head("Activo", false);

        LinearLayout st = card();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.avatar(this, "…", Ui.MUT, 46));
        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        who.setPadding(Ui.dp(this, 12), 0, 0, 0);
        TextView alias = Ui.text(this, "Cargando…", 17, Ui.TX, true);
        TextView sub = Ui.text(this, " ", 12.5f, Ui.MUT, false);
        who.addView(alias);
        who.addView(sub);
        top.addView(who, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        FrameSlot pillSlot = new FrameSlot(this);
        top.addView(pillSlot);
        st.addView(top);
        LinearLayout q = new LinearLayout(this);
        TextView qa = Ui.text(this, "Hoy", 12.5f, Ui.SUB, false);
        TextView qb = Ui.text(this, "—", 12.5f, Ui.SUB, true);
        q.addView(qa, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        q.addView(qb);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.topMargin = Ui.dp(this, 16);
        st.addView(q, qlp);
        Ui.Bar bar = new Ui.Bar(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 6);
        st.addView(bar, blp);
        add(st, 20);

        io.execute(() -> {
            try {
                JSONObject m = api.me();
                runOnUiThread(() -> {
                    String name = m.optString("alias"), plan = m.optString("plan");
                    boolean unl = m.isNull("limit");
                    int used = m.optInt("used"), limit = m.optInt("limit");
                    ((ViewGroup) top.getChildAt(0).getParent()).removeViewAt(0);
                    top.addView(Ui.avatar(this, name, Ui.planColor(plan), 46), 0);
                    alias.setText(name);
                    sub.setText("Plan " + plan);
                    pillSlot.set(Ui.pill(this, plan, Ui.planColor(plan)));
                    qb.setText(unl ? used + " · ilimitado" : used + " / " + limit);
                    bar.set(unl ? 1f : limit == 0 ? 1f : used / (float) limit, unl);
                });
            } catch (Api.ApiException e) {
                if (e.status == 426) runOnUiThread(this::showNewTerms);
                else if (e.status == 401) runOnUiThread(this::onResume); 
                else runOnUiThread(() -> sub.setText(e.getMessage()));
            } catch (Exception e) {
                runOnUiThread(() -> { alias.setText("Sin conexión"); sub.setText("No se pudo leer tu cuota."); });
            }
        });

        LinearLayout how = card();
        how.addView(Ui.text(this, "Cómo usarlo", 13, Ui.MUT, true));
        String[] steps = {"1  Selecciona texto en cualquier app (Chrome, PDF, chat…)", "2  En el menú que aparece, toca «Question»", "3  La respuesta aparecerá casi invisible abajo."};
        for (String s : steps) {
            TextView t = Ui.text(this, s, 14.5f, Ui.TX, false);
            t.setPadding(0, Ui.dp(this, 8), 0, 0);
            how.addView(t);
        }
        add(how, 14);

        add(Ui.text(this, "Historial · solo en este teléfono", 13, Ui.MUT, true), 22);
        JSONArray h = api.history();
        if (h.length() == 0) add(Ui.text(this, "Aún no hay consultas.", 14, Ui.MUT, false), 8);
        for (int i = 0; i < h.length(); i++) {
            JSONObject e = h.optJSONObject(i);
            String[] s = Ui.splitAnswer(e.optString("a"));
            LinearLayout c = card();
            String qq = e.optString("q");
            TextView tq = Ui.text(this, qq, 12.5f, Ui.MUT, false);
            tq.setMaxLines(2);
            tq.setEllipsize(android.text.TextUtils.TruncateAt.END);
            c.addView(tq);
            TextView ta = Ui.text(this, s[0], 17, Ui.AC, true);
            ta.setPadding(0, Ui.dp(this, 6), 0, 0);
            c.addView(ta);
            if (!s[1].isEmpty()) {
                TextView te = Ui.text(this, s[1], 14, Ui.SUB, false);
                te.setMaxLines(2);
                te.setEllipsize(android.text.TextUtils.TruncateAt.END);
                te.setPadding(0, Ui.dp(this, 4), 0, 0);
                c.addView(te);
                c.setOnClickListener(v -> { boolean open = te.getMaxLines() > 2; te.setMaxLines(open ? 2 : 100); tq.setMaxLines(open ? 2 : 100); });
            }
            TextView tt = Ui.text(this, ago(e.optLong("t")), 11, Ui.MUT, false);
            tt.setPadding(0, Ui.dp(this, 8), 0, 0);
            c.addView(tt);
            add(c, 10);
        }

        addBtn("Ver términos", false, v -> showTerms(), 24);
        addBtn("Cerrar sesión en este teléfono", false, v -> { api.forget(); showEnroll(""); }, 10);
    }

    private static final class FrameSlot extends LinearLayout {
        FrameSlot(android.content.Context c) { super(c); }
        void set(View v) { removeAllViews(); addView(v); }
    }

    private void showTerms() {
        head("Términos y condiciones", false);
        TextView t = Ui.text(this, terms(), 14, Ui.TX, false);
        t.setLineSpacing(0, 1.15f);
        add(t, 20);
        addBtn("Volver", true, v -> showStatus(), 20);
    }

    private void showNewTerms() {
        head("Términos actualizados", false);
        add(Ui.text(this, terms(), 14, Ui.TX, false), 20);
        TextView err = Ui.text(this, "", 13, Ui.BAD, false);
        add(err, 8);
        addBtn("Acepto", true, v -> io.execute(() -> {
            try { api.acceptTerms(); runOnUiThread(this::showStatus); }
            catch (Exception e) { runOnUiThread(() -> err.setText("No se pudo aceptar: " + e.getMessage())); }
        }), 10);
    }

    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }

    // --- BLOQUE STEALTH: AUTO-LOCK ---
    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        finishAffinity(); 
    }
}