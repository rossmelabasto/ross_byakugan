const express = require("express");
const config = require("./config");
const publicRoutes = require("./public");

const app = express();
app.disable("x-powered-by");
app.use(express.json({ limit: "32kb" }));
app.get("/healthz", (_req, res) => res.json({ ok: true }));
app.use("/v1", publicRoutes);
// El panel admin (src/admin.js) se monta aquí en el siguiente paso.

app.listen(config.port, config.host, () => console.log(`byakugan escuchando en ${config.host}:${config.port}`));
