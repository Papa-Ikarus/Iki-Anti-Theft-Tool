# 🔐 Iki Anti-Theft Tool

Ein eigenes Anti-Theft-System für Android mit Web-Dashboard, Standortüberwachung, Fernbefehlen, App-Nutzungsstatistik und automatischen Tagesberichten.

Das Projekt besteht aus einer Android-App auf dem überwachten Gerät und einem Web-Dashboard zur Überwachung und Steuerung.

---

## 📌 Projektstatus

**Aktive Entwicklung**

Das Grundsystem ist funktionsfähig und wird aktuell weiter ausgebaut und stabilisiert.

### Bereits umgesetzt

- ✅ Android-App
- ✅ Supabase-Anbindung
- ✅ Firebase Cloud Messaging (FCM)
- ✅ Web-Dashboard
- ✅ Benutzer-Login
- ✅ Geräteverwaltung
- ✅ Multi-Device-Unterstützung
- ✅ Standortübertragung
- ✅ Echtzeit-Standortupdates
- ✅ Standortverlauf
- ✅ Routenanzeige auf der Karte
- ✅ Zeitstempel der Standortpunkte
- ✅ Foto-Befehl
- ✅ Audio-Befehl
- ✅ Standort-Befehl
- ✅ App-Nutzungsstatistik
- ✅ manueller `usage`-Befehl
- ✅ persistente Befehle über SharedPreferences
- ✅ automatische Tagesberichte
- ✅ FCM-Benachrichtigung für Tagesberichte
- ✅ Google-Maps-Routenlink im Tagesbericht
- ✅ Top-App-Nutzung im Tagesbericht
- ✅ Report-Upsert gegen doppelte Tagesberichte
- ✅ Verarbeitung mehrerer Geräte

---

# 🏗️ Architektur

Das Projekt besteht aus mehreren Komponenten:

```text
┌──────────────────────┐
│     Android-App      │
│   Überwachtes Gerät  │
└──────────┬───────────┘
           │
           │ HTTPS / FCM
           ▼
┌──────────────────────┐
│       Supabase       │
│                      │
│  PostgreSQL          │
│  Edge Functions      │
│  Storage             │
│  Authentication      │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│   Web-Dashboard      │
│                      │
│ Karte                │
│ Geräte               │
│ Befehle              │
│ App-Nutzung          │
│ Tagesberichte        │
└──────────────────────┘

          ▲
          │
          │ FCM
          │
┌──────────────────────┐
│       Firebase       │
│         FCM          │
└──────────────────────┘
```



# 📱 Android-App

Die Android-App läuft auf dem überwachten Gerät.

Sie übernimmt unter anderem:

Registrierung des Geräts
Standorterfassung
Übertragung von Standortdaten
Empfang von Fernbefehlen
Fotoaufnahme
Audioaufnahme
Übertragung von Daten zu Supabase
Erfassung der App-Nutzung
Verarbeitung von FCM-Nachrichten
persistente Speicherung ausstehender Befehle
Android-Technologie
Kotlin
Android SDK
Firebase Cloud Messaging
Supabase REST API
OkHttp
Google Play Services Location
Android WorkManager
SharedPreferences


Aktuelle Build-Konfiguration

```text
compileSdk: 36
minSdk:     26
targetSdk:  33
Java:       17
```

---
# 🌐 Web-Dashboard

Das Dashboard ist eine Web-Anwendung und wird über Firebase Hosting bereitgestellt.


Das Dashboard ermöglicht:

# 🔐 Anmeldung

Login über Supabase Authentication.

# 📱 Geräteverwaltung

Alle registrierten Geräte werden angezeigt.

Bei mehreren Geräten kann zwischen den Geräten gewechselt werden.

# 📍 Standort

Das Dashboard zeigt:

aktuellen Standort
letzten bekannten Standort
Standortverlauf
Route der letzten 24 Stunden
Zeitstempel der Standortpunkte

Die Karte verwendet Leaflet und OpenStreetMap.

