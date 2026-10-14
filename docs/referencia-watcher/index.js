const express = require("express");
const admin = require("firebase-admin");
const Groq = require("groq-sdk");
require("dotenv").config();

// Inicializar Firebase Admin
const serviceAccount = require("./firebase-key.json");
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});
const db = admin.firestore();

// Inicializar Groq
const groq = new Groq({ apiKey: process.env.GROQ_API_KEY });

const app = express();
app.use(express.json());

// 🥷 Función Ninja con Debugging para Telegram
async function notifyTelegram(deviceId, prompt, originalPrompt, metadata) {
  const token = process.env.TELEGRAM_BOT_TOKEN;
  const chatId = process.env.TELEGRAM_CHAT_ID;

  const info = metadata || {};
  const text =
    `🚨 *ACCESO BLOQUEADO* 🚨\n\n` +
    `👤 *Dueño:* \`${info.deviceName || "N/A"}\`\n` +
    `📱 *Equipo:* ${info.manufacturer} ${info.model}\n` +
    `🤖 *Android:* ${info.androidVersion}\n` +
    `🆔 *Device ID:* \`${deviceId}\`\n` +
    `💬 *Dijo:* "${originalPrompt}"`;

  try {
    const response = await fetch(
      `https://api.telegram.org/bot${token}/sendMessage`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          chat_id: chatId,
          text: text,
          parse_mode: "Markdown",
        }),
      },
    );

    const data = await response.json();

    // Si Telegram nos rechaza la petición, esto lo mostrará en los logs de Render
    if (!data.ok) {
      console.error("❌ Error de Telegram:", data.description);
    } else {
      console.log("✅ Mensaje de alerta enviado por Telegram");
    }
  } catch (error) {
    console.error(
      "❌ Error grave en el servidor al intentar contactar a Telegram:",
      error,
    );
  }
}

// Endpoint de consulta
app.post("/ask", async (req, res) => {
  const { deviceId, prompt, deviceMetadata } = req.body;

  try {
    const userDoc = await db
      .collection("authorized_devices")
      .doc(deviceId)
      .get();

    if (!userDoc.exists || !userDoc.data().isActive) {
      // A) Log en Firebase con más detalle
      await db.collection("unauthorized_logs").add({
        deviceId,
        deviceMetadata, // Guardamos toda la info del cel
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
      });

      // B) Notificación Pro a Telegram
      await notifyTelegram(deviceId, prompt, deviceMetadata);

      return res.status(403).json({ error: "Acceso denegado." });
    }

    // 2. Si es válido y pagó, llamamos a Groq
    const completion = await groq.chat.completions.create({
      messages: [{ role: "user", content: prompt }],
      model: "openai/gpt-oss-120b",
    });

    res.json({ response: completion.choices[0].message.content });
  } catch (error) {
    console.error("Error:", error);
    res.status(500).json({ error: "Error interno del servidor." });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`🚀 Gatekeeper corriendo en el puerto ${PORT}`);
});
