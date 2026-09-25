type Config = { url: string; serviceKey: string; ownerUserId: string; origin: string };

// Ein Besitzer für das bestehende private Dashboard; niemals beliebige angemeldete Nutzer zulassen.
export function handler(config: Config, send: typeof fetch = fetch) {
  return async (req: Request): Promise<Response> => {
    const origin = req.headers.get("origin");
    const headers = {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": config.origin,
      "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Vary": "Origin",
    };
    const response = (status: number, data: object) => new Response(JSON.stringify(data), { status, headers });
    if (origin && origin !== config.origin) return response(403, { error: "Origin not allowed" });
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers });
    if (req.method !== "POST") return response(405, { error: "POST required" });
    if (!config.url || !config.serviceKey || !config.ownerUserId || !config.origin) {
      return response(503, { error: "Owner registration not configured" });
    }
    const authorization = req.headers.get("authorization") ?? "";
    if (!authorization.startsWith("Bearer ")) return response(401, { error: "Sign in required" });
    try {
      // Online-Prüfung bei Supabase Auth; weder JWT-Inhalt noch user_metadata vertrauen.
      const verified = await send(`${config.url}/auth/v1/user`, {
        headers: { apikey: config.serviceKey, Authorization: authorization },
      });
      if (!verified.ok) return response(401, { error: "Invalid session" });
      const user = await verified.json();
      if (user.id !== config.ownerUserId || user.is_anonymous === true) {
        return response(403, { error: "Owner access required" });
      }
      if (Number(req.headers.get("content-length") ?? 0) > 4096) {
        return response(413, { error: "Request too large" });
      }
      const raw = await req.text();
      if (raw.length > 4096) return response(413, { error: "Request too large" });
      let data;
      try { data = JSON.parse(raw); } catch { return response(400, { error: "Invalid JSON" }); }
      if (!data || typeof data.token !== "string" ||
          !/^[A-Za-z0-9_:.-]{20,2048}$/.test(data.token)) {
        return response(400, { error: "Invalid FCM token" });
      }
      const saved = await send(`${config.url}/rest/v1/owner?id=eq.dashboard&select=id`, {
        method: "PATCH",
        headers: {
          apikey: config.serviceKey, Authorization: `Bearer ${config.serviceKey}`,
          "Content-Type": "application/json", Prefer: "return=representation",
        },
        body: JSON.stringify({ android_fcm_token: data.token }),
      });
      if (!saved.ok) return response(502, { error: "Token could not be saved" });
      // PATCH legt keine Zeile an. Nur ein bestätigtes Update gilt als Erfolg.
      const rows = await saved.json();
      if (Array.isArray(rows) && rows.length === 0) {
        return response(503, { error: "Owner registration not configured" });
      }
      if (!Array.isArray(rows) || rows.length !== 1 || rows[0]?.id !== "dashboard") {
        return response(502, { error: "Token update could not be confirmed" });
      }
      return response(200, { success: true });
    } catch {
      return response(502, { error: "Registration temporarily unavailable" });
    }
  };
}
