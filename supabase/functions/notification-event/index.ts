import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

const DEVICE_TOKEN_REGEX = /^[A-Za-z0-9_-]{43}$/;
const DEVICE_ID_REGEX = /^device-[A-Za-z0-9_-]{1,120}$/;

const MAX_APP_NAME_LENGTH = 200;
const MAX_PACKAGE_NAME_LENGTH = 255;
const MAX_NOTIFICATION_KEY_LENGTH = 1000;

type NotificationEventBody = {
  deviceId?: unknown;
  appName?: unknown;
  packageName?: unknown;
  eventType?: unknown;
  notificationId?: unknown;
  notificationKey?: unknown;
  eventTimestamp?: unknown;
  postTimestamp?: unknown;
};

function response(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
    },
  });
}

async function sha256Hex(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const hash = await crypto.subtle.digest("SHA-256", bytes);

  return Array.from(new Uint8Array(hash))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return response(405, {
      error: "Method not allowed",
    });
  }

  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    console.error("notification-event: Supabase configuration missing");

    return response(500, {
      error: "Server configuration error",
    });
  }

  const deviceToken = req.headers.get("X-Device-Token");

  if (!deviceToken || !DEVICE_TOKEN_REGEX.test(deviceToken)) {
    return response(401, {
      error: "Unauthorized",
    });
  }

  let body: NotificationEventBody;

  try {
    body = await req.json();
  } catch {
    return response(400, {
      error: "Invalid JSON",
    });
  }

  const {
    deviceId,
    appName,
    packageName,
    eventType,
    notificationId,
    notificationKey,
    eventTimestamp,
    postTimestamp,
  } = body;

  if (
    typeof deviceId !== "string" ||
    !DEVICE_ID_REGEX.test(deviceId)
  ) {
    return response(400, {
      error: "Invalid deviceId",
    });
  }

  if (
    typeof appName !== "string" ||
    appName.length < 1 ||
    appName.length > MAX_APP_NAME_LENGTH
  ) {
    return response(400, {
      error: "Invalid appName",
    });
  }

  if (
    typeof packageName !== "string" ||
    packageName.length < 1 ||
    packageName.length > MAX_PACKAGE_NAME_LENGTH
  ) {
    return response(400, {
      error: "Invalid packageName",
    });
  }

  if (
    eventType !== "posted" &&
    eventType !== "removed"
  ) {
    return response(400, {
      error: "Invalid eventType",
    });
  }

  if (
    notificationId !== undefined &&
    notificationId !== null &&
    (
      typeof notificationId !== "number" ||
      !Number.isInteger(notificationId)
    )
  ) {
    return response(400, {
      error: "Invalid notificationId",
    });
  }

  if (
    notificationKey !== undefined &&
    notificationKey !== null &&
    (
      typeof notificationKey !== "string" ||
      notificationKey.length > MAX_NOTIFICATION_KEY_LENGTH
    )
  ) {
    return response(400, {
      error: "Invalid notificationKey",
    });
  }

  if (
    typeof eventTimestamp !== "number" ||
    !Number.isSafeInteger(eventTimestamp) ||
    eventTimestamp <= 0
  ) {
    return response(400, {
      error: "Invalid eventTimestamp",
    });
  }

  if (
    postTimestamp !== undefined &&
    postTimestamp !== null &&
    (
      typeof postTimestamp !== "number" ||
      !Number.isSafeInteger(postTimestamp) ||
      postTimestamp <= 0
    )
  ) {
    return response(400, {
      error: "Invalid postTimestamp",
    });
  }

  const tokenHash = await sha256Hex(deviceToken);

  const supabase = createClient(
    SUPABASE_URL,
    SUPABASE_SERVICE_ROLE_KEY,
    {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
      },
    },
  );

  const {
    data: device,
    error: deviceError,
  } = await supabase
    .from("devices")
    .select("id")
    .eq("id", deviceId)
    .eq("upload_token_hash", tokenHash)
    .maybeSingle();

  if (deviceError) {
    console.error(
      "notification-event: device authentication query failed",
      deviceError.message,
    );

    return response(500, {
      error: "Internal server error",
    });
  }

  if (!device) {
    return response(401, {
      error: "Unauthorized",
    });
  }

  const {
    error: insertError,
  } = await supabase
    .from("notification_events")
    .insert({
      device_id: deviceId,
      app_name: appName,
      package_name: packageName,
      event_type: eventType,
      notification_id: notificationId ?? null,
      notification_key: notificationKey ?? null,
      event_timestamp: eventTimestamp,
      post_timestamp: postTimestamp ?? null,
    });

  if (insertError) {
    console.error(
      "notification-event: insert failed",
      insertError.message,
    );

    return response(500, {
      error: "Internal server error",
    });
  }

  return response(200, {
    ok: true,
  });
});