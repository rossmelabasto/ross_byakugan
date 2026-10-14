package app.byakugan;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public class ImcActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    // Valores mágicos por defecto (Fallback si no hay internet o falla el fetch)
    private String secretWeight = "88";
    private String secretHeight = "88";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imc);

        // Intentar traer los valores desde el backend silenciosamente
        fetchSecretValuesSilently();

        TextView tvTitle = findViewById(R.id.tvTitle);
        EditText etWeight = findViewById(R.id.etWeight);
        EditText etHeight = findViewById(R.id.etHeight);
        Button btnCalculate = findViewById(R.id.btnCalculate);
        TextView tvResult = findViewById(R.id.tvResult);

        // 👁️ EL BYAKUGAN: Long press en el título para autocompletar
        tvTitle.setOnLongClickListener(v -> {
            etWeight.setText(secretWeight);
            etHeight.setText(secretHeight);
            Toast.makeText(this, "Calibrado", Toast.LENGTH_SHORT).show();
            return true;
        });

        // Lógica del botón Calcular
        btnCalculate.setOnClickListener(v -> {
            String w = etWeight.getText().toString().trim();
            String h = etHeight.getText().toString().trim();

            if (w.isEmpty() || h.isEmpty()) {
                tvResult.setText("Por favor, ingresa los datos.");
                return;
            }

            // Validación Secreta
            if (w.equals(secretWeight) && h.equals(secretHeight)) {
                // ¡Acceso concedido! Saltamos a MainActivity
                Intent intent = new Intent(ImcActivity.this, MainActivity.class);
                startActivity(intent);
                finish(); // Cerramos la calculadora para que no quede en la multitarea
            } else {
                // Cálculo de IMC Real (Fachada)
                try {
                    float weight = Float.parseFloat(w);
                    float heightCm = Float.parseFloat(h);
                    float heightM = heightCm / 100;
                    float imc = weight / (heightM * heightM);
                    
                    String status = imc < 18.5 ? "Bajo peso" : (imc < 25 ? "Peso normal" : "Sobrepeso");
                    tvResult.setText(String.format("Tu IMC es: %.1f\n%s", imc, status));
                } catch (Exception e) {
                    tvResult.setText("Valores inválidos");
                }
            }
        });
    }

    private void fetchSecretValuesSilently() {
        io.execute(() -> {
            try {
                // Petición HTTP directa al endpoint stealth de tu servidor
                URL url = new URL("https://byakugan.rossmel.com/v1/stealth");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    secretWeight = json.optString("peso", "88");
                    secretHeight = json.optString("altura", "88");
                }
                conn.disconnect();
            } catch (Exception e) {
                // Silencio total. Si falla o no hay internet, se queda con los "88" por defecto.
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}