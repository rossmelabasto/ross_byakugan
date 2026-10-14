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

/** GHOST UI: Muestra la respuesta como texto flotante y solo se oculta al tocar el texto. */
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

        // Si no hay texto o no está logueado, muere en silencio
        if (prompt.isEmpty() || !api.enrolled()) {
            finish();
            return;
        }

        // Configuración de Ventana Invisible y No-Bloqueante
        Window w = getWindow();
        w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); 
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        // Magia negra: No roba el foco de la app de fondo y deja pasar los toques afuera
        w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        
        // No se cierra tocando afuera, SOLO tocando el texto
        setFinishOnTouchOutside(false);

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                FrameLayout.LayoutParams.WRAP_CONTENT));
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 32));

        ghostText = new TextView(this);
        // Blanco al 90% de opacidad
        ghostText.setTextColor(Color.parseColor("#E6FFFFFF")); 
        // Sombra oscura difuminada para que sea súper legible en fondos claros
        ghostText.setShadowLayer(6, 0, 2, Color.parseColor("#CC000000")); 
        ghostText.setTextSize(14);
        ghostText.setGravity(Gravity.CENTER);
        ghostText.setText("...");
        // Pequeño padding para aumentar la zona táctil (hitbox) del texto
        ghostText.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        
        // Solo se cierra si haces click en EL TEXTO
        ghostText.setOnClickListener(v -> finish());

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
                    ghostText.setText(s[0]); 
                    ghostText.performHapticFeedback(HapticFeedbackConstants.CONFIRM); 
                });
            } catch (Exception e) {
                runOnUiThread(() -> ghostText.setText(".")); 
            }
        });
    }

    @Override protected void onDestroy() { 
        super.onDestroy(); 
        io.shutdownNow(); 
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        finishAffinity(); 
    }
}