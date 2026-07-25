# AGENTS.md

Diese Datei gilt für **jede KI** (egal welches Tool), die an diesem Repo
arbeitet. Bitte vor jeder Änderung lesen und einhalten.

## Projektüberblick

Diebstahlschutz-Tool für ein privates Zweit-Android-Handy. Drei Teile,
die zusammenspielen:

```
android-app/   Kotlin-App auf dem Zweithandy (Kamera/Audio/Standort-Capture)
dashboard/     Web-Dashboard (Karte + Steuerbuttons), läuft via Firebase Hosting
functions/     Cloud Function, die den FCM-Trigger vom Dashboard zum Handy schickt
docs/          Setup- und sonstige Dokumentation
```

Kommunikationsfluss: Dashboard → Cloud Function → FCM (data-only push) →
Android-App → Capture → Upload nach Firestore/Storage → Dashboard zeigt
Ergebnis live an.

Details zur Einrichtung stehen in `docs/SETUP.md` – dort nachlesen statt
Setup-Schritte zu raten oder neu zu erfinden.

## Grundregeln

1. **Architektur nicht eigenmächtig ändern.** Wenn eine andere Lösung
   sinnvoller erscheint (z. B. anderer Cloud-Anbieter statt Firebase,
   anderes Datenmodell), das als Vorschlag im PR/Commit-Text
   dokumentieren statt es einfach umzusetzen.
2. **Bestehende Schnittstellen respektieren:**
   - FCM-Datenformat: `{ "command": "photo" | "audio" | "location" }`
   - Firestore-Struktur: `devices/{deviceId}` mit Unterkollektion
     `locations`; Feld `fcmToken` im Device-Dokument
   - Storage-Pfade: `devices/{deviceId}/photos/...` und
     `devices/{deviceId}/audio/...`
   Wer diese ändert, muss alle drei Teile (App, Function, Dashboard)
   konsistent mitziehen.
3. **Keine Secrets committen.** `google-services.json`,
   `firebase-config.js`, API-Keys, Service-Account-Keys – alles bleibt
   lokal, ist in `.gitignore` gelistet. Niemals Platzhalter durch echte
   Werte ersetzen und committen.
4. **TODOs sind Aufträge, keine Deko.** Wer an einer Datei mit TODO
   arbeitet, sollte es entweder lösen oder konkretisieren – nicht
   stillschweigend ignorieren.
5. **Kommentare und Doku auf Deutsch**, Code (Variablen-/Funktionsnamen)
   auf Englisch – so ist es aktuell im Projekt durchgängig gehalten.
6. **Kotlin-Stil:** offizielle Kotlin-Konventionen, 4 Leerzeichen
   Einrückung, `camelCase` für Funktionen/Variablen, `PascalCase` für
   Klassen. Keine neuen Abhängigkeiten hinzufügen, ohne sie kurz im
   Commit zu begründen.
7. **Sicherheitsrelevantes bevorzugt behandeln.** Dieses Projekt
   sammelt Kamera-, Mikro- und Standortdaten – Änderungen an
   Firestore-Regeln, Permissions oder Upload-Zielen immer besonders
   sorgfältig prüfen und im Commit-Text explizit erwähnen.
8. **Kleine, nachvollziehbare Commits.** Ein Commit = eine
   zusammenhängende Änderung. Aussagekräftige Commit-Messages (siehe
   `CONTRIBUTING.md`).

## Was NICHT verändert werden soll ohne Rücksprache

- Package-Name der Android-App (`com.ikianti.app`)
- Grundlegendes Berechtigungsmodell (welche Permissions die App anfragt)
- Der Umstand, dass FCM-Nachrichten "data only" (ohne sichtbare
  Notification-Payload) verschickt werden

## Definition of Done

Eine Änderung gilt als fertig, wenn:
- sie zu den bestehenden Schnittstellen passt (siehe oben)
- keine Secrets oder Test-Keys im Diff auftauchen
- `docs/SETUP.md` aktualisiert ist, falls sich der Einrichtungsprozess
  geändert hat
- offene TODOs entweder gelöst oder präzisiert wurden
