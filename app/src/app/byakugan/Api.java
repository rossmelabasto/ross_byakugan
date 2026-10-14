package app.byakugan;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Cliente HTTP mínimo del backend + almacenamiento local (token e historial). */
final class Api {
    static final String BASE = "https://byakugan.rossmel.com";

    static final class ApiException extends Exception {
        final int status;
        ApiException(int status, String msg) { super(msg); this.status = status; }
    }

    private final SharedPreferences prefs;

    Api(Context c) { prefs = c.getApplicationContext().getSharedPreferences("byakugan", Context.MODE_PRIVATE); }

    String token() { return prefs.getString("token", null); }
    boolean enrolled() { return token() != null; }
    void forget() { prefs.edit().clear().apply(); }

    JSONObject enroll(String code, String name) throws Exception {
        JSONObject meta = new JSONObject()
                .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL).put("androidVersion", Build.VERSION.RELEASE);
        JSONObject body = new JSONObject().put("code", code).put("accepted", true).put("deviceName", name).put("meta", meta);
        JSONObject r = call("POST", "/v1/enroll", body, false);
        prefs.edit().putString("token", r.getString("token")).apply();
        return r;
    }

    JSONObject me() throws Exception { return call("GET", "/v1/me", null, true); }

    String ask(String prompt) throws Exception {
        String answer = call("POST", "/v1/ask", new JSONObject().put("prompt", prompt), true).getString("response");
        addHistory(prompt, answer);
        return answer;
    }

    void acceptTerms() throws Exception { call("POST", "/v1/accept-terms", new JSONObject().put("accepted", true), true); }

    // Historial local (últimas 30 consultas, solo en este teléfono).
    void addHistory(String q, String a) {
        try {
            JSONArray old = new JSONArray(prefs.getString("history", "[]"));
            JSONArray n = new JSONArray();
            n.put(new JSONObject().put("q", q).put("a", a).put("t", System.currentTimeMillis()));
            for (int i = 0; i < old.length() && n.length() < 30; i++) n.put(old.get(i));
            prefs.edit().putString("history", n.toString()).apply();
        } catch (Exception ignored) { }
    }

    JSONArray history() {
        try { return new JSONArray(prefs.getString("history", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private JSONObject call(String method, String path, JSONObject body, boolean auth) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(10000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Content-Type", "application/json");
        if (auth) c.setRequestProperty("Authorization", "Bearer " + token());
        if (body != null) {
            c.setDoOutput(true);
            try (OutputStream o = c.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        }
        int status = c.getResponseCode();
        InputStream in = status < 400 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) sb.append(l).append('\n');
        }
        JSONObject json;
        try { json = new JSONObject(sb.toString()); } catch (Exception e) { json = new JSONObject(); }
        if (status >= 400) {
            if (status == 401 && auth) forget(); // token revocado: vuelve a pedir código
            throw new ApiException(status, json.optString("error", "Error " + status));
        }
        return json;
    }
}
