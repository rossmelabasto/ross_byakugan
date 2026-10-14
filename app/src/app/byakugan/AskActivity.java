package app.byakugan;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Acción "Consultar" del menú de selección de texto: envía el texto y muestra la respuesta. */
public class AskActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView answer;
    private String lastAnswer = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        CharSequence sel = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        String prompt = sel == null ? "" : sel.toString().trim();
        Api api = new Api(this);

        Window w = getWindow();
        w.setGravity(Gravity.BOTTOM);
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        int p = Ui.dp(this, 18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, p);

        String q = prompt.length() > 160 ? prompt.substring(0, 160) + "…" : prompt;
        TextView question = Ui.text(this, q, 12, Ui.MUT, false);
        question.setMaxLines(3);
        answer = Ui.text(this, "Consultando…", 16, Ui.TX, false);
        answer.setTextIsSelectable(true);
        answer.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(answer);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        row.addView(Ui.button(this, "Copiar", false, v -> {
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("respuesta", lastAnswer));
            Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
        }));
        row.addView(Ui.button(this, "Cerrar", true, v -> finish()));

        root.addView(question);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(row);
        setContentView(root);

        if (prompt.isEmpty()) { show("No hay texto seleccionado."); return; }
        if (!api.enrolled()) {
            show("Primero activa Byakugan con tu código de invitación.");
            row.addView(Ui.button(this, "Abrir app", true, v -> { startActivity(new Intent(this, MainActivity.class)); finish(); }), 0);
            return;
        }
        io.execute(() -> {
            try {
                String r = api.ask(prompt);
                runOnUiThread(() -> show(r));
            } catch (Api.ApiException e) {
                runOnUiThread(() -> show(e.status == 426 ? "Hay nuevos términos. Abre la app para aceptarlos." : e.getMessage()));
            } catch (Exception e) {
                runOnUiThread(() -> show("Sin conexión con el servidor."));
            }
        });
    }

    private void show(String s) { lastAnswer = s; answer.setText(s); }

    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }
}
