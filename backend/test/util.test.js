const test = require("node:test");
const assert = require("node:assert");
const u = require("../src/util");

test("token y código tienen el formato esperado", () => {
  assert.match(u.newToken(), /^byk_[\w-]{43}$/);
  assert.match(u.newInviteCode(), /^BYA-[A-Z2-9]{4}-[A-Z2-9]{4}$/);
});
test("normalizeCode ignora espacios y mayúsculas", () => {
  assert.strictEqual(u.normalizeCode("  bya-abcd-efgh "), "BYA-ABCD-EFGH");
});
test("dayKey usa GMT-4", () => {
  assert.strictEqual(u.dayKey(new Date("2026-10-13T02:00:00Z")), "2026-10-12");
  assert.strictEqual(u.dayKey(new Date("2026-10-13T05:00:00Z")), "2026-10-13");
});
test("sesión admin: válida, manipulada y expirada", () => {
  const v = u.signSession("s3cret");
  assert.ok(u.verifySession("s3cret", v));
  assert.ok(!u.verifySession("otro", v));
  assert.ok(!u.verifySession("s3cret", v.replace(/.$/, "0")) || v.endsWith("0"));
  assert.ok(!u.verifySession("s3cret", u.signSession("s3cret", -1000)));
});
