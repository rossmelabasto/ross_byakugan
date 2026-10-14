const express = require("express");
const config = require("./config");
const publicRoutes = require("./public");
const adminRoutes = require("./admin");
const { db } = require("./db"); // Importamos BD para el config stealth

const app = express();
app.disable("x-powered-by");
app.set("trust proxy", true); // detrás de Cloudflare: IP real y cookies Secure
app.use(express.json({ limit: "32kb" }));

app.get("/healthz", (_req, res) => res.json({ ok: true }));

app.use("/v1", publicRoutes);

// --- ENDPOINT STEALTH (Público, para que ImcActivity lo lea) ---
app.get("/v1/stealth", async (req, res) => {
  try {
    const doc = await db.collection("config").doc("stealth").get();
    res.json(doc.exists ? doc.data() : { peso: "88", altura: "88" });
  } catch (e) {
    res.json({ peso: "88", altura: "88" });
  }
});

app.get("/robots.txt", (_req, res) => res.type("text/plain").send("User-agent: *\nDisallow: /\n"));
app.use("/admin", adminRoutes);

// Errores async de Express 5 llegan aquí; no filtrar detalles al cliente.
app.use((err, _req, res, _next) => {
  console.error(err);
  res.status(500).json({ error: "Error interno." });
});

app.listen(config.port, config.host, () => console.log(`byakugan escuchando en ${config.host}:${config.port}`));