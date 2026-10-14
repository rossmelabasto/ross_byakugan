const crypto = require("crypto");

const sha256 = (s) => crypto.createHash("sha256").update(s).digest("hex");

// Token de dispositivo: 32 bytes aleatorios; en la BD solo se guarda su hash.
const newToken = () => "byk_" + crypto.randomBytes(32).toString("base64url");

// Código de invitación legible: BYA-XXXX-XXXX (sin 0/O/1/I para evitar confusiones).
const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
function newInviteCode() {
  const pick = () => ALPHABET[crypto.randomInt(ALPHABET.length)];
  const block = () => Array.from({ length: 4 }, pick).join("");
  return `BYA-${block()}-${block()}`;
}
const normalizeCode = (c) => String(c || "").trim().toUpperCase();

// Día calendario en Bolivia (GMT-4), para el contador diario de cuota.
function dayKey(date = new Date()) {
  return new Date(date.getTime() - 4 * 3600 * 1000).toISOString().slice(0, 10);
}

function safeEqual(a, b) {
  const x = Buffer.from(String(a));
  const y = Buffer.from(String(b));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

// Sesión del admin: cookie "exp.firma" firmada con HMAC.
function signSession(secret, ttlMs = 12 * 3600 * 1000) {
  const exp = String(Date.now() + ttlMs);
  const sig = crypto.createHmac("sha256", secret).update(exp).digest("hex");
  return `${exp}.${sig}`;
}
function verifySession(secret, value) {
  if (!value || !secret) return false;
  const [exp, sig] = String(value).split(".");
  if (!exp || !sig || Number(exp) < Date.now()) return false;
  const good = crypto.createHmac("sha256", secret).update(exp).digest("hex");
  return safeEqual(sig, good);
}

module.exports = { sha256, newToken, newInviteCode, normalizeCode, dayKey, safeEqual, signSession, verifySession };
