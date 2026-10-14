package app.byakugan;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Acción "Consultar" del menú de selección de texto: envía el texto y muestra la respuesta. */
public class AskActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Api api;
    private String prompt;
    private String lastAnswer = "";
    private LinearLayout body, actions;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        api = new Api(this);
        CharSequence sel = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        prompt = sel == null ? "" : sel.toString().trim();

        Window w = getWindow();
        w.setGravity(Gravity.BOTTOM);
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0.35f);
        setFinishOnTouchOutside(true);

        int p = Ui.dp(this, 20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, Ui.dp(this, 10), p, p);

        View handle = new View(this);
        handle.setBackground(Ui.box(this, Ui.LINE, 0, 99));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = Ui.dp(this, 14);
        root.addView(handle, hp);

        String q = prompt.length() > 140 ? prompt.substring(0, 140) + "…" : prompt;
        TextView question = Ui.text(this, q, 12, Ui.MUT, false);
        question.setMaxLines(3);
        root.addView(question);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        root.addView(actions);
        setContentView(root);
        // Ancho fijo al de la pantalla (menos márgenes): si no, un texto corto deja el panel estrecho.
        root.setMinimumWidth(getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 24));
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        if (prompt.isEmpty()) { showError("No hay texto seleccionado."); return; }
        if (!api.enrolled()) { showNotEnrolled(); return; }
        run();
    }

    private void actionButtons(boolean retry) {
        actions.removeAllViews();
        if (retry) actions.addView(Ui.button(this, "Reintentar", false, v -> run()), btnLp());
        else if (!lastAnswer.isEmpty()) actions.addView(Ui.button(this, "Copiar", false, v -> {
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("respuesta", lastAnswer));
            Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
        }), btnLp());
        actions.addView(Ui.button(this, "Cerrar", true, v -> finish()), btnLp());
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 46));
        lp.leftMargin = Ui.dp(this, 8);
        return lp;
    }

    private void run() {
        body.removeAllViews();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar pb = new ProgressBar(this);
        pb.getIndeterminateDrawable().setTint(Ui.AC);
        row.addView(pb, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
        TextView t = Ui.text(this, "  Consultando…", 15, Ui.SUB, false);
        row.addView(t);
        body.addView(row);
        actionButtons(false);
        actions.removeAllViews();
        actions.addView(Ui.button(this, "Cerrar", false, v -> finish()), btnLp());

        io.execute(() -> {
            try {
                String r = api.ask(prompt);
                runOnUiThread(() -> showAnswer(r));
            } catch (Api.ApiException e) {
                String m = e.status == 426 ? "Hay nuevos términos. Abre la app para aceptarlos."
                        : e.status == 429 ? "Llegaste al límite de consultas de hoy."
                        : e.status == 401 ? "Tu acceso ya no es válido. Abre la app para activarlo de nuevo."
                        : e.getMessage();
                runOnUiThread(() -> showError(m, e.status >= 500 || e.status == 502));
            } catch (Exception e) {
                runOnUiThread(() -> showError("Sin conexión con el servidor.", true));
            }
        });
    }

    private void showAnswer(String raw) {
        lastAnswer = raw;
        String[] s = Ui.splitAnswer(raw);
        body.removeAllViews();
        body.addView(Ui.text(this, "RESPUESTA", 11, Ui.MUT, true));
        TextView a = Ui.text(this, s[0], 20, Ui.AC, true);
        a.setTextIsSelectable(true);
        a.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 8));
        body.addView(a);
        if (!s[1].isEmpty()) {
            TextView e = Ui.text(this, s[1], 14.5f, Ui.TX, false);
            e.setLineSpacing(0, 1.12f);
            e.setTextIsSelectable(true);
            body.addView(e);
        }
        actionButtons(false);
        body.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
    }

    private void showError(String msg) { showError(msg, false); }

    private void showError(String msg, boolean retry) {
        lastAnswer = "";
        body.removeAllViews();
        body.addView(Ui.text(this, msg, 15, Ui.BAD, false));
        actionButtons(retry);
    }

    private void showNotEnrolled() {
        body.removeAllViews();
        body.addView(Ui.text(this, "Primero activa Byakugan con tu código de invitación.", 15, Ui.TX, false));
        actions.removeAllViews();
        actions.addView(Ui.button(this, "Abrir app", true, v -> { startActivity(new Intent(this, MainActivity.class)); finish(); }), btnLp());
    }

    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }
}
