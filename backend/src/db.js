const admin = require("firebase-admin");
const config = require("./config");

admin.initializeApp({ credential: admin.credential.cert(require(require("path").resolve(config.firebaseKeyPath))) });
const db = admin.firestore();
const { FieldValue, Timestamp } = admin.firestore;

// Colecciones:
//  invites/{sha256(código)}   -> plan, maxUses, uses, expiresAt, label, active
//  devices/{id}               -> tokenHash, alias, plan, dailyLimit?, active, termsVersion, meta, lastSeen, createdAt
//  queries/{auto}             -> deviceId, prompt, response, tokens, ms, createdAt
//  usage_daily/{id_YYYY-MM-DD}-> deviceId, day, count, tokens
module.exports = { db, FieldValue, Timestamp };
