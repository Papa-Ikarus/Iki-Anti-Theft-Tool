import { handler } from "./handler.ts";
import { reportMessage } from "../daily-report/push-message.ts";

function assert(value: unknown, message = "Assertion failed"): asserts value {
  if (!value) throw new Error(message);
}
const config = { url: "https://example.supabase.co", serviceKey: "test-server-only", ownerUserId: "owner-1", origin: "https://dashboard.example" };
function request(body: unknown = { token: "test-token-12345678901234567890" }, auth = "Bearer session", origin = config.origin) {
  return new Request("https://example.test", { method: "POST", headers: {
    authorization: auth, origin, "Content-Type": "application/json",
  }, body: JSON.stringify(body) });
}

Deno.test("Kein Zugriff ohne Sitzung oder vom fremden Ursprung", async () => {
  const run = handler(config, () => { throw new Error("No network expected"); });
  assert((await run(request({}, ""))).status === 401);
  assert((await run(request({}, "Bearer session", "https://evil.example"))).status === 403);
});

Deno.test("Fremdes und anonymes Konto dürfen Besitzer-Token nicht überschreiben", async () => {
  for (const user of [{ id: "another-user" }, { id: "owner-1", is_anonymous: true }]) {
    let calls = 0;
    const run = handler(config, async () => { calls++; return Response.json(user); });
    assert((await run(request())).status === 403);
    assert(calls === 1);
  }
});

Deno.test("Ungültige Sitzung, Token und fehlende Konfiguration werden abgewiesen", async () => {
  assert((await handler(config, async () => new Response(null, { status: 401 }))(request())).status === 401);
  const run = handler(config, async () => Response.json({ id: "owner-1" }));
  assert((await run(request({ token: "bad\nvalue" }))).status === 400);
  assert((await run(request({ token: "a".repeat(5000) }))).status === 413);
  assert((await handler({ ...config, ownerUserId: "" })(request())).status === 503);
});

Deno.test("Nur android_fcm_token wird per UPDATE am festen Owner geändert, niemals INSERT/Upsert", async () => {
  let calls = 0;
  const run = handler(config, async (url, init) => {
    calls++;
    if (calls === 1) {
      assert(String(url) === `${config.url}/auth/v1/user`);
      assert(new Headers(init?.headers).get("Authorization") === "Bearer session");
      return Response.json({ id: "owner-1" });
    }
    assert(String(url) === `${config.url}/rest/v1/owner?id=eq.dashboard&select=id`);
    assert(init?.method === "PATCH", "Kein INSERT oder Upsert erlaubt");
    assert(new Headers(init.headers).get("Prefer") === "return=representation");
    const saved = JSON.parse(String(init?.body));
    assert(JSON.stringify(Object.keys(saved)) === JSON.stringify(["android_fcm_token"]));
    assert(saved.android_fcm_token === "test-token-12345678901234567890");
    for (const field of ["id", "updated_at", "fcm_token", "registered_at", "other"]) {
      assert(!(field in saved), `${field} darf nicht geschrieben werden`);
    }
    return Response.json([{ id: "dashboard" }]);
  });
  const result = await run(request({ token: "test-token-12345678901234567890",
    id: "another-owner", updated_at: "changed", fcm_token: "changed", registered_at: 123, other: "changed" }));
  assert(result.status === 200);
  assert(await result.text() === JSON.stringify({ success: true }), "Keine Secrets in der Antwort");
  assert(calls === 2);
});

Deno.test("Fehlender Dashboard-Owner führt zu 503 ohne Anlage oder weiteren Schreibversuch", async () => {
  let calls = 0;
  const run = handler(config, async (_url, init) => {
    calls++;
    if (calls === 1) return Response.json({ id: "owner-1" });
    assert(init?.method === "PATCH");
    return Response.json([]);
  });
  const result = await run(request());
  assert(result.status === 503);
  assert((await result.json()).error === "Owner registration not configured");
  assert(calls === 2, "Kein Fallback-Insert");
});

Deno.test("Unerwartete Update-Antwort meldet keinen falschen Erfolg und keine Secrets", async () => {
  for (const body of [null, {}, [{ id: "other" }], [{ id: "dashboard" }, { id: "other" }]]) {
    let calls = 0;
    const run = handler(config, async () => ++calls === 1
      ? Response.json({ id: "owner-1" }) : Response.json(body));
    const result = await run(request());
    assert(result.status === 502);
    assert(!(await result.text()).includes(config.serviceKey));
    assert(calls === 2);
  }
});

Deno.test("Datenbankfehler meldet keinen Registrierungserfolg", async () => {
  let calls = 0;
  const run = handler(config, async () => ++calls === 1 ? Response.json({ id: "owner-1" }) : new Response(null, { status: 500 }));
  assert((await run(request())).status === 502);
});

Deno.test("Android bekommt ausschließlich Data-Push, Web behält seinen bisherigen Kanal", () => {
  const input = { token: "test-token", deviceId: "test-phone", date: "2026-09-22", title: "Bericht", body: "Text", mapsUrl: "https://maps.example" };
  const android = reportMessage({ ...input, android: true });
  assert(!("notification" in android) && !("webpush" in android));
  assert(android.data?.command === "DAILY_REPORT");
  assert(android.data?.device_id === input.deviceId && android.data?.date === input.date);
  const web = reportMessage({ ...input, android: false });
  assert(!("data" in web));
  assert(web.webpush?.fcm_options.link === input.mapsUrl);
});
