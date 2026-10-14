package app.byakugan;

import android.app.Activity;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
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

/** Pantalla principal: activación con código + términos, y estado/historial una vez activada. */
public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Api api;
    private ScrollView scroll;
    private LinearLayout col;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        api = new Api(this);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        col.setPadding(p, p * 2, p, p);
        scroll.addView(col);
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

    private void head(String subtitle) {
        col.removeAllViews();
        col.addView(Ui.text(this, "Byakugan", 28, Ui.AC, true));
        col.addView(Ui.text(this, subtitle, 14, Ui.MUT, false));
    }

    private View gap() { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(this, 14))); return v; }

    private void showEnroll(String error) {
        head("Asistente de estudio. Activa tu acceso con el código que te dieron.");
        col.addView(gap());
        EditText code = Ui.input(this, "BYA-XXXX-XXXX");
        code.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        EditText name = Ui.input(this, "Tu nombre o alias (opcional)");
        col.addView(code);
        col.addView(gap());
        col.addView(name);
        col.addView(gap());
        TextView t = Ui.text(this, terms(), 12, Ui.MUT, false);
        t.setBackground(Ui.box(this, Ui.CARD, Ui.LINE, 10));
        int pp = Ui.dp(this, 12);
        t.setPadding(pp, pp, pp, pp);
        col.addView(t);
        CheckBox ok = new CheckBox(this);
        ok.setText("Leí y acepto los términos");
        ok.setTextColor(Ui.TX);
        col.addView(ok);
        TextView err = Ui.text(this, error, 13, Ui.BAD, false);
        col.addView(err);
        col.addView(gap());
        col.addView(Ui.button(this, "Activar", true, v -> {
            if (!ok.isChecked()) { err.setText("Debes aceptar los términos."); return; }
            err.setText("Activando…");
            io.execute(() -> {
                try {
                    api.enroll(code.getText().toString().trim(), name.getText().toString().trim());
                    runOnUiThread(this::showStatus);
                } catch (Api.ApiException e) {
                    runOnUiThread(() -> err.setText(e.getMessage()));
                } catch (Exception e) {
                    runOnUiThread(() -> err.setText("Sin conexión con el servidor."));
                }
            });
        }));
    }

    private void showStatus() {
        head("Activa. Selecciona texto en cualquier app y toca «Consultar».");
        TextView info = Ui.text(this, "Cargando…", 15, Ui.TX, false);
        info.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        col.addView(info);
        io.execute(() -> {
            try {
                JSONObject m = api.me();
                runOnUiThread(() -> info.setText(m.optString("alias") + " · plan " + m.optString("plan") + "\nHoy: " + m.optInt("used") + " de " + m.optInt("limit") + " consultas"));
            } catch (Api.ApiException e) {
                if (e.status == 426) runOnUiThread(this::showNewTerms);
                else runOnUiThread(this::onResume); // token revocado → api.forget() ya limpió y vuelve a la activación
            } catch (Exception e) {
                runOnUiThread(() -> info.setText("Sin conexión: no se pudo leer tu cuota."));
            }
        });

        col.addView(Ui.text(this, "Historial (solo en este teléfono)", 13, Ui.MUT, false));
        JSONArray h = api.history();
        if (h.length() == 0) col.addView(Ui.text(this, "Aún no hay consultas.", 14, Ui.MUT, false));
        for (int i = 0; i < h.length(); i++) {
            JSONObject e = h.optJSONObject(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.box(this, Ui.CARD, Ui.LINE, 10));
            int pp = Ui.dp(this, 12);
            card.setPadding(pp, pp, pp, pp);
            String q = e.optString("q");
            card.addView(Ui.text(this, q.length() > 120 ? q.substring(0, 120) + "…" : q, 13, Ui.MUT, false));
            card.addView(Ui.text(this, e.optString("a"), 14, Ui.TX, false));
            card.addView(Ui.text(this, DateUtils.getRelativeTimeSpanString(e.optLong("t")).toString(), 11, Ui.MUT, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 8);
            col.addView(card, lp);
        }
        col.addView(gap());
        col.addView(Ui.button(this, "Ver términos", false, v -> showTerms()));
        col.addView(Ui.button(this, "Cerrar sesión en este teléfono", false, v -> { api.forget(); showEnroll(""); }));
    }

    private void showTerms() {
        head("Términos y condiciones");
        col.addView(gap());
        col.addView(Ui.text(this, terms(), 13, Ui.TX, false));
        col.addView(gap());
        col.addView(Ui.button(this, "Volver", true, v -> showStatus()));
    }

    private void showNewTerms() {
        head("Términos actualizados");
        col.addView(gap());
        col.addView(Ui.text(this, terms(), 13, Ui.TX, false));
        TextView err = Ui.text(this, "", 13, Ui.BAD, false);
        col.addView(err);
        col.addView(Ui.button(this, "Acepto", true, v -> io.execute(() -> {
            try { api.acceptTerms(); runOnUiThread(this::showStatus); }
            catch (Exception e) { runOnUiThread(() -> err.setText("No se pudo aceptar: " + e.getMessage())); }
        })));
    }

    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }
}
