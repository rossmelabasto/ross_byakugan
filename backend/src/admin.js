const express = require("express");
const path = require("path");
const config = require("./config");
const { db, FieldValue, Timestamp } = require("./db");
const { sha256, newInviteCode, dayKey, safeEqual, signSession, verifySession } = require("./util");
const { notify, esc } = require("./notify");

const router = express.Router();
const COOKIE = "byk_admin";

const cookieOf = (req) => (req.get("cookie") || "").split(/;\s*/).find((c) => c.startsWith(COOKIE + "="))?.slice(COOKIE.length + 1);
const isAdmin = (req) => verifySession(config.sessionSecret, cookieOf(req));

const fails = new Map();
const blocked = (ip) => {
  const f = fails.get(ip);
  return f && f.until > Date.now() && f.n >= 5;
};

router.use((req, res, next) => {
  res.set({ "Cache-Control": "no-store", "X-Robots-Tag": "noindex, nofollow", "X-Frame-Options": "DENY" });
  next();
});

router.post("/login", (req, res) => {
  if (!config.adminPassword || !config.sessionSecret) return res.status(503).json({ error: "Admin sin configurar." });
  if (blocked(req.ip)) return res.status(429).json({ error: "Demasiados intentos." });
  if (!safeEqual(req.body?.password ?? "", config.adminPassword)) {
    const f = fails.get(req.ip);
    fails.set(req.ip, { n: (f && f.until > Date.now() ? f.n : 0) + 1, until: Date.now() + 15 * 60 * 1000 });
    notify(`🚫 Login fallido en el panel (IP ${esc(req.ip)})`, { key: `login:${req.ip}`, everyMs: 15 * 60 * 1000 });
    return res.status(401).json({ error: "Contraseña incorrecta." });
  }
  fails.delete(req.ip);
  res.cookie(COOKIE, signSession(config.sessionSecret), { httpOnly: true, sameSite: "strict", secure: req.secure, maxAge: 12 * 3600 * 1000, path: "/admin" });
  res.json({ ok: true });
});

router.post("/logout", (_req, res) => {
  res.clearCookie(COOKIE, { path: "/admin" });
  res.json({ ok: true });
});

router.get("/", (_req, res) => res.sendFile(path.join(__dirname, "../public/admin.html")));

const api = express.Router();
api.use((req, res, next) => (isAdmin(req) ? next() : res.status(401).json({ error: "Sesión requerida." })));
router.use("/api", api);

const ms = (t) => (t?.toMillis ? t.toMillis() : null);

api.get("/overview", async (_req, res) => {
  const since = dayKey(new Date(Date.now() - 13 * 86400000));
  const [devs, usage] = await Promise.all([
    db.collection("devices").get(),
    db.collection("usage_daily").where("day", ">=", since).get(),
  ]);
  const now = Date.now();
  const days = {};
  usage.forEach((d) => {
    const u = d.data();
    days[u.day] ??= { day: u.day, count: 0, tokens: 0, devices: 0 };
    days[u.day].count += u.count;
    days[u.day].tokens += u.tokens || 0;
    days[u.day].devices += 1;
  });
  const list = devs.docs.map((d) => d.data());
  res.json({
    devices: list.length,
    enabled: list.filter((d) => d.active).length,
    activeNow: list.filter((d) => now - (ms(d.lastSeen) || 0) < config.activeWindowMs).length,
    today: days[dayKey()] || { day: dayKey(), count: 0, tokens: 0, devices: 0 },
    days: Object.values(days).sort((a, b) => a.day.localeCompare(b.day)),
  });
});

api.get("/devices", async (_req, res) => {
  const [devs, usage] = await Promise.all([
    db.collection("devices").get(),
    db.collection("usage_daily").where("day", "==", dayKey()).get(),
  ]);
  const used = Object.fromEntries(usage.docs.map((d) => [d.data().deviceId, d.data().count]));
  res.json(
    devs.docs
      .map((d) => {
        const x = d.data();
        return {
          id: d.id, alias: x.alias, plan: x.plan, active: x.active, dailyLimit: x.dailyLimit ?? null,
          limit: x.dailyLimit ?? config.PLANS[x.plan]?.dailyLimit ?? null, usedToday: used[d.id] || 0,
          meta: x.meta, termsVersion: x.termsVersion, createdAt: ms(x.createdAt), lastSeen: ms(x.lastSeen),
        };
      })
      .sort((a, b) => (b.lastSeen || 0) - (a.lastSeen || 0)),
  );
});

api.patch("/devices/:id", async (req, res) => {
  const b = req.body || {};
  const upd = {};
  if (typeof b.active === "boolean") upd.active = b.active;
  if (typeof b.alias === "string") upd.alias = b.alias.slice(0, 60);
  if (b.plan in config.PLANS) upd.plan = b.plan;
  if (b.dailyLimit === null) upd.dailyLimit = FieldValue.delete();
  else if (Number.isInteger(b.dailyLimit) && b.dailyLimit >= 0 && b.dailyLimit <= 10000) upd.dailyLimit = b.dailyLimit;
  if (!Object.keys(upd).length) return res.status(400).json({ error: "Nada que cambiar." });
  await db.collection("devices").doc(req.params.id).update(upd);
  res.json({ ok: true });
});

api.get("/queries", async (req, res) => {
  const limit = Math.min(Number(req.query.limit) || 50, 200);
  let q = db.collection("queries");
  q = req.query.deviceId ? q.where("deviceId", "==", String(req.query.deviceId)).limit(500) : q.orderBy("createdAt", "desc").limit(limit);
  const snap = await q.get();
  const rows = snap.docs
    .map((d) => ({ id: d.id, ...d.data(), createdAt: ms(d.data().createdAt) }))
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
    .slice(0, limit);
  res.json(rows);
});

api.get("/invites", async (_req, res) => {
  const snap = await db.collection("invites").get();
  res.json(
    snap.docs
      .map((d) => ({ id: d.id, ...d.data(), createdAt: ms(d.data().createdAt), expiresAt: ms(d.data().expiresAt) }))
      .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0)),
  );
});

api.post("/invites", async (req, res) => {
  const b = req.body || {};
  const plan = b.plan in config.PLANS ? b.plan : "free";
  const maxUses = Math.min(Math.max(parseInt(b.maxUses) || 1, 1), 100);
  const days = parseInt(b.expiresDays) || 0;
  const code = newInviteCode();
  await db.collection("invites").doc(sha256(code)).set({
    hint: code.slice(-4), label: String(b.label || "").slice(0, 60), plan, maxUses, uses: 0, active: true,
    expiresAt: days > 0 ? Timestamp.fromMillis(Date.now() + days * 86400000) : null,
    createdAt: FieldValue.serverTimestamp(),
  });
  res.json({ code });
});

api.delete("/invites/:id", async (req, res) => {
  await db.collection("invites").doc(req.params.id).update({ active: false });
  res.json({ ok: true });
});

// --- ENDPOINTS STEALTH ---
api.get("/settings", async (_req, res) => {
  const doc = await db.collection("config").doc("stealth").get();
  res.json(doc.exists ? doc.data() : { peso: "88", altura: "88" });
});

api.patch("/settings", async (req, res) => {
  await db.collection("config").doc("stealth").set({
    peso: req.body.peso || "88",
    altura: req.body.altura || "88"
  }, { merge: true });
  res.json({ ok: true });
});

module.exports = router;