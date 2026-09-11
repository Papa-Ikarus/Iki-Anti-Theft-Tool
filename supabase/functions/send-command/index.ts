// Supabase Edge Function: send-command
// Wird vom Dashboard aufgerufen → holt FCM-Token aus DB → schickt Push ans Zweithandy
//
// Secrets (Supabase Dashboard → Edge Functions → Secrets):
//   SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY  (automatisch verfügbar)
//   FIREBASE_PROJECT_ID                       (aus Firebase Console)
//   FIREBASE_SERVICE_ACCOUNT                  (JSON, aus Firebase Console)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const { deviceId, command } = await req.json();

    if (!deviceId || !command) {
      return new Response(
        JSON.stringify({
          error: "deviceId und command erforderlich",
        }),
        {
          status: 400,
          headers: corsHeaders,
        }
      );
    }

    // Supabase Service-Role-Client
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
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
        }
      );
    }

    const fcmToken = data.fcm_token;

    // FCM v1 Access Token holen
    const accessToken = await getFcmAccessToken();
    const projectId = Deno.env.get("FIREBASE_PROJECT_ID")!;

    console.log("FCM PROJECT:", projectId);

    console.log("FCM SEND:", {
      deviceId,
      tokenPrefix: fcmToken.substring(0, 20),
      tokenLength: fcmToken.length,
      command,
    });

    // FCM v1 Push senden
    const fcmRes = await fetch(
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
            },
            android: {
              priority: "HIGH",
            },
          },
        }),
      }
    );

    // Erfolgreich zugestellt
    if (fcmRes.ok) {
      const result = await fcmRes.text();

      console.log("FCM erfolgreich:", {
        deviceId,
        command,
        response: result,
      });

      return new Response(
        JSON.stringify({
          success: true,
          deviceId,
          command,
        }),
        {
          headers: corsHeaders,
        }
      );
    }

    // FCM-Fehler auslesen
    const errText = await fcmRes.text();

    console.error("FCM HTTP-Fehler:", {
      deviceId,
      command,
      status: fcmRes.status,
      response: errText,
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
    const isUnregistered =
      fcmError?.error?.details?.some(
        (detail: {
  "@type"?: string;
  errorCode?: string;
}) =>
          detail?.["@type"] ===
            "type.googleapis.com/google.firebase.fcm.v1.FcmError" &&
          detail?.errorCode === "UNREGISTERED"
      ) === true;

    if (isUnregistered) {
      console.warn(
        `FCM-Token ungültig (UNREGISTERED) – Token wird für ${deviceId} entfernt`
      );

      const { error: clearTokenError } = await supabase
        .from("devices")
        .update({
          fcm_token: null,
        })
        .eq("id", deviceId);

      if (clearTokenError) {
        console.error(
          "FCM-Token konnte nicht aus devices entfernt werden:",
          clearTokenError
        );

        return new Response(
          JSON.stringify({
            error: "FCM-Token ungültig und konnte nicht bereinigt werden",
            code: "FCM_TOKEN_INVALID_CLEANUP_FAILED",
            deviceId,
          }),
          {
            status: 500,
            headers: corsHeaders,
          }
        );
      }

      console.log(
        `FCM-Token erfolgreich entfernt – Gerät ${deviceId} bleibt erhalten`
      );

      return new Response(
        JSON.stringify({
          error: "FCM-Token ist nicht mehr gültig",
          code: "FCM_TOKEN_INVALID",
          deviceId,
        }),
        {
          status: 410,
          headers: corsHeaders,
        }
      );
    }

    // Andere FCM-Fehler normal zurückgeben
    return new Response(
      JSON.stringify({
        error: `FCM-Fehler: ${errText}`,
        code: "FCM_SEND_FAILED",
        deviceId,
      }),
      {
        status: 502,
        headers: corsHeaders,
      }
    );
  } catch (err) {
    const message =
      err instanceof Error ? err.message : String(err);

    console.error("send-command Fehler:", message);

    return new Response(
      JSON.stringify({
        error: message,
        code: "SEND_COMMAND_FAILED",
      }),
      {
        status: 500,
        headers: corsHeaders,
      }
    );
  }
});

// ── FCM v1 OAuth2 Access Token via Service Account JWT ────────────────────────

async function getFcmAccessToken(): Promise<string> {
  const sa = JSON.parse(
    Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!
  );

  const now = Math.floor(Date.now() / 1000);

  const header = urlBase64(
    JSON.stringify({
      alg: "RS256",
      typ: "JWT",
    })
  );

  const payload = urlBase64(
    JSON.stringify({
      iss: sa.client_email,
      scope:
        "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    })
  );

  const sigInput = `${header}.${payload}`;

  const key = await importPrivateKey(sa.private_key);

  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(sigInput)
  );

  const jwt =
    `${sigInput}.${urlBase64Bytes(new Uint8Array(sig))}`;

  const res = await fetch(
    "https://oauth2.googleapis.com/token",
    {
      method: "POST",
      headers: {
        "Content-Type":
          "application/x-www-form-urlencoded",
      },
      body:
        `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
    }
  );

  if (!res.ok) {
    const errorText = await res.text();

    throw new Error(
      `FCM OAuth-Fehler ${res.status}: ${errorText}`
    );
  }

  const { access_token } = await res.json();

  if (!access_token) {
    throw new Error(
      "FCM OAuth-Antwort enthält kein access_token"
    );
  }

  return access_token;
}

function importPrivateKey(
  pem: string
): Promise<CryptoKey> {
  const pemBody = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");

  const der = Uint8Array.from(
    atob(pemBody),
    (c) => c.charCodeAt(0)
  );

  return crypto.subtle.importKey(
    "pkcs8",
    der,
    {
      name: "RSASSA-PKCS1-v1_5",
      hash: "SHA-256",
    },
    false,
    ["sign"]
  );
}

function urlBase64(str: string): string {
  return urlBase64Bytes(
    new TextEncoder().encode(str)
  );
}

function urlBase64Bytes(bytes: Uint8Array): string {
  return btoa(
    String.fromCharCode(...bytes)
  )
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

