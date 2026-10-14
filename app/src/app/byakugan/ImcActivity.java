package app.byakugan;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

public class ImcActivity extends AppCompatActivity {

    // Valores mágicos por defecto (Fallback si no hay internet)
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
            Toast.makeText(this, "Calibrado", Toast.LENGTH_SHORT).show(); // Opcional para dar feedback
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
        new Thread(() -> {
            try {
                // Aquí usas tu Api.java para traer la config.
                // Ajusta la URL a tu endpoint (luego armaremos el backend para esto)
                String jsonResponse = Api.get("https://tu-backend.com/api/config/trigger");
                
                if (jsonResponse != null && !jsonResponse.isEmpty()) {
                    JSONObject json = new JSONObject(jsonResponse);
                    secretWeight = json.getString("peso");
                    secretHeight = json.getString("altura");
                }
            } catch (Exception e) {
                // Silencio total. Si falla (no WiFi), se queda con los "88" por defecto.
                e.printStackTrace();
            }
        }).start();
    }
}