// Supabase Edge Function: daily-report
//
// Wird täglich via cron-job.org aufgerufen.
// Verarbeitet alle registrierten Geräte aus der Tabelle "devices".
// Für jedes Gerät werden die Standortdaten der letzten 24 Stunden
// ausgewertet, ein Report gespeichert und das Dashboard per FCM informiert.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

Deno.serve(async (req) => {
  // ──────────────────────────────────────────────────────────────────────────
  // 1. Authentifizierung des externen Cron-Aufrufers
  // ──────────────────────────────────────────────────────────────────────────

  const authHeader = req.headers.get("Authorization") ?? "";
  const cronSecret = Deno.env.get("DAILY_REPORT_SECRET");

  if (!cronSecret) {
    console.error("DAILY_REPORT_SECRET ist nicht konfiguriert.");

    return new Response(
      JSON.stringify({ error: "Server authentication not configured" }),
      {
        status: 500,
        headers: { "Content-Type": "application/json" },
      },
    );
  }

  if (authHeader !== `Bearer ${cronSecret}`) {
    return new Response(
      JSON.stringify({ error: "Unauthorized" }),
      {
        status: 401,
        headers: { "Content-Type": "application/json" },
      },
    );
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 2. Supabase Client mit Service-Role-Key
  // ──────────────────────────────────────────────────────────────────────────

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

  if (!supabaseUrl || !serviceKey) {
    console.error("Supabase-Konfiguration fehlt.");

    return new Response(
      JSON.stringify({ error: "Supabase configuration missing" }),
      {
        status: 500,
        headers: { "Content-Type": "application/json" },
      },
    );
  }

  const supabase = createClient(supabaseUrl, serviceKey);

  // ──────────────────────────────────────────────────────────────────────────
  // 3. Zeitraum bestimmen
  // ──────────────────────────────────────────────────────────────────────────

  const now = Date.now();
  const since = now - 24 * 60 * 60 * 1000;

  // ──────────────────────────────────────────────────────────────────────────
  // 4. Alle registrierten Geräte laden
  // ──────────────────────────────────────────────────────────────────────────

  const { data: devices, error: devicesError } = await supabase
    .from("devices")
    .select("id")
    .order("created_at", { ascending: true });

  if (devicesError) {
    console.error("Fehler beim Laden der Geräte:", devicesError);

    return new Response(
      JSON.stringify({
        error: "Failed to load devices",
        details: devicesError.message,
      }),
      {
        status: 500,
        headers: { "Content-Type": "application/json" },
      },
    );
  }

  if (!devices || devices.length === 0) {
    console.warn("Keine registrierten Geräte gefunden.");

    return new Response(
      JSON.stringify({
        success: true,
        devices: 0,
        processed: 0,
        reports: 0,
      }),
      {
        headers: { "Content-Type": "application/json" },
      },
    );
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 5. Dashboard-FCM-Token laden
  // ──────────────────────────────────────────────────────────────────────────

  const { data: ownerData, error: ownerError } = await supabase
    .from("owner")
    .select("fcm_token")
    .eq("id", "dashboard")
    .single();

  if (ownerError) {
    console.warn("Dashboard-Owner konnte nicht geladen werden:", ownerError.message);
  }

  const dashboardFcmToken = ownerData?.fcm_token ?? null;

  if (!dashboardFcmToken) {
    console.warn("Kein Dashboard-FCM-Token – Push wird übersprungen.");
  }

  // Access Token nur einmal erzeugen und für alle Geräte wiederverwenden.
  let fcmAccessToken: string | null = null;
  let fcmTokenError: string | null = null;

  // ──────────────────────────────────────────────────────────────────────────
  // 6. Geräte einzeln verarbeiten
  // ──────────────────────────────────────────────────────────────────────────

  let processedDevices = 0;
  let reportsCreated = 0;
  let totalLocations = 0;

  const results: Array<{
    device_id: string;
    location_count: number;
    report_created: boolean;
    push_sent: boolean;
    error?: string;
  }> = [];

  for (const device of devices) {
    const deviceId = device.id;

    try {
      console.log(`Verarbeite Gerät: ${deviceId}`);

      // ──────────────────────────────────────────────────────────────────────
      // Standortdaten der letzten 24 Stunden
      // ──────────────────────────────────────────────────────────────────────

      const { data: locations, error: locationsError } = await supabase
        .from("locations")
        .select("lat, lng, timestamp")
        .eq("device_id", deviceId)
        .gte("timestamp", since)
        .order("timestamp", { ascending: true });

      if (locationsError) {
        throw new Error(
          `Locations konnten nicht geladen werden: ${locationsError.message}`,
        );
      }

      const count = locations?.length ?? 0;
      totalLocations += count;

      let title = `📍 Tagesbericht – ${deviceId}`;
      let body = "Heute keine Standortdaten empfangen.";
      let mapsUrl: string | null = null;
      let reportCreated = false;
      let pushSent = false;

      // ──────────────────────────────────────────────────────────────────────
      // Standortdaten vorhanden
      // ──────────────────────────────────────────────────────────────────────

      if (count > 0) {
        const first = locations![0];
        const last = locations![locations!.length - 1];

        const fmt = (ts: number) =>
          new Date(ts).toLocaleTimeString("de-DE", {
            hour: "2-digit",
            minute: "2-digit",
            timeZone: "Europe/Berlin",
          });

        mapsUrl = buildMapsUrl(locations!);

        title = `📍 Tagesbericht – ${deviceId} – ${count} Standorte`;
        body =
          `Erster: ${fmt(first.timestamp)} · Letzter: ${fmt(last.timestamp)} Uhr`;

        // ────────────────────────────────────────────────────────────────────
        // Top-App-Nutzung laden
        // ────────────────────────────────────────────────────────────────────

        const reportDate = new Date(first.timestamp)
          .toLocaleDateString("en-CA", {
            timeZone: "Europe/Berlin",
          });

        const { data: usageData, error: usageError } = await supabase
          .from("usage_logs")
          .select("app_name, app_package, total_time_ms")
          .eq("device_id", deviceId)
          .eq("date", reportDate)
          .order("total_time_ms", { ascending: false })
          .limit(5);

        if (usageError) {
          console.warn(
            `Usage Logs für ${deviceId} konnten nicht geladen werden:`,
            usageError.message,
          );
        }

        const topApps = usageData
          ?.map((u) => {
            const mins = Math.round(u.total_time_ms / 60000);
            return `${u.app_name || u.app_package} ${mins}Min`;
          })
          .join(" · ") ?? "";

        if (topApps) {
          body += `\n📱 ${topApps}`;
        }

        // ────────────────────────────────────────────────────────────────────
        // Report speichern
        // ────────────────────────────────────────────────────────────────────

        const reportDateForDb = new Date()
          .toLocaleDateString("en-CA", {
            timeZone: "Europe/Berlin",
          });

        const { error: reportError } = await supabase
         .from("reports")
         .upsert(
           {
             date: reportDateForDb,
             device_id: deviceId,
             location_count: count,
             first_timestamp: first.timestamp,
             last_timestamp: last.timestamp,
             first_lat: first.lat,
             first_lng: first.lng,
             last_lat: last.lat,
             last_lng: last.lng,
             maps_url: mapsUrl,
           },
           {
             onConflict: "date,device_id",
           },
         );
         

        if (reportError) {
          throw new Error(
            `Report konnte nicht gespeichert werden: ${reportError.message}`,
          );
        }

        reportCreated = true;
        reportsCreated++;
      }

      // ──────────────────────────────────────────────────────────────────────
      // Dashboard-Push
      // ──────────────────────────────────────────────────────────────────────

      if (dashboardFcmToken) {
        try {
          if (!fcmAccessToken && !fcmTokenError) {
            try {
              fcmAccessToken = await getFcmAccessToken();
            } catch (err) {
              fcmTokenError =
                err instanceof Error ? err.message : String(err);

              console.error(
                "FCM Access Token konnte nicht erstellt werden:",
                fcmTokenError,
              );
            }
          }

          if (fcmAccessToken) {
            const projectId = Deno.env.get("FIREBASE_PROJECT_ID");

            if (!projectId) {
              throw new Error("FIREBASE_PROJECT_ID ist nicht konfiguriert.");
            }

            const fcmResponse = await fetch(
              `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
              {
                method: "POST",
                headers: {
                  Authorization: `Bearer ${fcmAccessToken}`,
                  "Content-Type": "application/json",
                },
                body: JSON.stringify({
                  message: {
                    token: dashboardFcmToken,
                    notification: {
                      title,
                      body,
                    },
                    webpush: {
                      notification: {
                        title,
                        body,
                        requireInteraction: true,
                      },
                      fcm_options: mapsUrl
                        ? { link: mapsUrl }
                        : {},
                    },
                  },
                }),
              },
            );

            if (!fcmResponse.ok) {
              const fcmErrorText = await fcmResponse.text();

              throw new Error(
                `FCM HTTP ${fcmResponse.status}: ${fcmErrorText}`,
              );
            }

            pushSent = true;
            console.log(`FCM Push für ${deviceId} erfolgreich gesendet.`);
          }
        } catch (err) {
          console.error(
            `FCM-Fehler für ${deviceId}:`,
            err instanceof Error ? err.message : err,
          );
        }
      }

      processedDevices++;

      results.push({
        device_id: deviceId,
        location_count: count,
        report_created: reportCreated,
        push_sent: pushSent,
      });

      console.log(
        `Gerät ${deviceId}: ${count} Locations, Report=${reportCreated}, Push=${pushSent}`,
      );
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : String(err);

      console.error(`Fehler bei Gerät ${deviceId}:`, errorMessage);

      results.push({
        device_id: deviceId,
        location_count: 0,
        report_created: false,
        push_sent: false,
        error: errorMessage,
      });

      // Wichtig:
      // Ein fehlerhaftes Gerät darf die Verarbeitung anderer Geräte
      // nicht abbrechen.
      continue;
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 7. Ergebnis zurückgeben
  // ──────────────────────────────────────────────────────────────────────────

  return new Response(
    JSON.stringify({
      success: true,
      devices: devices.length,
      processed: processedDevices,
      reports: reportsCreated,
      locations: totalLocations,
      results,
    }),
    {
      headers: { "Content-Type": "application/json" },
    },
  );
});

// ─────────────────────────────────────────────────────────────────────────────
// Google Maps URL erzeugen
// ─────────────────────────────────────────────────────────────────────────────

function buildMapsUrl(
  locs: { lat: number; lng: number }[],
): string {
  if (locs.length === 0) {
    return "";
  }

  // Identische aufeinanderfolgende Koordinaten entfernen.
  const unique: { lat: number; lng: number }[] = [];

  for (const loc of locs) {
    const previous = unique[unique.length - 1];

    if (
      !previous ||
      previous.lat !== loc.lat ||
      previous.lng !== loc.lng
    ) {
      unique.push(loc);
    }
  }

  if (unique.length === 1) {
    return `https://www.google.com/maps?q=${unique[0].lat},${unique[0].lng}`;
  }

  const max = 10;
  const step = Math.max(1, Math.floor(unique.length / max));

  const pts: { lat: number; lng: number }[] = [];

  for (let i = 0; i < unique.length; i += step) {
    pts.push(unique[i]);

    if (pts.length >= max) {
      break;
    }
  }

  const last = unique[unique.length - 1];

  const alreadyLast =
    pts[pts.length - 1]?.lat === last.lat &&
    pts[pts.length - 1]?.lng === last.lng;

  if (!alreadyLast) {
    pts.push(last);
  }

  const origin = `${pts[0].lat},${pts[0].lng}`;
  const destination = `${pts[pts.length - 1].lat},${pts[pts.length - 1].lng}`;

  const waypoints = pts
    .slice(1, -1)
    .map((p) => `${p.lat},${p.lng}`)
    .join("|");

  let url =
    `https://www.google.com/maps/dir/${origin}/${destination}`;

  if (waypoints) {
    url += `?api=1&waypoints=${encodeURIComponent(waypoints)}`;
  }

  return url;
}

// ─────────────────────────────────────────────────────────────────────────────
// Firebase OAuth Access Token
// ─────────────────────────────────────────────────────────────────────────────

async function getFcmAccessToken(): Promise<string> {
  const serviceAccountRaw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");

  if (!serviceAccountRaw) {
    throw new Error("FIREBASE_SERVICE_ACCOUNT ist nicht konfiguriert.");
  }

  const sa = JSON.parse(serviceAccountRaw);
  const now = Math.floor(Date.now() / 1000);

  const header = urlB64(
    JSON.stringify({
      alg: "RS256",
      typ: "JWT",
    }),
  );

  const payload = urlB64(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  );

  const sigInput = `${header}.${payload}`;

  const privateKeyBase64 = sa.private_key
    .replace(/-----[^-]+-----/g, "")
    .replace(/\s/g, "");

  const privateKeyBytes = Uint8Array.from(
    atob(privateKeyBase64),
    (c) => c.charCodeAt(0),
  );

  const key = await crypto.subtle.importKey(
    "pkcs8",
    privateKeyBytes,
    {
      name: "RSASSA-PKCS1-v1_5",
      hash: "SHA-256",
    },
    false,
    ["sign"],
  );

  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(sigInput),
  );

  const jwt =
    `${sigInput}.${urlB64Bytes(new Uint8Array(signature))}`;

  const response = await fetch(
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

  if (!response.ok) {
    const errorText = await response.text();

    throw new Error(
      `Google OAuth HTTP ${response.status}: ${errorText}`,
    );
  }

  const tokenData = await response.json();

  if (!tokenData.access_token) {
    throw new Error("Google OAuth lieferte kein access_token.");
  }

  return tokenData.access_token;
}

// ─────────────────────────────────────────────────────────────────────────────
// Base64 URL Encoding
// ─────────────────────────────────────────────────────────────────────────────

function urlB64(s: string): string {
  return urlB64Bytes(
    new TextEncoder().encode(s),
  );
}

function urlB64Bytes(b: Uint8Array): string {
  return btoa(
    String.fromCharCode(...b),
  )
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}
