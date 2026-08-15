# Daily Report – Architektur und Änderungen

## Projekt

**Iki Anti-Theft Tool**

Supabase-Projekt:

- Project: `Papas Projekt`
- Reference ID: `ywrhhuhadgtmdzldbawa`
- Region: `West EU (Ireland)`

Edge Function:

```text
supabase/functions/daily-report/index.ts

1. Ausgangssituation
Die Edge Function daily-report wurde ursprünglich für ein einzelnes fest codiertes Gerät entwickelt.

Ursprünglich:

const DEVICE_ID = "phone-1";
Die tatsächlichen Daten in der Datenbank verwenden jedoch dynamische Geräte-IDs, z. B.:

device-f912c4ca
Dadurch lieferte die Function zunächst:

{
  "success": true,
  "count": 0
}
obwohl tatsächlich Standortdaten vorhanden waren.

2. Authentifizierung korrigiert
Ursprüngliches Problem
Die Function verwendete den Supabase Service Role Key als externes Authentifizierungsgeheimnis:

const authHeader = req.headers.get("Authorization") ?? "";
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

if (!authHeader.includes(serviceKey)) {
  return new Response(
    JSON.stringify({ error: "Unauthorized" }),
    { status: 401 }
  );
}
Zusätzlich war die Supabase Function zunächst noch mit JWT-Verifikation konfiguriert.

Das führte beim direkten Aufruf zu:

{
  "code": "UNAUTHORIZED_INVALID_JWT_FORMAT",
  "message": "Invalid JWT"
}
3. Supabase JWT-Verifikation deaktiviert
In:

supabase/config.toml
wurde konfiguriert:

project_id = "ywrhhuhadgtmdzldbawa"

[functions.daily-report]
verify_jwt = false
Damit übernimmt die Edge Function selbst die Authentifizierung.

4. Eigener Cron-Secret-Mechanismus
Die externe Authentifizierung erfolgt jetzt über:

DAILY_REPORT_SECRET
Die Function verwendet:

const authHeader = req.headers.get("Authorization") ?? "";
const cronSecret = Deno.env.get("DAILY_REPORT_SECRET");

if (!cronSecret) {
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
Wichtig:

SUPABASE_SERVICE_ROLE_KEY wird weiterhin benötigt, aber ausschließlich für den internen Supabase-Datenbankzugriff.

Es wird NICHT mehr als Passwort für cron-job.org verwendet.

5. Authentifizierung erfolgreich getestet
Der Test mit dem korrekten DAILY_REPORT_SECRET liefert:

{
  "success": true,
  "count": 0
}
Das bestätigte:

Supabase Gateway
    ↓
verify_jwt = false
    ↓
Edge Function
    ↓
DAILY_REPORT_SECRET
    ↓
Authentifizierung erfolgreich
Der vorherige Fehler:

{
  "code": "UNAUTHORIZED_INVALID_JWT_FORMAT",
  "message": "Invalid JWT"
}
ist damit behoben.

6. Multi-Device-Unterstützung
Die Function verwendet keine fest codierte Geräte-ID mehr.

Entfernt wurde das Konzept:

const DEVICE_ID = "device-f912c4ca";
Stattdessen werden die registrierten Geräte aus der Tabelle:

devices
geladen.

Aktuelles Schema:

devices
├── id
├── fcm_token
├── last_seen
├── last_boot
└── created_at
Die Function verwendet:

const { data: devices, error: devicesError } = await supabase
  .from("devices")
  .select("id")
  .order("created_at", { ascending: true });
Danach wird jedes Gerät einzeln verarbeitet:

for (const device of devices) {
  const deviceId = device.id;

  // Standortdaten des Geräts laden
  // Report erstellen
  // Dashboard benachrichtigen
}
Dadurch funktioniert die Function zukünftig automatisch mit mehreren Geräten.

7. Aktuelles Gerät
Aktuell existiert:

device-f912c4ca
In locations existieren dafür aktuell mehrere Standortdaten.

Beispielsweise:

device_id       lat          lng         timestamp
device-f912c4ca 51.5735093   6.993563    ...
Die Function verarbeitet diese Daten jetzt automatisch anhand der devices-Tabelle.

8. Fehlerisolierung zwischen Geräten
Die Geräte werden einzeln verarbeitet.

Ein Fehler bei einem Gerät soll die Verarbeitung der anderen Geräte nicht abbrechen.

Konzept:

Gerät A
  ↓
Fehler
  ↓
loggen
  ↓
Gerät B
  ↓
weiterverarbeiten
Die Ergebnisse werden gesammelt:

const results: Array<{
  device_id: string;
  location_count: number;
  report_created: boolean;
  push_sent: boolean;
  error?: string;
}> = [];
9. Location-Abfrage
Für jedes registrierte Gerät werden die Standortdaten der letzten 24 Stunden geladen:

const now = Date.now();
const since = now - 24 * 60 * 60 * 1000;
Abfrage:

const { data: locations, error: locationsError } = await supabase
  .from("locations")
  .select("lat, lng, timestamp")
  .eq("device_id", deviceId)
  .gte("timestamp", since)
  .order("timestamp", { ascending: true });
10. Tagesbericht
Bei vorhandenen Standortdaten werden folgende Informationen gespeichert:

date
device_id
location_count
first_timestamp
last_timestamp
first_lat
first_lng
last_lat
last_lng
maps_url
11. Reports-Duplikate verhindert
Die Tabelle reports besitzt bereits folgende Unique-Constraint:

reports_date_device_id_unique
Definition:

UNIQUE (date, device_id)
Aktuelle Constraints:

reports_date_device_id_unique
UNIQUE (date, device_id)

reports_pkey
PRIMARY KEY (id)
Damit kann es pro Gerät und Datum nur einen Report geben.

Beispiel:

2026-08-15 + device-f912c4ca
darf nur einmal existieren.

12. Upsert statt Insert
Die Edge Function verwendet für reports:

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
Damit wird bei einem erneuten Lauf desselben Tages der vorhandene Report aktualisiert.

Es entsteht kein zweiter Datensatz.

13. Bereits vorhandene Duplikate
Während der Entwicklung wurden mehrere manuelle Tests durchgeführt.

Dadurch entstanden temporär mehrere Reports für:

2026-08-15
device-f912c4ca
Diese wurden identifiziert und sollen vor dem produktiven Betrieb bereinigt werden.

Die Duplikatprüfung erfolgt mit:

SELECT
    date,
    device_id,
    COUNT(*) AS count
FROM reports
GROUP BY date, device_id
HAVING COUNT(*) > 1;
Erwartung nach der Bereinigung:

keine Zeilen
14. Google Maps
Die Function erzeugt weiterhin eine Google-Maps-Route.

Die neue buildMapsUrl()-Implementierung entfernt identische aufeinanderfolgende Koordinaten.

Bei nur einem Standort:

https://www.google.com/maps?q=LAT,LNG
Bei mehreren Standorten wird eine Route mit maximal ungefähr 10 Punkten erzeugt.

15. App-Nutzung
Für jedes Gerät werden zusätzlich die Top-5-App-Nutzungen aus:

usage_logs
geladen.

Abfrage:

.from("usage_logs")
.select("app_name, app_package, total_time_ms")
.eq("device_id", deviceId)
.eq("date", reportDate)
.order("total_time_ms", { ascending: false })
.limit(5);
Die Nutzungszeit wird in Minuten umgerechnet.

Beispiel:

📱 Chrome 45Min · YouTube 32Min · Maps 18Min
16. Dashboard-FCM
Der Dashboard-FCM-Token wird weiterhin aus:

owner
geladen.

Dabei wird:

id = dashboard
verwendet.

Abfrage:

const { data: ownerData } = await supabase
  .from("owner")
  .select("fcm_token")
  .eq("id", "dashboard")
  .single();
Wichtig:

owner.fcm_token
    ↓
Dashboard
während:

devices.fcm_token
    ↓
jeweiliges registriertes Gerät
nicht miteinander verwechselt werden sollen.

17. Firebase FCM
Die Function erzeugt ein OAuth Access Token über:

FIREBASE_SERVICE_ACCOUNT
und verwendet:

FIREBASE_PROJECT_ID
für Firebase Cloud Messaging.

FCM Endpoint:

https://fcm.googleapis.com/v1/projects/{projectId}/messages:send
18. FCM erfolgreich getestet
Der erfolgreiche Test lieferte:

{
  "success": true,
  "devices": 1,
  "processed": 1,
  "reports": 1,
  "locations": 5,
  "results": [
    {
      "device_id": "device-f912c4ca",
      "location_count": 5,
      "report_created": true,
      "push_sent": true
    }
  ]
}
Damit wurde die komplette Pipeline erfolgreich getestet.

19. Aktueller Datenfluss
cron-job.org
      │
      │ POST
      │ Authorization: Bearer DAILY_REPORT_SECRET
      ▼
Supabase Edge Function
daily-report
      │
      ├── Authentifizierung
      │
      ├── devices
      │      │
      │      ├── Gerät A
      │      ├── Gerät B
      │      └── Gerät C
      │
      ├── locations
      │
      ├── usage_logs
      │
      ├── reports
      │      │
      │      └── UNIQUE(date, device_id)
      │
      └── Firebase FCM
             │
             ▼
          Dashboard
20. Aktueller erfolgreicher Test
Request:

curl -X POST "https://ywrhhuhadgtmdzldbawa.supabase.co/functions/v1/daily-report" -H "Authorization: Bearer DEIN_DAILY_REPORT_SECRET" -H "Content-Type: application/json" -d "{}"
Ergebnis:

{
  "success": true,
  "devices": 1,
  "processed": 1,
  "reports": 1,
  "locations": 5,
  "results": [
    {
      "device_id": "device-f912c4ca",
      "location_count": 5,
      "report_created": true,
      "push_sent": true
    }
  ]
}
21. Deployment
Deployment erfolgt aus:

C:\Iki-Anti-Theft-Tool
mit:

supabase functions deploy daily-report
Die Warnung:

WARNING: Docker is not running
ist beim Remote-Deployment nicht kritisch.

Der erfolgreiche Deploy wird durch:

Deployed Functions on project ywrhhuhadgtmdzldbawa: daily-report
bestätigt.

22. Supabase-Konfiguration
Aktuell:

project_id = "ywrhhuhadgtmdzldbawa"

[functions.daily-report]
verify_jwt = false
Die Function verwendet eine eigene Bearer-Authentifizierung über:

DAILY_REPORT_SECRET
23. Secrets
Benötigte Supabase Secrets:

DAILY_REPORT_SECRET
SUPABASE_SERVICE_ROLE_KEY
FIREBASE_PROJECT_ID
FIREBASE_SERVICE_ACCOUNT
Secrets niemals in Git committen.

Secrets niemals in Chat, Issues oder Dokumentationen mit ihrem tatsächlichen Wert speichern.

24. Bekannte offene Aufgabe
Die Multi-Device-Funktion ist implementiert und erfolgreich getestet.

Als nächstes sollte geprüft werden:

reports enthält keine verbleibenden Duplikate.
upsert funktioniert bei wiederholtem Aufruf.
Zweiter Aufruf am selben Tag erzeugt keinen zweiten Report.
cron-job.org wird auf den neuen Auth-Mechanismus eingestellt.
Der automatische 03:00-Uhr-Lauf wird einmal real getestet.
Verhalten bei mehreren Geräten testen.
Verhalten bei einem Gerät ohne Standortdaten testen.
Verhalten bei einem offline Gerät testen.
FCM-Fehler sollen die Report-Erstellung nicht verhindern.
25. Wichtige Entwicklungsregel
Bei Änderungen an daily-report immer zwischen folgenden Bereichen unterscheiden:

Externe Authentifizierung
DAILY_REPORT_SECRET
Supabase-Datenbankzugriff
SUPABASE_SERVICE_ROLE_KEY
Firebase
FIREBASE_SERVICE_ACCOUNT
FIREBASE_PROJECT_ID
Geräte
devices.id
Keine dieser Informationen sollte hart codiert werden, wenn sie aus der Datenbank oder aus Supabase Secrets bezogen werden kann.

Status
Daily Report: FUNKTIONSFÄHIG

 JWT-Problem behoben
 Eigene Cron-Authentifizierung
 Supabase Service Role getrennt
 Dynamische Geräteermittlung
 Multi-Device-Verarbeitung
 24h Location-Auswertung
 App-Nutzungsdaten
 Google Maps URL
 Reports
 Unique Constraint (date, device_id)
 Upsert
 Firebase FCM
 Dashboard Push
 Erfolgreicher End-to-End-Test


Nächster Schwerpunkt: Duplikatverhalten endgültig testen und anschließend den automatischen cron-job.org-Lauf produktiv schalten.


