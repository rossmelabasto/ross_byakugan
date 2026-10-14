package app.byakugan;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** GHOST UI: Muestra la respuesta como texto flotante semi-transparente y se oculta al toque. */
public class AskActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Api api;
    private String prompt;
    private TextView ghostText;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        api = new Api(this);
        CharSequence sel = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        prompt = sel == null ? "" : sel.toString().trim();

        // Si no hay texto o no está logueado, muere en silencio para no levantar sospechas
        if (prompt.isEmpty() || !api.enrolled()) {
            finish();
            return;
        }

        // Configuración de Ventana Invisible (Sin fondo, sin dim)
        Window w = getWindow();
        w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); 
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        setFinishOnTouchOutside(true);

        // Contenedor principal que captura los clicks para cerrarse
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                FrameLayout.LayoutParams.WRAP_CONTENT));
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 32));
        root.setOnClickListener(v -> finish()); // Click anywhere = desaparece

        // El texto fantasma (Baja opacidad)
        ghostText = new TextView(this);
        ghostText.setTextColor(Color.parseColor("#77FFFFFF")); // Blanco semi-transparente
        ghostText.setTextSize(13);
        ghostText.setGravity(Gravity.CENTER);
        ghostText.setText("...");

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, 
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        
        root.addView(ghostText, params);
        setContentView(root);

        run();
    }

    private void run() {
        io.execute(() -> {
            try {
                String r = api.ask(prompt);
                String[] s = Ui.splitAnswer(r);
                runOnUiThread(() -> {
                    ghostText.setText(s[0]); // Muestra solo la respuesta cruda
                    ghostText.performHapticFeedback(HapticFeedbackConstants.CONFIRM); // Pequeña vibración
                });
            } catch (Exception e) {
                // Silencio total en caso de error, o un punto para debuggear
                runOnUiThread(() -> ghostText.setText(".")); 
            }
        });
    }

    @Override protected void onDestroy() { 
        super.onDestroy(); 
        io.shutdownNow(); 
    }

    // --- BLOQUE STEALTH: AUTO-LOCK ---
    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        finishAffinity(); 
    }
}