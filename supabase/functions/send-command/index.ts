// Supabase Edge Function: send-command
// Wird vom Dashboard aufgerufen → holt FCM-Token aus DB → schickt Push ans Zweithandy
//
// Secrets (Supabase Dashboard → Edge Functions → Secrets):
//   SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY  (automatisch verfügbar)
//   FIREBASE_PROJECT_ID                       (aus Firebase Console)
//   FIREBASE_SERVICE_ACCOUNT                  (JSON, aus Firebase Console)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const dashboardOrigin = Deno.env.get("IKI_DASHBOARD_ORIGIN") ??
  "https://iki-anti-theft.web.app";

const corsHeaders = {
  "Access-Control-Allow-Origin": dashboardOrigin,
  "Access-Control-Allow-Headers":
    "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Vary": "Origin",
};

Deno.serve(async (req) => {
  const origin = req.headers.get("origin");

  if (origin && origin !== dashboardOrigin) {
    return new Response(
      JSON.stringify({ error: "Origin not allowed" }),
      {
        status: 403,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json",
        },
      },
    );
  }
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: corsHeaders,
    });
  }

  if (req.method !== "POST") {
    return new Response(
      JSON.stringify({ error: "POST required" }),
      {
        status: 405,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json",
        },
      },
    );
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const ownerUserId = Deno.env.get("IKI_OWNER_USER_ID") ?? "";

  if (!supabaseUrl || !serviceKey || !ownerUserId) {
    return new Response(
      JSON.stringify({ error: "Command service not configured" }),
      {
        status: 503,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json",
        },
      },
    );
  }

  const authorization = req.headers.get("authorization") ?? "";

  if (!authorization.startsWith("Bearer ")) {
    return new Response(
      JSON.stringify({ error: "Sign in required" }),
      {
        status: 401,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json",
        },
      },
    );
  }

  try {
    const verified = await fetch(
      `${supabaseUrl}/auth/v1/user`,
      {
        headers: {
          "apikey": serviceKey,
          "Authorization": authorization,
        },
      },
    );

    if (!verified.ok) {
      return new Response(
        JSON.stringify({ error: "Invalid session" }),
        {
          status: 401,
          headers: {
            ...corsHeaders,
            "Content-Type": "application/json",
          },
        },
      );
    }

    const user = await verified.json();

    if (
      user.id !== ownerUserId ||
      user.is_anonymous === true
    ) {
      return new Response(
        JSON.stringify({ error: "Owner access required" }),
        {
          status: 403,
          headers: {
            ...corsHeaders,
            "Content-Type": "application/json",
          },
        },
      );
    }

    const { deviceId, command } = await req.json();

    const validCommands = new Set([
      "photo",
      "audio",
      "location",
      "usage",
    ]);

    if (
      typeof deviceId !== "string" ||
      typeof command !== "string" ||
      !deviceId.trim() ||
      !validCommands.has(command)
    ) {
      return new Response(
        JSON.stringify({
          error: "Ungültiges Gerät oder ungültiger Befehl",
          code: "INVALID_COMMAND",
        }),
        {
          status: 400,
          headers: corsHeaders,
        },
      );
    }

    if (!deviceId || !command) {
      return new Response(
        JSON.stringify({
          error: "deviceId und command erforderlich",
        }),
        {
          status: 400,
          headers: corsHeaders,
        },
      );
    }

    // Supabase Service-Role-Client
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // FCM-Token aus Supabase laden
    const { data, error } = await supabase
      .from("devices")
      .select("fcm_token")
      .eq("id", deviceId)
      .single();

    if (error || !data?.fcm_token) {
      return new Response(
        JSON.stringify({
          error: "Gerät nicht gefunden oder kein FCM-Token vorhanden",
          code: "NO_FCM_TOKEN",
        }),
        {
          status: 404,
          headers: corsHeaders,
        },
      );
    }

    const fcmToken = data.fcm_token;

    const projectId = Deno.env.get("FIREBASE_PROJECT_ID")!;

    console.log("FCM PROJECT:", projectId);

    console.log("FCM SEND vorbereiten:", {
      deviceId,
      command,
    });

    // Server-seitige Command-ID erzeugen und Command vor dem Versand speichern
    const commandId = crypto.randomUUID();
    // Geheimen Rückmeldetoken erzeugen; nur den Hash speichern.
    const ackToken = Array.from(
      crypto.getRandomValues(new Uint8Array(32)),
      (byte) => byte.toString(16).padStart(2, "0"),
    ).join("");

    const ackTokenHash = Array.from(
      new Uint8Array(
        await crypto.subtle.digest(
          "SHA-256",
          new TextEncoder().encode(ackToken),
        ),
      ),
      (byte) => byte.toString(16).padStart(2, "0"),
    ).join("");

    // Zunächst 24 Stunden für Zustellung und spätere Rückmeldungen.
    const ackTokenExpiresAt = new Date(
      Date.now() + 24 * 60 * 60 * 1000,
    ).toISOString();

    const { error: commandInsertError } = await supabase
      .from("commands")
      .insert({
        id: commandId,
        device_id: deviceId,
        command,
        status: "pending",
        ack_token_hash: ackTokenHash,
        ack_token_expires_at: ackTokenExpiresAt,
      });

    if (commandInsertError) {
      console.error("Command konnte nicht gespeichert werden:", {
        deviceId,
        command,
        code: commandInsertError.code,
      });

      return new Response(
        JSON.stringify({
          error: "Befehl konnte nicht vorbereitet werden",
          code: "COMMAND_CREATE_FAILED",
        }),
        {
          status: 500,
          headers: corsHeaders,
        },
      );
    }

    // FCM v1 Access Token holen
    let accessToken: string;

    try {
      accessToken = await getFcmAccessToken();
    } catch (err) {
      const failedAt = new Date().toISOString();

      const { error: commandUpdateError } = await supabase
        .from("commands")
        .update({
          status: "error",
          finished_at: failedAt,
          updated_at: failedAt,
          error_code: "FCM_AUTH_FAILED",
          error_message: "FCM-Authentifizierung fehlgeschlagen",
        })
        .eq("id", commandId)
        .eq("status", "pending");

      if (commandUpdateError) {
        console.error("Command-Status konnte nicht auf error gesetzt werden:", {
          commandId,
          code: commandUpdateError.code,
        });
      }

      console.error("FCM-Authentifizierung fehlgeschlagen:", {
        commandId,
        deviceId,
        error: err instanceof Error ? err.message : String(err),
      });

      return new Response(
        JSON.stringify({
          error: "FCM-Authentifizierung fehlgeschlagen",
          code: "FCM_AUTH_FAILED",
          deviceId,
          commandId,
        }),
        {
          status: 502,
          headers: corsHeaders,
        },
      );
    }

    // FCM v1 Push senden
    let fcmRes: Response;

    try {
      fcmRes = await fetch(
        `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
        {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token: fcmToken,
              data: {
                command,
                command_id: commandId,
                ack_token: ackToken,
              },
              android: {
                priority: "HIGH",
              },
            },
          }),
        },
      );
    } catch (err) {
      const failedAt = new Date().toISOString();

      const { error: commandUpdateError } = await supabase
        .from("commands")
        .update({
          status: "error",
          finished_at: failedAt,
          updated_at: failedAt,
          error_code: "FCM_NETWORK_FAILED",
          error_message: "FCM-Netzwerkfehler",
        })
        .eq("id", commandId)
        .eq("status", "pending");

      if (commandUpdateError) {
        console.error("Command-Status konnte nicht auf error gesetzt werden:", {
          commandId,
          code: commandUpdateError.code,
        });
      }

      console.error("FCM-Netzwerkfehler:", {
        commandId,
        deviceId,
        error: err instanceof Error ? err.message : String(err),
      });

      return new Response(
        JSON.stringify({
          error: "FCM-Versand fehlgeschlagen",
          code: "FCM_NETWORK_FAILED",
          deviceId,
          commandId,
        }),
        {
          status: 502,
          headers: corsHeaders,
        },
      );
    }

    // Erfolgreich zugestellt
    if (fcmRes.ok) {
      await fcmRes.text();

      const sentAt = new Date().toISOString();

      const { error: commandUpdateError } = await supabase
        .from("commands")
        .update({
          status: "sent",
          sent_at: sentAt,
          updated_at: sentAt,
        })
        .eq("id", commandId)
        .eq("status", "pending");

      if (commandUpdateError) {
        console.error("Command-Status konnte nicht auf sent gesetzt werden:", {
          commandId,
          code: commandUpdateError.code,
        });
      }

      console.log("FCM erfolgreich:", {
        commandId,
        deviceId,
        command,
      });

      return new Response(
        JSON.stringify({
          success: true,
          commandId,
          deviceId,
          command,
          trackingUpdated: !commandUpdateError,
        }),
        {
          headers: corsHeaders,
        },
      );
    }

    // FCM-Fehler auslesen
    const errText = await fcmRes.text();
    const failedAt = new Date().toISOString();

    const { error: commandErrorUpdateError } = await supabase
      .from("commands")
      .update({
        status: "error",
        finished_at: failedAt,
        updated_at: failedAt,
        error_code: `FCM_HTTP_${fcmRes.status}`,
        error_message: "FCM-Versand fehlgeschlagen",
      })
      .eq("id", commandId)
      .eq("status", "pending");

    if (commandErrorUpdateError) {
      console.error("Command-Status konnte nicht auf error gesetzt werden:", {
        commandId,
        code: commandErrorUpdateError.code,
      });
    }

    console.error("FCM HTTP-Fehler:", {
      commandId,
      deviceId,
      command,
      status: fcmRes.status,
    });

    let fcmError: {
      error?: {
        details?: Array<{
          "@type"?: string;
          errorCode?: string;
        }>;
      };
    } | null = null;

    try {
      fcmError = JSON.parse(errText);
    } catch {
      // Antwort war kein JSON
    }

    /*
     * FCM liefert bei einem dauerhaft ungültigen Token typischerweise:
     *
     * HTTP 404
     * error.status = "NOT_FOUND"
     * details[].errorCode = "UNREGISTERED"
     *
     * In diesem Fall darf das Gerät NICHT gelöscht werden.
     * Wir entfernen lediglich den ungültigen FCM-Token.
     */
    const isUnregistered = fcmError?.error?.details?.some(
      (detail: {
        "@type"?: string;
        errorCode?: string;
      }) =>
        detail?.["@type"] ===
          "type.googleapis.com/google.firebase.fcm.v1.FcmError" &&
        detail?.errorCode === "UNREGISTERED",
    ) === true;

    if (isUnregistered) {
      console.warn("FCM-Token ist nicht mehr registriert:", {
        commandId,
        deviceId,
      });

      return new Response(
        JSON.stringify({
          error: "FCM-Token ist nicht mehr gültig",
          code: "FCM_TOKEN_INVALID",
          deviceId,
          commandId,
        }),
        {
          status: 410,
          headers: corsHeaders,
        },
      );
    }

    // Andere FCM-Fehler normal zurückgeben
    return new Response(
      JSON.stringify({
        error: "FCM-Versand fehlgeschlagen",
        code: "FCM_SEND_FAILED",
        deviceId,
        commandId,
      }),
      {
        status: 502,
        headers: corsHeaders,
      },
    );
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);

    console.error("send-command Fehler:", message);

    return new Response(
      JSON.stringify({
        error: "Interner Fehler beim Senden des Befehls",
        code: "SEND_COMMAND_FAILED",
      }),
      {
        status: 500,
        headers: corsHeaders,
      },
    );
  }
});

// ── FCM v1 OAuth2 Access Token via Service Account JWT ────────────────────────

async function getFcmAccessToken(): Promise<string> {
  const sa = JSON.parse(
    Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!,
  );

  const now = Math.floor(Date.now() / 1000);

  const header = urlBase64(
    JSON.stringify({
      alg: "RS256",
      typ: "JWT",
    }),
  );

  const payload = urlBase64(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  );

  const sigInput = `${header}.${payload}`;

  const key = await importPrivateKey(sa.private_key);

  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(sigInput),
  );

  const jwt = `${sigInput}.${urlBase64Bytes(new Uint8Array(sig))}`;

  const res = await fetch(
    "https://oauth2.googleapis.com/token",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body:
        `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
    },
  );

  if (!res.ok) {
    const errorText = await res.text();

    throw new Error(
      `FCM OAuth-Fehler ${res.status}: ${errorText}`,
    );
  }

  const { access_token } = await res.json();

  if (!access_token) {
    throw new Error(
      "FCM OAuth-Antwort enthält kein access_token",
    );
  }

  return access_token;
}

function importPrivateKey(
  pem: string,
): Promise<CryptoKey> {
  const pemBody = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");

  const der = Uint8Array.from(
    atob(pemBody),
    (c) => c.charCodeAt(0),
  );

  return crypto.subtle.importKey(
    "pkcs8",
    der,
    {
      name: "RSASSA-PKCS1-v1_5",
      hash: "SHA-256",
    },
    false,
    ["sign"],
  );
}

function urlBase64(str: string): string {
  return urlBase64Bytes(
    new TextEncoder().encode(str),
  );
}

function urlBase64Bytes(bytes: Uint8Array): string {
  return btoa(
    String.fromCharCode(...bytes),
  )
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}
