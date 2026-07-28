// Supabase Edge Function: daily-report
// Wird täglich um 03:00 Uhr via cron-job.org aufgerufen (siehe docs/SETUP.md).
// Liest Standortdaten der letzten 24h, erstellt Bericht, schickt Push ans Dashboard.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

Deno.serve(async (req) => {
  // Einfacher Auth-Check: nur Aufrufe mit Service-Role-Key erlaubt
  const authHeader = req.headers.get("Authorization") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  if (!authHeader.includes(serviceKey)) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401 });
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    serviceKey
  );

  const DEVICE_ID = "phone-1";
  const now  = Date.now();
  const since = now - 24 * 60 * 60 * 1000;

  // 1. Standortpunkte der letzten 24h laden
  const { data: locations, error } = await supabase
    .from("locations")
    .select("lat, lng, timestamp")
    .eq("device_id", DEVICE_ID)
    .gte("timestamp", since)
    .order("timestamp", { ascending: true });

  if (error) {
    console.error("DB-Fehler:", error);
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  }

  const count = locations?.length ?? 0;
  let title = "📍 Tagesbericht";
  let body  = "Heute keine Standortdaten empfangen.";
  let mapsUrl: string | null = null;

  if (count > 0) {
    const first = locations![0];
    const last  = locations![locations!.length - 1];

    const fmt = (ts: number) => new Date(ts).toLocaleTimeString("de-DE", {
      hour: "2-digit", minute: "2-digit", timeZone: "Europe/Berlin"
    });

    mapsUrl = buildMapsUrl(locations!);
    title   = `📍 Tagesbericht – ${count} Standorte`;
    body    = `Erster: ${fmt(first.timestamp)} · Letzter: ${fmt(last.timestamp)} Uhr`;

    // Top-App-Nutzung laden
    const yesterday = new Date(first.timestamp).toISOString().split("T")[0];
    const { data: usageData } = await supabase
      .from("usage_logs")
      .select("app_name, app_package, total_time_ms")
      .eq("device_id", DEVICE_ID)
      .eq("date", yesterday)
      .order("total_time_ms", { ascending: false })
      .limit(5);

    const topApps = usageData?.map(u => {
      const mins = Math.round(u.total_time_ms / 60000);
      return `${u.app_name || u.app_package} ${mins}Min`;
    }).join(" · ") ?? "";

    if (topApps) body += `\n📱 ${topApps}`;

    // Bericht in DB speichern
    await supabase.from("reports").insert({
      date: new Date().toISOString().split("T")[0],
      device_id: DEVICE_ID,
      location_count: count,
      first_timestamp: first.timestamp,
      last_timestamp: last.timestamp,
      first_lat: first.lat,
      first_lng: first.lng,
      last_lat: last.lat,
      last_lng: last.lng,
      maps_url: mapsUrl,
    });
  }

  // 2. Web-Push an Dashboard (Handy 1) senden
  const { data: ownerData } = await supabase
    .from("owner")
    .select("fcm_token")
    .eq("id", "dashboard")
    .single();

  if (ownerData?.fcm_token) {
    try {
      const accessToken = await getFcmAccessToken();
      const projectId   = Deno.env.get("FIREBASE_PROJECT_ID")!;

      await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: ownerData.fcm_token,
            notification: { title, body },
            webpush: {
              notification: { title, body, requireInteraction: true },
              fcm_options: mapsUrl ? { link: mapsUrl } : {},
            },
          },
        }),
      });
    } catch (err) {
      console.error("FCM-Fehler:", err);
    }
  } else {
    console.warn("Kein Owner-FCM-Token – Push übersprungen");
  }

  return new Response(JSON.stringify({ success: true, count }), {
    headers: { "Content-Type": "application/json" },
  });
});

// ── Hilfsfunktionen ───────────────────────────────────────────────────────────

function buildMapsUrl(locs: { lat: number; lng: number }[]): string {
  if (locs.length === 1) return `https://www.google.com/maps?q=${locs[0].lat},${locs[0].lng}`;

  const max  = 10;
  const step = Math.max(1, Math.floor(locs.length / max));
  const pts: typeof locs = [];
  for (let i = 0; i < locs.length; i += step) {
    pts.push(locs[i]);
    if (pts.length >= max) break;
  }
  if (pts[pts.length - 1] !== locs[locs.length - 1]) pts.push(locs[locs.length - 1]);

  const origin      = `${pts[0].lat},${pts[0].lng}`;
  const destination = `${pts[pts.length - 1].lat},${pts[pts.length - 1].lng}`;
  const waypoints   = pts.slice(1, -1).map(p => `${p.lat},${p.lng}`).join("|");
  let url = `https://www.google.com/maps/dir/${origin}/${destination}`;
  if (waypoints) url += `/@?api=1&waypoints=${encodeURIComponent(waypoints)}`;
  return url;
}

async function getFcmAccessToken(): Promise<string> {
  const sa  = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!);
  const now = Math.floor(Date.now() / 1000);

  const header  = urlB64(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = urlB64(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now, exp: now + 3600,
  }));

  const sigInput = `${header}.${payload}`;
  const key = await crypto.subtle.importKey("pkcs8",
    Uint8Array.from(atob(sa.private_key
      .replace(/-----[^-]+-----/g, "").replace(/\s/g, "")),
      c => c.charCodeAt(0)),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);

  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key,
    new TextEncoder().encode(sigInput));
  const jwt = `${sigInput}.${urlB64Bytes(new Uint8Array(sig))}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });
  const { access_token } = await res.json();
  return access_token;
}

function urlB64(s: string) { return urlB64Bytes(new TextEncoder().encode(s)); }
function urlB64Bytes(b: Uint8Array) {
  return btoa(String.fromCharCode(...b)).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"");
}
