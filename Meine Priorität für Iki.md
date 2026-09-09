# Meine Priorität für Iki

## Iki Anti-Theft Tool – Entwicklungs-Roadmap

Diese Datei definiert die geplante Reihenfolge der nächsten Entwicklungsarbeiten am **Iki Anti-Theft Tool**.

Ziel ist es, das bestehende System zuerst zu stabilisieren und anschließend schrittweise um wichtige Funktionen zu erweitern.

---

# Prioritäten

| Priorität | Bereich                        | Nutzen      | Status          |
| --------: | ------------------------------ | ----------- | --------------- |
|         1 | Tagesbericht verbessern        | Sehr hoch   | ⏳               |
|         2 | Live-Gerätestatus              | Sehr hoch   | 🔜 Als Nächstes |
|         3 | Standortkarte verbessern       | Hoch        | ⏳               |
|         4 | Remote-Befehle robuster machen | Hoch        | ⏳               |
|         5 | FCM-/Geräteverwaltung          | Hoch        | ⏳               |
|         6 | UsageStats verbessern          | Mittel–hoch | ⏳               |
|         7 | Sicherheits-/Fehlerprotokoll   | Hoch        | ⏳               |
|         8 | Dashboard UI/UX                | Mittel      | ⏳               |
|         9 | Offline-/Reconnect-Handling    | Hoch        | ⏳               |
|        10 | Aufräumen & Release-Härtung    | Sehr hoch   | ⏳               |

---

# Phase 1 – Stabilität

## 1. Tagesbericht verbessern

Der bestehende Tagesbericht soll zu einem übersichtlichen vollständigen Tagesbericht ausgebaut werden.

Geplante Informationen:

* Datum
* Anzahl der Standortpunkte
* erster Standort
* letzter Standort
* erste Uhrzeit
* letzte Uhrzeit
* Google-Maps-Route
* Top-App-Nutzung
* Geräte-ID

Beispiel:

```text
📍 Tagesbericht – device-b4e40188

📅 09.09.2026

📍 37 Standortpunkte
🕐 Erster: 08:14 Uhr
🕐 Letzter: 18:42 Uhr

📱 App-Nutzung
• Brave – 42 Min
• Launcher – 26 Min
• Einstellungen – 8 Min

🗺️ Route anzeigen
```

### Aktueller Stand

* Web-Push funktioniert.
* FCM Token wird korrekt gespeichert.
* FCM Push wird erfolgreich versendet.
* Brave empfängt die Benachrichtigung.
* Klick auf die Benachrichtigung öffnet Google Maps.
* `daily-report` ist erfolgreich deployed.
* Der echte produktive Tagesbericht muss noch mit einem vollständigen Tagesdatensatz getestet werden.

### Wichtig

Die temporäre Funktion `test-daily-report` bleibt bis nach dem erfolgreichen produktiven Test bestehen.

Danach wird sie entfernt.

---

# Phase 2 – Dashboard

## 2. Live-Gerätestatus

### Ziel

Das Dashboard soll auf einen Blick zeigen, ob ein Gerät erreichbar bzw. zuletzt aktiv war.

Beispiel:

```text
📱 Mein Gerät

🟢 Online
🔋 78 %
📶 WLAN
📍 Standort vor 42 Sekunden
🕐 Letzter Kontakt: 19:51
🔌 Boot: heute 08:03
```

Mögliche Datenquellen:

* `devices.last_seen`
* `devices.last_boot`
* `locations.timestamp`
* aktuelle Standortdaten
* FCM-Verbindungs-/Command-Status
* später Batterieinformationen

### Zielzustand

Das Dashboard soll eindeutig zwischen folgenden Zuständen unterscheiden können:

```text
🟢 Online
🟡 Zuletzt vor kurzer Zeit gesehen
🟠 Länger nicht gesehen
🔴 Offline / keine Verbindung
```

---

## 3. Standortkarte verbessern

Die Standortdaten sollen direkt im Dashboard übersichtlich dargestellt werden.

Geplante Funktionen:

