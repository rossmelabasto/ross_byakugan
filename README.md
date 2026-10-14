# byakugan

Asistente de estudio: seleccionas texto en el móvil → "Consultar" → respuesta con la opción correcta y una explicación breve.

- `backend/` — Express + Firestore + Groq (`/v1/enroll`, `/v1/me`, `/v1/ask`). Se despliega en lilarch (Docker + Cloudflare Tunnel).
- `docs/TERMS.md` — términos que acepta cada dispositivo al canjear su invitación.
- Pendiente: `backend/src/admin.js` + panel web (dispositivos activos, consultas, invitaciones) y la app Android.

Arranque local: `cd backend && cp .env.example .env && npm install && npm start`.
