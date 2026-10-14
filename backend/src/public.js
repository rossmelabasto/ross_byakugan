const express = require("express");
const config = require("./config");
const { db, FieldValue, Timestamp } = require("./db");
const ai = require("./ai");
const { sha256, newToken, normalizeCode, dayKey } = require("./util");

const router = express.Router();

// Canjea un código de invitación por el token del dispositivo (requiere aceptar los términos).
router.post("/enroll", async (req, res) => {
  const { code, accepted, deviceName, meta } = req.body || {};
  if (accepted !== true) return res.status(400).json({ error: "Debes aceptar los términos." });
  const codeId = sha256(normalizeCode(code));
  const inviteRef = db.collection("invites").doc(codeId);
  const token = newToken();
  const deviceRef = db.collection("devices").doc();

  try {
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(inviteRef);
      const inv = snap.data();
      const expired = inv?.expiresAt && inv.expiresAt.toMillis() < Date.now();
      if (!snap.exists || !inv.active || expired || inv.uses >= inv.maxUses) {
        throw Object.assign(new Error("invalid"), { status: 403 });
      }
      tx.update(inviteRef, { uses: FieldValue.increment(1) });
      tx.set(deviceRef, {
        tokenHash: sha256(token),
        alias: String(deviceName || inv.label || "sin nombre").slice(0, 60),
        plan: inv.plan,
        active: true,
        termsVersion: config.termsVersion,
        meta: {
          manufacturer: String(meta?.manufacturer || "").slice(0, 40),
          model: String(meta?.model || "").slice(0, 40),
          androidVersion: String(meta?.androidVersion || "").slice(0, 20),
        },
        inviteId: codeId,
        createdAt: FieldValue.serverTimestamp(),
        lastSeen: FieldValue.serverTimestamp(),
      });
    });
  } catch (e) {
    if (e.status === 403) return res.status(403).json({ error: "Código inválido o agotado." });
    console.error("enroll:", e);
    return res.status(500).json({ error: "Error interno." });
  }
  res.json({ token, termsVersion: config.termsVersion });
});

// Autenticación por token de dispositivo (Bearer).
const auth = makeAuth(true);
const authAllowOldTerms = makeAuth(false);

function makeAuth(enforceTerms) {
  return async (req, res, next) => {
  const m = /^Bearer (byk_[\w-]+)$/.exec(req.get("authorization") || "");
  if (!m) return res.status(401).json({ error: "No autorizado." });
  const q = await db.collection("devices").where("tokenHash", "==", sha256(m[1])).limit(1).get();
  if (q.empty || !q.docs[0].data().active) return res.status(401).json({ error: "No autorizado." });
  req.device = { id: q.docs[0].id, ...q.docs[0].data() };
  if (enforceTerms && req.device.termsVersion < config.termsVersion) {
    return res.status(426).json({ error: "Acepta los nuevos términos.", termsVersion: config.termsVersion });
  }
  next();
  };
}

const limitOf = (d) => d.dailyLimit ?? config.PLANS[d.plan]?.dailyLimit ?? 0;

async function usageToday(deviceId) {
  const s = await db.collection("usage_daily").doc(`${deviceId}_${dayKey()}`).get();
  return s.exists ? s.data().count : 0;
}

// Latido + estado de cuota (la app lo llama al abrirse).
router.get("/me", auth, async (req, res) => {
  const d = req.device;
  await db.collection("devices").doc(d.id).update({ lastSeen: FieldValue.serverTimestamp() });
  const used = await usageToday(d.id);
  res.json({ alias: d.alias, plan: d.plan, used, limit: limitOf(d), termsVersion: config.termsVersion });
});

// Consulta.
router.post("/ask", auth, async (req, res) => {
  const d = req.device;
  const prompt = String(req.body?.prompt || "").trim();
  if (!prompt) return res.status(400).json({ error: "Texto vacío." });
  if (prompt.length > config.maxPromptChars) return res.status(413).json({ error: "Texto demasiado largo." });

  // Reserva de cuota atómica antes de llamar al modelo.
  const usageRef = db.collection("usage_daily").doc(`${d.id}_${dayKey()}`);
  const limit = limitOf(d);
  try {
    await db.runTransaction(async (tx) => {
      const s = await tx.get(usageRef);
      const count = s.exists ? s.data().count : 0;
      if (count >= limit) throw Object.assign(new Error("quota"), { status: 429 });
      tx.set(usageRef, { deviceId: d.id, day: dayKey(), count: count + 1, tokens: s.data()?.tokens ?? 0 }, { merge: true });
    });
  } catch (e) {
    if (e.status === 429) return res.status(429).json({ error: "Cuota diaria agotada.", limit });
    console.error("quota:", e);
    return res.status(500).json({ error: "Error interno." });
  }

  try {
    const out = await ai.ask(prompt);
    await Promise.all([
      db.collection("queries").add({
        deviceId: d.id, alias: d.alias, prompt, response: out.text,
        tokens: out.tokens, ms: out.ms, createdAt: FieldValue.serverTimestamp(),
      }),
      usageRef.update({ tokens: FieldValue.increment(out.tokens) }),
      db.collection("devices").doc(d.id).update({ lastSeen: FieldValue.serverTimestamp() }),
    ]);
    res.json({ response: out.text });
  } catch (e) {
    console.error("ask:", e);
    // Devuelve la cuota si el modelo falló.
    await usageRef.update({ count: FieldValue.increment(-1) }).catch(() => {});
    res.status(502).json({ error: "El modelo no respondió. Intenta de nuevo." });
  }
});

// El usuario acepta la versión nueva de los términos.
router.post("/accept-terms", authAllowOldTerms, async (req, res) => {
  if (req.body?.accepted !== true) return res.status(400).json({ error: "Debes aceptar los términos." });
  await db.collection("devices").doc(req.device.id).update({ termsVersion: config.termsVersion });
  res.json({ termsVersion: config.termsVersion });
});

module.exports = router;
