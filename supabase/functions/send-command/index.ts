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
  if (req.method === "OPTIONS") return new Response(null, { headers: corsHeaders });

  try {
    const { deviceId, command } = await req.json();
    if (!deviceId || !command) {
      return new Response(JSON.stringify({ error: "deviceId und command erforderlich" }),
        { status: 400, headers: corsHeaders });
    }

    // FCM-Token aus Supabase laden
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const { data, error } = await supabase
      .from("devices")
      .select("fcm_token")
      .eq("id", deviceId)
      .single();

    if (error || !data?.fcm_token) {
      return new Response(JSON.stringify({ error: "Gerät nicht gefunden" }),
        { status: 404, headers: corsHeaders });
    }

    // FCM v1 Push senden
const accessToken = await getFcmAccessToken();
const projectId = Deno.env.get("FIREBASE_PROJECT_ID")!;

console.log("FCM PROJECT:", projectId);

console.log("FCM SEND:", {
  deviceId,
  tokenPrefix: data.fcm_token?.substring(0, 20),
  tokenLength: data.fcm_token?.length,
  command
});

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
        token: data.fcm_token,
        data: { command },
        android: { priority: "HIGH" },
      },
    }),
  }
);

console.log("FCM SEND:", {
  deviceId,
  tokenPrefix: data.fcm_token?.substring(0, 20),
  tokenLength: data.fcm_token?.length,
  command
});



    if (!fcmRes.ok) {
      const err = await fcmRes.text();
      throw new Error(`FCM-Fehler: ${err}`);
    }

    return new Response(JSON.stringify({ success: true }), { headers: corsHeaders });

  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }),
      { status: 500, headers: corsHeaders });
  }
});

// ── FCM v1 OAuth2 Access Token via Service Account JWT ────────────────────────

async function getFcmAccessToken(): Promise<string> {
  const sa = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!);
  const now = Math.floor(Date.now() / 1000);

  const header  = urlBase64(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = urlBase64(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));

  const sigInput = `${header}.${payload}`;
  const key = await importPrivateKey(sa.private_key);
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5", key,
    new TextEncoder().encode(sigInput)
  );
  const jwt = `${sigInput}.${urlBase64Bytes(new Uint8Array(sig))}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });

  const { access_token } = await res.json();
  return access_token;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const pemBody = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const der = Uint8Array.from(atob(pemBody), c => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8", der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false, ["sign"]
  );
}

function urlBase64(str: string): string {
  return urlBase64Bytes(new TextEncoder().encode(str));
}

function urlBase64Bytes(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