* Tagesroute
* Startpunkt
* Endpunkt
* Standortpunkte
* Zeitstempel
* Tagesauswahl
* Klick auf Standortpunkt
* Anzeige der Uhrzeit
* Anzahl der Standortpunkte
* Route des ausgewählten Tages

Beispiel:

```text
Start
  ●
  │
  ●
  │
  ●────●
       │
       ●
       │
      Ende
```

Google Maps bleibt zusätzlich als externe Routenansicht verfügbar.

---

# Phase 3 – Remote-Steuerung

## 4. Remote-Befehle robuster machen

Die bestehenden Remote-Befehle sollen zuverlässig nachvollziehbar werden.

Geplante Befehle:

```text
📍 Standort anfordern
📷 Foto aufnehmen
🎙️ Audio aufnehmen
📱 UsageStats abrufen
🔔 Test-Benachrichtigung
```

Später eventuell weitere legitime Geräteverwaltungsfunktionen.

### Command-Status

Jeder Befehl soll eine eindeutige Command-ID bekommen.

Beispiel:

```text
Command
   ↓
SENT
   ↓
RECEIVED
   ↓
EXECUTED
```

Optional:

```text
FAILED
TIMEOUT
```

Dadurch kann das Dashboard anzeigen, ob ein Befehl tatsächlich vom Android-Gerät verarbeitet wurde.

---

# Phase 4 – FCM und Geräteverwaltung

## 5. FCM-/Geräteverwaltung

FCM Tokens sollen sauber verwaltet werden.

Geplantes Verhalten:

```text
FCM Token speichern
       ↓
Nachricht senden
       ↓
Erfolg?
  ↙       ↘
 JA       NEIN
          ↓
   UNREGISTERED?
          ↓
   alten Token entfernen
```

Ziele:

* ungültige Tokens erkennen
* alte Tokens entfernen
* Token-Wechsel sauber behandeln
* Geräte eindeutig identifizieren
* Reinstallationen berücksichtigen

---

# Phase 5 – UsageStats

## 6. UsageStats verbessern

Die bestehende UsageStats-Erfassung funktioniert grundsätzlich.

Als nächste Verbesserungen:

* korrekte Tageszuordnung
* korrekte Start-/Endzeiten
* verständliche App-Namen
* bessere Sortierung
* übersichtlichere Darstellung
* Top-Apps
* Nutzungsdauer
* eventuell grafische Darstellung

Beispiel:

```text
Brave
████████████████ 42 Min

Launcher
██████████ 26 Min

Einstellungen
███ 8 Min
```

Die bestehende Android-Erfassung soll dabei möglichst stabil bleiben.

---

# Phase 6 – Sicherheits- und Fehlerprotokoll

## 7. Sicherheits-/Fehlerprotokoll

Das System soll wichtige Ereignisse nachvollziehbar protokollieren.

Beispiel:

```text
🔐 Sicherheitsereignisse

09.09. 19:42  Standort aktualisiert
09.09. 19:41  FCM verbunden
09.09. 18:42  Gerät zuletzt gesehen
09.09. 08:03  Gerät gestartet
08.09. 23:15  App registriert
```

Mögliche Ereignisse:

* Geräte-Registrierung
* FCM Token geändert
* FCM Command empfangen
* Command ausgeführt
* Standort übertragen
* UsageStats übertragen
* Upload fehlgeschlagen
* Netzwerkfehler
* App-Start
* Geräte-Boot

---

# Phase 7 – Dashboard UI/UX

## 8. Dashboard verbessern

Erst wenn die Funktionen stabil sind, wird die Oberfläche umfassender überarbeitet.

Mögliche Verbesserungen:

* klarere Gerätekarten
* Statusanzeigen
* bessere Mobile-Darstellung
* übersichtliche Tagesberichte
* bessere Kartenansicht
* Command-Status
* Fehleranzeigen
* Ladezustände
* leere Zustände
* bessere Navigation

### Grundregel

Keine unnötigen Änderungen am funktionierenden Dashboard.

Neue UI-Funktionen werden erst eingebaut, wenn die zugrunde liegenden Daten zuverlässig funktionieren.

---

# Phase 8 – Offline und Reconnect

## 9. Offline-/Reconnect-Handling

