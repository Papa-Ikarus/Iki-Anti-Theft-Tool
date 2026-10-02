import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type AckStatus = "received" | "running" | "success" | "error" | "timeout";

const ranks: Record<string, number> = {
  pending: 0,
  sent: 1,
  received: 2,
  running: 3,
  success: 4,
  error: 4,
  timeout: 4,
};

function reply(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    },
  });
}

export async function handleCommandAck(req: Request): Promise<Response> {
  if (req.method !== "POST") {
    return reply(405, { code: "METHOD_NOT_ALLOWED" });
  }

  const token = req.headers.get("X-Command-Ack-Token") ?? "";

  if (!/^[0-9a-f]{64}$/.test(token)) {
    return reply(401, { code: "ACK_UNAUTHORIZED" });
  }

  let payload: unknown;

  try {
    payload = await req.json();
  } catch {
    return reply(400, { code: "INVALID_JSON" });
  }

  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return reply(400, { code: "INVALID_ACK" });
  }

  const { commandId, deviceId, status } = payload as Record<string, unknown>;

  const allowedStatuses = new Set([
    "received",
    "running",
    "success",
    "error",
    "timeout",
  ]);

  if (
    typeof commandId !== "string" ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
      .test(commandId) ||
    typeof deviceId !== "string" ||
    !deviceId.trim() ||
    deviceId.length > 128 ||
    typeof status !== "string" ||
    !allowedStatuses.has(status)
  ) {
    return reply(400, { code: "INVALID_ACK" });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

  if (!url || !key) {
    return reply(503, { code: "ACK_UNAVAILABLE" });
  }

  try {
    const tokenHash = Array.from(
      new Uint8Array(
        await crypto.subtle.digest(
          "SHA-256",
          new TextEncoder().encode(token),
        ),
      ),
      (byte) => byte.toString(16).padStart(2, "0"),
    ).join("");

    const supabase = createClient(url, key, {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
      },
    });

    const now = new Date().toISOString();

    // Token autorisiert ausschließlich diesen Befehl.
    const { data: command, error: readError } = await supabase
      .from("commands")
      .select("status")
      .eq("id", commandId)
      .eq("device_id", deviceId)
      .eq("ack_token_hash", tokenHash)
      .gt("ack_token_expires_at", now)
      .maybeSingle();

    if (readError) {
      return reply(503, { code: "ACK_READ_FAILED" });
    }

    if (!command) {
      return reply(401, { code: "ACK_UNAUTHORIZED" });
    }

    const currentStatus = command.status as string;
    const nextStatus = status as AckStatus;

    if (!(currentStatus in ranks)) {
      return reply(409, { code: "INVALID_CURRENT_STATUS" });
    }

    // Wiederholte und verspätete Zwischenmeldungen verändern nichts.
    if (
      currentStatus === nextStatus ||
      (
        ranks[nextStatus] < ranks[currentStatus] &&
        ranks[nextStatus] < 4
      )
    ) {
      return reply(200, {
        success: true,
        applied: false,
        status: currentStatus,
      });
    }

    // Ein Endstatus darf nicht durch einen anderen ersetzt werden.
    if (ranks[currentStatus] === 4) {
      return reply(409, { code: "COMMAND_ALREADY_FINISHED" });
    }

    const update: Record<string, string> = {
      status: nextStatus,
      updated_at: now,
    };

    if (nextStatus === "received") {
      update.received_at = now;
    } else if (nextStatus === "running") {
      update.started_at = now;
    } else {
      update.finished_at = now;
    }

    // Vergleich und Änderung erfolgen gemeinsam in der Datenbank.
    const { data: updated, error: updateError } = await supabase
      .from("commands")
      .update(update)
      .eq("id", commandId)
      .eq("device_id", deviceId)
      .eq("ack_token_hash", tokenHash)
      .gt("ack_token_expires_at", new Date().toISOString())
      .eq("status", currentStatus)
      .select("status");

    if (updateError) {
      return reply(503, { code: "ACK_UPDATE_FAILED" });
    }

    if (!updated || updated.length === 0) {
      // Zwischenzeitliche Statusänderung: Android muss erneut versuchen.
      return reply(409, { code: "ACK_RETRY" });
    }

    return reply(200, {
      success: true,
      applied: true,
      status: updated[0].status,
    });
  } catch {
    // Keine Tokens oder Anfrageinhalte protokollieren.
    return reply(503, { code: "ACK_UNAVAILABLE" });
  }
}

if (import.meta.main) {
  Deno.serve(handleCommandAck);
}
