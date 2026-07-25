# Iki Das Anti Theft Tool

Diebstahlschutz-Tool für ein privates Zweit-Android-Handy. Ermöglicht das
Fernauslösen von Foto-Snapshot, Audio-Aufnahme und Standortabfrage über
Firebase Cloud Messaging (FCM), sowie die Anzeige des letzten Standorts
auf einer Karte im Web-Dashboard.

⚠️ **Nur für Geräte, die dir gehören / die du kontrollierst.** Der Einsatz
auf Geräten anderer Personen ohne deren Wissen ist rechtlich problematisch
(Stalkerware) – nicht Zweck dieses Projekts.

## Architektur

```
┌─────────────────┐        FCM Push         ┌───────────────────┐
│  Web-Dashboard   │ ───────────────────────▶│  Android-App       │
│  (dashboard/)    │                          │  (android-app/)    │
│  Firebase Hosting│◀─────────────────────────│  Foreground Service│
└─────────────────┘   Firestore + Storage    └───────────────────┘
        │                     ▲
        │  Cloud Function     │  Upload Foto / Audio / Standort
        ▼                     │
┌─────────────────┐           │
│ functions/       │──────────┘
│ (sendet FCM via  │
│  Admin SDK)       │
└─────────────────┘
```

## Ordnerstruktur

- `android-app/` – Android-App (Kotlin), läuft auf dem Zweithandy
- `dashboard/` – Web-Dashboard (Firebase Hosting), zeigt Karte + Steuerung
- `functions/` – Firebase Cloud Function, löst den FCM-Trigger aus
- `docs/SETUP.md` – Schritt-für-Schritt Firebase-Einrichtung

## Quick Start

Siehe [`docs/SETUP.md`](docs/SETUP.md) für die vollständige Einrichtung
(Firebase-Projekt anlegen, Konfigurationsdateien einfügen, App bauen,
Dashboard deployen).

## Status

🚧 Grundgerüst – Firebase-Konfiguration und echte Capture-Logik müssen
noch ergänzt werden (siehe TODOs in den jeweiligen Dateien).
