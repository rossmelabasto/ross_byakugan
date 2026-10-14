# ross_byakugan

Asistente de estudio: seleccionas texto en el móvil → **Consultar** → respuesta con la opción correcta y una explicación breve.

| Carpeta | Qué es |
|---|---|
| `backend/` | Express 5 + Firestore + Groq. API `/v1/enroll`, `/v1/me`, `/v1/ask`, `/v1/accept-terms` y panel en `/admin` |
| `app/` | App Android en Java, sin Gradle ni librerías (`./build.sh [install]`) |
| `docs/TERMS.md` | Términos que acepta cada dispositivo al activarse (la app los empaqueta) |
| `docs/referencia-watcher/` | Referencia de la versión anterior (the-watcher / guardian-angel): inventario, problemas y qué se corrigió |
| `firestore.rules` | Reglas deny-all: todo el acceso va por el backend (cuenta de servicio) |
| `docker-compose.yml` | Despliegue en lilarch (puerto `127.0.0.1:8101`, detrás de Cloudflare Tunnel) |

## Cómo funciona
- **Acceso por invitación:** el admin crea un código `BYA-XXXX-XXXX` en el panel; el usuario lo canjea en la app aceptando los términos y recibe un token por dispositivo (en la BD solo se guarda su hash).
- **Planes:** `free` (20/día), `pro` (100/día) y `unlimited`. El tope se puede sobrescribir por dispositivo.
- **Panel:** resumen con gráfico de 14 días, dispositivos activos y cuota, historial de consultas con búsqueda, invitaciones. Protegido con contraseña y, en producción, con Cloudflare Access.
- **Respuestas:** modelo `openai/gpt-oss-120b` en Groq con `reasoning_effort=high` y temperatura 0 (en pruebas, con esfuerzo medio fallaba una pregunta ambigua de OLAP que con alto acertaba siempre).
- **Avisos por Telegram:** dispositivo nuevo, código rechazado, cuota agotada, fallo del modelo o login fallido (opcional, sin texto de las preguntas).

## Arranque local
```bash
cd backend && cp .env.example .env     # rellena GROQ_API_KEY, ADMIN_PASSWORD, SESSION_SECRET
# coloca la cuenta de servicio de Firebase como backend/firebase-key.json
npm install && npm start               # http://127.0.0.1:8100/admin
npm test                               # utilidades (token, códigos, día GMT-4, sesión)
```

## App Android
```bash
cd app && ./build.sh install           # aapt2 → javac → d8 → zipalign → apksigner → adb install
```
Usa el JDK de Android Studio si no hay `javac` en el PATH. La llave de firma (`app/byakugan-debug.keystore`) se genera sola y no se versiona. Para apuntar a otro servidor, cambia `Api.BASE`.

## Secretos (no versionados)
`backend/.env`, `backend/firebase-key.json` y `app/*.keystore` están en `.gitignore`.
