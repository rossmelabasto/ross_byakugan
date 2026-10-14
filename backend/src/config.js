require("dotenv").config();

const PLANS = {
  free: { dailyLimit: 20 },
  pro: { dailyLimit: 100 },
  unlimited: { dailyLimit: null }, // null = sin tope diario (se sigue contando el uso)
};

module.exports = {
  PLANS,
  port: Number(process.env.PORT || 8100),
  host: process.env.HOST || "127.0.0.1",
  groqKey: process.env.GROQ_API_KEY,
  reasoningEffort: process.env.GROQ_REASONING || "high", // low | medium | high (más razonamiento = más precisión y más latencia)
  groqModel: process.env.GROQ_MODEL || "openai/gpt-oss-120b",
  firebaseKeyPath: process.env.FIREBASE_KEY_PATH || "./firebase-key.json",
  adminPassword: process.env.ADMIN_PASSWORD,
  sessionSecret: process.env.SESSION_SECRET,
  termsVersion: Number(process.env.TERMS_VERSION || 1),
  activeWindowMs: 5 * 60 * 1000, // "activo ahora" = visto en los últimos 5 min
  maxPromptChars: 4000,
};
