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