# 🎛️ Fernbefehle

Folgende Befehle stehen zur Verfügung:

📷 Foto
🎙️ Audio
📍 Standort
📊 App-Nutzung
📊 App-Nutzung

Das Dashboard kann die App-Nutzung des ausgewählten Geräts anzeigen.

Dabei werden unter anderem verwendet:

App-Name
Package Name
Nutzungsdauer
Startzeit
letzter Aufruf

---
☁️ Supabase

Supabase ist die zentrale Backend-Plattform des Projekts.

Verwendete Komponenten:

PostgreSQL
Edge Functions
Storage
Authentication


Die Datenbank enthält unter anderem folgende Tabellen:

```text
devices
locations
usage_logs
reports
owner
```

---
## 🗄️ Datenbank

<mark>devices</mark>

Registrierte Android-Geräte.

Wichtige Felder:

```text
id
fcm_token
last_seen
last_boot
created_at
```

---
<mark>locations</mark>

Standortdaten der Geräte.


Wichtige Felder:

```text
id
device_id
lat
lng
timestamp
created_at
```

Standorte sind über <mark>device_id</mark> einem Gerät zugeordnet.
---

<mark>usage_logs</mark>


Speichert die App-Nutzung.


Unter anderem:

```text
device_id
date
app_name
app_package
total_time_ms
start_time
last_used_time
```

Die App-Nutzung wird nach Gerät und Datum verarbeitet.
---

<mark>reports</mark>

Speichert die erzeugten Tagesberichte.

Unter anderem:

```text
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
created_at
```

Für einen Tag und ein Gerät darf nur ein Tagesbericht existieren.

Dafür wird ein eindeutiger Schlüssel verwendet:

```text
(date, device_id)
```

Der Daily Report verwendet deshalb einen Upsert.
---

<mark>owner</mark>

Speichert den FCM-Token des Dashboards.

Das Dashboard kann dadurch über neue Tagesberichte informiert werden.
---


## 📍 Standortüberwachung

Die Android-App übermittelt Standortpunkte an Supabase.

Das Dashboard kann diese Daten anschließend darstellen.

Der Standortverlauf der letzten 24 Stunden wird als Route dargestellt.

Zusätzlich werden einzelne Standortpunkte mit Zeitstempeln angezeigt.
---


## 📊 App-Nutzung

Die Android-App erfasst die Nutzung installierter Apps.

Die Daten werden in <mark>usage_logs</mark> gespeichert.

Das Dashboard kann die Daten für das ausgewählte Gerät abrufen.

Zusätzlich kann über den Button:

```text
 📊 App-Nutzung
```

eine sofortige Abfrage ausgelöst werden.
---


## 📋 Tagesbericht

Der Tagesbericht wird automatisch über die Supabase Edge Function

```text
daily-report
```

erstellt.


Ablauf

```text
cron-job.org
      │
      ▼
Supabase Edge Function
      │
      ▼
Geräte laden
      │
      ▼
Standorte der letzten 24 Stunden laden
      │
      ├── keine Standorte
      │
      └── Standorte vorhanden
              │
              ▼
        App-Nutzung laden
              │
              ▼
        Report speichern
              │
              ▼
        FCM Push senden
              │
              ▼
        Dashboard
```
---


## ⏰ Automatischer Daily Report

Der Daily Report wird über `cron-job.org` automatisch ausgelöst.

Die aktuelle Ausführungsfrequenz wird über den Cron-Job konfiguriert.

### Aktuelle Testkonfiguration

Der Cron-Job läuft derzeit testweise alle 6 Stunden.

Damit wird die Edge Function bis zu viermal täglich aufgerufen:

- 00:00 Uhr
- 06:00 Uhr
- 12:00 Uhr
- 18:00 Uhr

Die genaue Uhrzeit und Frequenz kann in `cron-job.org` angepasst werden.

Die Edge Function verarbeitet dabei alle registrierten Geräte.