Das Android-Gerät soll auch bei fehlender Internetverbindung möglichst zuverlässig arbeiten.

Geplanter Ablauf:

```text
Standort erfassen
       ↓
Internet vorhanden?
   ↙          ↘
 JA           NEIN
 ↓             ↓
Supabase      lokale Queue
                ↓
          später hochladen
```

Mögliche Offline-Daten:

* Locations
* UsageStats
* Command-Ergebnisse

Nach Wiederherstellung der Verbindung:

```text
Internet wieder verfügbar
          ↓
     Queue prüfen
          ↓
    Daten hochladen
          ↓
    erfolgreich?
       ↙     ↘
     JA      NEIN
     ↓        ↓
  entfernen  behalten
```

---

# Phase 9 – Release-Härtung

## 10. Aufräumen & Release-Härtung

Vor einer stabilen Release-Version:

### Android

* Berechtigungen überprüfen
* Hintergrundverhalten überprüfen
* Akkuverbrauch prüfen
* Netzwerkfehler testen
* Offline-Verhalten testen
* FCM testen
* Location-Upload testen
* UsageStats testen
* Debug-Logs reduzieren

### Supabase

* RLS überprüfen
* Service-Role-Key niemals im Client verwenden
* Secrets überprüfen
* Edge Functions überprüfen
* Fehlerbehandlung überprüfen
* Datenbank-Constraints überprüfen

### Firebase

* FCM Tokens überprüfen
* Web Push überprüfen
* Android Push überprüfen
* ungültige Tokens behandeln
* Service Worker überprüfen

### Dashboard

* Authentifizierung überprüfen
* Session-Wiederherstellung überprüfen
* Fehlerzustände prüfen
* Mobile Darstellung prüfen
* Browser-Kompatibilität testen

### Release

* Debug-Code entfernen
* finale Build-Konfiguration
* Versionsnummer festlegen
* Release-Build erstellen
* vollständigen End-to-End-Test durchführen

---

# Entwicklungsprinzipien

## 1. Bestehende funktionierende Funktionen nicht unnötig verändern

Insbesondere:

* Web Push
* FCM
* `daily-report`
* Supabase-Verbindung
* bestehende Android-Location-Erfassung
* funktionierende Dashboard-Funktionen

## 2. Änderungen einzeln durchführen

Nach Möglichkeit:

```text
Änderung
↓
Build
↓
Test
↓
Commit
↓
Push
↓
nächste Änderung
```

## 3. Keine großen Umbauten ohne Grund

Bestehenden funktionierenden Code bevorzugt erweitern statt komplett neu schreiben.

## 4. Produktionscode und Testcode trennen

Temporäre Testfunktionen wie:

```text
test-daily-report
```

werden nach erfolgreichem Test wieder entfernt.

## 5. Supabase-Datenbankänderungen als Migration dokumentieren

Keine wichtigen Datenbankänderungen nur manuell durchführen.

---

# Aktueller nächster Schritt

## 🔜 Live-Gerätestatus

Nach erfolgreichem produktivem `daily-report`-Test wird als nächstes der **Live-Gerätestatus** umgesetzt.

Dafür sollen zunächst die bereits vorhandenen Daten verwendet werden:

```text
devices
├── id
├── last_seen
├── last_boot
└── created_at

locations
└── timestamp
```

Erst danach entscheiden wir, ob zusätzliche Android-Daten wie Batterie, Netzwerktyp oder weitere Statusinformationen benötigt werden.

---

# Fortschritt

```text
Phase 1  Stabilität          🟡 läuft
Phase 2  Dashboard           ⏳
Phase 3  Remote-Steuerung    ⏳
Phase 4  FCM/Geräte          ⏳
Phase 5  UsageStats          🟢 Basis funktioniert
Phase 6  Sicherheitslog      ⏳
Phase 7  Dashboard UI/UX     ⏳
Phase 8  Offline/Reconnect   ⏳
Phase 9  Release-Härtung     ⏳
```

## Wichtigster nächster Meilenstein

**Produktiven Daily Report erfolgreich durchführen und anschließend den Live-Gerätestatus implementieren.**
