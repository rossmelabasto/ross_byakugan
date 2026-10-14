# Referencia: the-watcher / guardian-angel (versión anterior)

Documento de referencia de lo que existía **antes** de byakugan. No se continúa: byakugan se rehízo de cero (ver `../../README.md`). Se conserva para saber de dónde viene y qué se corrigió.

> Auditoría hecha el 2026-10-13 desde `~/code/watcher-backend`, `~/Documents/apks/` y los APK desensamblados con `apkanalyzer`. El código fuente Android original (proyecto de Android Studio) se perdió: estaba en una laptop que se formateó sin respaldo.

## Qué era
App Android que se instalaba en el móvil y añadía una acción **«Consultar»** al menú de selección de texto (copiar / pegar / …). Al usarla enviaba el texto seleccionado a un backend propio, que llamaba a Groq y devolvía la respuesta.

## Inventario

### Backend — `~/code/watcher-backend`
Repo `rossmelabasto/watcher-backend` (6 commits, 2026-04-21 → 2026-04-23). Copia del código en `index.js` y `package.json` de esta carpeta (sin secretos).
- **Stack:** Node + Express 5 + `firebase-admin` (Firestore) + `groq-sdk`; desplegado en Render (`watcher-backend-d571.onrender.com`, plan gratis).
- **Endpoint único:** `POST /ask` con `{ deviceId, prompt, deviceMetadata }`.
- **Autorización:** busca `authorized_devices/{deviceId}` en Firestore; si existe y `isActive`, llama a Groq (`openai/gpt-oss-120b`) con el prompt tal cual y devuelve el texto.
- **Si no está autorizado:** guarda un registro en `unauthorized_logs` y avisa por Telegram con los datos del equipo; responde 403.
- **Variables (`.env`, no versionado):** `GROQ_API_KEY`, `PORT`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`; más `firebase-key.json` (cuenta de servicio).

### Apps — `~/Documents/apks/` (copias en `apks/` de esta carpeta, con `SHA256SUMS`)
| Archivo | Fecha | SHA-256 (inicio) |
|---|---|---|
| `watcher.apk` | 2026-04-21 | `406cfd06bd50dc28…` |
| `guardian_angel.apk` | 2026-05-17 | `bef3d0b9bed41154…` |

Ambos: paquete `com.rossmel.the_watcher`, versión 1.0, minSdk 26, targetSdk 36, Kotlin + Jetpack Compose, 11 MB.
- Una sola actividad, `WatcherInferenceActivity`, etiquetada «Consultar», con `intent-filter` `PROCESS_TEXT` (`text/plain`), `excludeFromRecents`.
- Etiqueta de la aplicación: «System App».
- Permiso único: `INTERNET`. Compilados con `debuggable=true` y `allowBackup=true`.
- Envía al backend `android_id`, `deviceName`, `manufacturer`, `model`, `androidVersion` y `prompt` (POST a `/ask`).

### Datos
- Firestore (proyecto del backend viejo): colecciones `authorized_devices` y `unauthorized_logs`.
- **Supabase: no se encontró nada.** El almacenamiento era Firestore; los tres proyectos de Supabase de la cuenta (`hibi`, `AdvAI`, `eko`) no tienen relación.

## Problemas encontrados
1. **Bug de Telegram.** `index.js` llama a `notifyTelegram(deviceId, prompt, deviceMetadata)` pero la función declara cuatro parámetros `(deviceId, prompt, originalPrompt, metadata)`: los metadatos llegaban como `originalPrompt` y `metadata` quedaba `undefined` (el aviso salía con «N/A»).
2. **No se registraba nada de las consultas autorizadas:** ni pregunta, ni respuesta, ni tokens, ni latencia, ni última actividad. Sin eso no es posible un dashboard.
3. **Identidad débil:** el `android_id` viajaba en el body, sin token ni firma; cualquiera que lo conociera podía suplantar al dispositivo. Tampoco había límite de uso.
4. **Cold start de Render** (plan gratis): la primera consulta podía tardar hasta ~1 minuto.
5. **Sin alta/baja de dispositivos** salvo editar Firestore a mano.
6. Respuestas sin formato fijo: no se garantizaba la opción correcta + explicación.

## Qué se hizo en byakugan (2026-10-13)
| Problema | Solución en byakugan |
|---|---|
| 1 | Reescrito `notify.js`, avisos con límite de frecuencia y sin el texto de las preguntas |
| 2 | Colecciones `queries` y `usage_daily`; panel con historial, resumen y dispositivos activos |
| 3 | Token por dispositivo (`byk_…`, solo su hash en BD), invitaciones, planes y cuota diaria atómica (`free` 20, `pro` 100, `unlimited`) |
| 4 | Despliegue en lilarch (Docker + Cloudflare Tunnel), sin cold start |
| 5 | Panel admin: invitar, suspender, cambiar de plan, renombrar |
| 6 | Prompt de sistema con formato «Respuesta: … / Explicación: …», temperatura 0 y razonamiento alto |

## Decisión sobre el alcance
La versión anterior estaba pensada para usarse sin que se notara (etiqueta «System App», fuera de recientes, sin trazabilidad). byakugan se rehízo como **asistente de estudio abierto**: nombre e icono visibles, consentimiento y términos explícitos (`../TERMS.md`), y registro de las consultas con aviso al usuario. Lo encubierto no se retoma.
