// Avisos por Telegram (opcional: sin token/chat no hace nada). Nunca incluye el texto de las preguntas.
const token = process.env.TELEGRAM_BOT_TOKEN;
const chatId = process.env.TELEGRAM_CHAT_ID;

const esc = (s) => String(s ?? "").replace(/[&<>]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" })[c]);

// Evita spam: un mismo aviso (clave) como máximo cada `everyMs`.
const last = new Map();
function throttled(key, everyMs) {
  const now = Date.now();
  if (now - (last.get(key) || 0) < everyMs) return true;
  last.set(key, now);
  if (last.size > 500) for (const [k, t] of last) if (now - t > everyMs) last.delete(k);
  return false;
}

async function send(text) {
  if (!token || !chatId) return;
  try {
    const r = await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: chatId, text: `👁 <b>byakugan</b>\n${text}`, parse_mode: "HTML", disable_web_page_preview: true }),
      signal: AbortSignal.timeout(8000),
    });
    const j = await r.json();
    if (!j.ok) console.error("telegram:", j.description);
  } catch (e) {
    console.error("telegram:", e.message);
  }
}

// `notify` nunca lanza ni bloquea la petición del usuario.
const notify = (text, { key, everyMs = 0 } = {}) => {
  if (key && throttled(key, everyMs)) return;
  send(text);
};

module.exports = {
  notify,
  esc,
  enabled: Boolean(token && chatId),
};