Durch die eindeutige Zuordnung eines Reports zu `date` und `device_id` werden bei wiederholten Aufrufen keine doppelten Tagesberichte für dasselbe Gerät und Datum erzeugt.
---


## 🔔 Firebase Cloud Messaging

Firebase wird im Projekt hauptsächlich für Push-Benachrichtigungen verwendet.

Verwendung:

```text
Android → FCM
Dashboard → FCM
Daily Report → FCM
```

Firebase ist damit nicht die eigentliche Datenbank des Systems.

Die persistenten Projektdaten liegen in Supabase.
---


## 🔐 Sicherheit

Sensible Zugangsdaten dürfen niemals in das Git-Repository eingecheckt werden.

Dazu gehören insbesondere:
```text
SUPABASE_SERVICE_ROLE_KEY
DAILY_REPORT_SECRET
FIREBASE_SERVICE_ACCOUNT
```

Der Service-Role-Key darf ausschließlich serverseitig verwendet werden.

Der Daily Report wird über
```text
Authorization: Bearer <DAILY_REPORT_SECRET>
```

authentifiziert.

Die Edge Function prüft diesen Header vor der Verarbeitung.
---


## 🗂️ Projektstruktur
```text
Iki-Anti-Theft-Tool/
│
├── android-app/
│   └── app/
│       └── src/
│           └── main/
│
├── dashboard/
│   ├── index.html
│   ├── firebase-messaging-sw.js
│   └── ...
│
├── supabase/
│   ├── functions/
│   │   ├── daily-report/
│   │   └── send-command/
│   │
│   └── migrations/
│       └── 001_init.sql
│
├── tools/
│   └── ...
│
├── docs/
│   ├── SETUP.md
│   └── ...
│
├── README.md
└── firebase.json
```
---


## ⚙️ Einrichtung

Eine vollständige Installationsanleitung befindet sich in:
```text
docs/SETUP.md
```

Dort werden unter anderem beschrieben:

Firebase einrichten
Supabase einrichten
Datenbank erstellen
Authentication
Edge Functions
Secrets
Daily Report
Firebase Hosting
Android-App
---


## 🧪 Entwicklung
Android-App

Das Verzeichnis
```text
android-app/
```

kann mit Android Studio geöffnet werden.

Dashboard

Das Dashboard befindet sich unter:
```text
dashboard/
```


Supabase Edge Functions

Wichtige Funktionen:
```text
send-command
daily-report
```

Deployment erfolgt über die Supabase CLI:
```text
supabase functions deploy send-command
supabase functions deploy daily-report
```
---


## 🛠️ Hilfreiche ADB-Befehle

Die App kann nach dem Verstecken des Launcher-Icons weiterhin direkt gestartet werden:
```text
adb shell am start -n com.ikianti.app/.MainActivity
```

ADB-Logs können beispielsweise gefiltert werden mit:
```text
adb logcat -s "SupabaseApi" "UsageStatsCapture" "CaptureFGS"
```
---


## 📝 Dokumentation

Weitere technische Dokumentation befindet sich unter:
```text
docs/
```

Besonders relevant:
```text
docs/SETUP.md
DAILY_REPORT_ARCHITECTURE.md
```
---


## 🚧 Aktuelle Entwicklung

Das Projekt befindet sich weiterhin in aktiver Entwicklung.

Aktuelle Schwerpunkte:

Stabilisierung der App-Nutzung
zuverlässige Synchronisation der <mark>usage_logs</mark>
Stabilisierung des Daily Reports
weitere Verbesserungen am Dashboard
Multi-Device-Optimierung
Fehlerbehandlung bei Offline-/Online-Wechseln
Verbesserung der Persistenz von Befehlen
---


## 📜 Lizenz

Dieses Projekt befindet sich derzeit in privater Entwicklung.

Eine endgültige Open-Source-Lizenz ist noch nicht festgelegt.
---


## 👨‍💻 Projekt

Iki Anti-Theft Tool

GitHub Repository:

https://github.com/Papa-Ikarus/Iki-Anti-Theft-Tool










