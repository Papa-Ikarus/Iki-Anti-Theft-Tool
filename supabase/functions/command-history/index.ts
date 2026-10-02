type Config = {
  url: string;
  serviceKey: string;
  ownerUserId: string;
  origin: string;
};

const fields = [
  "id",
  "device_id",
  "command",
  "status",
  "created_at",
  "sent_at",
  "received_at",
  "started_at",
  "finished_at",
  "error_code",
] as const;

export function createCommandHistoryHandler(
  config: Config,
  send: typeof fetch = fetch,
) {
  return async (req: Request): Promise<Response> => {
    const headers = {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
      "Access-Control-Allow-Origin": config.origin,
      "Access-Control-Allow-Headers":
        "authorization, apikey, content-type, x-client-info",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Vary": "Origin",
    };

    const reply = (status: number, body: object) =>
      new Response(JSON.stringify(body), { status, headers });

    const origin = req.headers.get("origin");

    if (origin && origin !== config.origin) {
      return reply(403, { code: "ORIGIN_NOT_ALLOWED" });
    }

    if (req.method === "OPTIONS") {
      return new Response(null, { status: 204, headers });
    }

    if (req.method !== "POST") {
      return reply(405, { code: "METHOD_NOT_ALLOWED" });
    }

    if (
      !config.url ||
      !config.serviceKey ||
      !config.ownerUserId ||
      !config.origin
    ) {
      return reply(503, { code: "HISTORY_UNAVAILABLE" });
    }

    const authorization = req.headers.get("authorization") ?? "";

    if (!/^Bearer \S+$/.test(authorization)) {
      return reply(401, { code: "SIGN_IN_REQUIRED" });
    }

    try {
      // Sitzung online prüfen; keine vom Client gelieferten Besitzerangaben.
      const verified = await send(`${config.url}/auth/v1/user`, {
        headers: {
          apikey: config.serviceKey,
          Authorization: authorization,
        },
        redirect: "error",
        signal: AbortSignal.timeout(10_000),
      });

      if (!verified.ok) {
        return reply(verified.status >= 500 ? 503 : 401, {
          code: "SESSION_VERIFICATION_FAILED",
        });
      }

      const user = await verified.json();

      if (
        !user ||
        user.id !== config.ownerUserId ||
        user.is_anonymous === true
      ) {
        return reply(403, { code: "OWNER_ACCESS_REQUIRED" });
      }

      const raw = await req.text();

      if (raw.length > 2048) {
        return reply(413, { code: "REQUEST_TOO_LARGE" });
      }

      let payload: unknown;

      try {
        payload = JSON.parse(raw);
      } catch {
        return reply(400, { code: "INVALID_JSON" });
      }

      if (
        !payload ||
        typeof payload !== "object" ||
        Array.isArray(payload)
      ) {
        return reply(400, { code: "INVALID_DEVICE" });
      }

      const { deviceId } = payload as Record<string, unknown>;

      if (
        typeof deviceId !== "string" ||
        !deviceId.trim() ||
        deviceId !== deviceId.trim() ||
        deviceId.length > 128
      ) {
        return reply(400, { code: "INVALID_DEVICE" });
      }

      const url = new URL(`${config.url}/rest/v1/commands`);
      url.searchParams.set("select", fields.join(","));
      url.searchParams.set("device_id", `eq.${deviceId}`);
      url.searchParams.set("order", "created_at.desc,id.desc");
      url.searchParams.set("limit", "50");

      const loaded = await send(url, {
        headers: {
          apikey: config.serviceKey,
          Authorization: `Bearer ${config.serviceKey}`,
        },
        redirect: "error",
        signal: AbortSignal.timeout(10_000),
      });

      if (!loaded.ok) {
        return reply(503, { code: "HISTORY_READ_FAILED" });
      }

      const rows = await loaded.json();

      if (
        !Array.isArray(rows) ||
        rows.length > 50 ||
        rows.some((row) =>
          !row ||
          typeof row !== "object" ||
          Array.isArray(row) ||
          row.device_id !== deviceId
        )
      ) {
        return reply(502, { code: "INVALID_HISTORY_RESPONSE" });
      }

      // Auch bei zusätzlichen Datenbankfeldern nur erlaubte Felder ausgeben.
      const commands = rows.map((row) =>
        Object.fromEntries(
          fields.map((field) => [field, row[field] ?? null]),
        )
      );

      return reply(200, { success: true, commands });
    } catch {
      // Keine Sitzungstokens oder Datenbankantworten protokollieren.
      return reply(503, { code: "HISTORY_UNAVAILABLE" });
    }
  };
}

if (import.meta.main) {
  Deno.serve(
    createCommandHistoryHandler({
      url: Deno.env.get("SUPABASE_URL") ?? "",
      serviceKey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
      ownerUserId: Deno.env.get("IKI_OWNER_USER_ID") ?? "",
      origin: Deno.env.get("IKI_DASHBOARD_ORIGIN") ??
        "https://iki-anti-theft.web.app",
    }),
  );
}
