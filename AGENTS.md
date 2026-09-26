# AGENTS.md

Diese Datei gilt für jede KI (unabhängig vom verwendeten Tool), die an
diesem Repository arbeitet. Vor jeder Änderung lesen und einhalten.

## Projektüberblick

Iki ist ein privates Anti-Theft-System für eigene Android-Geräte. Es darf
nicht als Stalkerware oder zur Überwachung fremder Personen eingesetzt werden.

Die wichtigsten Komponenten sind:

```text
android-app/app/     App auf dem überwachten Zweithandy
android-app/owner/   separate Besitzer-App für Tagesbericht-Pushs
dashboard/           Web-Dashboard, ausgeliefert über Firebase Hosting
supabase/functions/  Edge Functions für Befehle, Berichte und Owner-Push
supabase/migrations/ PostgreSQL-Schema, RLS- und Storage-Regeln
docs/                Setup- und Betriebsdokumentation
tools/               lokale Prüf- und Hilfsskripte
```

Firebase wird für Cloud Messaging (FCM) und das Hosting des Dashboards
verwendet. Persistente Daten, Authentifizierung und Datei-Uploads liegen in
Supabase.

Der Hauptfluss für Fernbefehle ist:

Dashboard → Supabase Edge Function `send-command` → FCM data-only Push →
Zweithandy-App → Aufnahme/Erfassung → Supabase Database bzw. Storage →
Dashboard.

Tagesberichte werden durch `daily-report` aus Supabase-Daten erzeugt und per
Web-Push sowie als data-only FCM-Push an die Besitzer-App gemeldet.

Details zur Einrichtung stehen in `docs/SETUP.md`; die Besitzer-App ist in
`docs/OWNER_APP.md` beschrieben. Setup-Schritte nicht raten oder duplizieren.

## Grundregeln

1. **Architektur nicht eigenmächtig austauschen.** Grundlegende Änderungen an
   Backend, Datenmodell, Authentifizierung oder Nachrichtenfluss zuerst als
   Vorschlag dokumentieren und mit dem Besitzer abstimmen.
2. **Bestehende Schnittstellen konsistent halten.** Bei Änderungen immer alle
   betroffenen Komponenten, Tests und Dokumente gemeinsam aktualisieren.
3. **Keine Geheimnisse committen.** Dazu gehören insbesondere
   `google-services.json`, Firebase-Service-Account-Daten,
   `SUPABASE_SERVICE_ROLE_KEY` und `DAILY_REPORT_SECRET`. Öffentliche
   Client-Konfiguration wie Supabase-URL, Supabase-`anon`-Key oder
   Firebase-Web-Konfiguration nicht mit serverseitigen Geheimnissen
   verwechseln. Trotzdem vor jedem Commit auf versehentlich eingefügte
   private Tokens oder Testschlüssel prüfen.
4. **TODOs sind Aufträge, keine Deko.** Wer den betroffenen Bereich ändert,
   löst den TODO oder präzisiert ihn mit einem konkreten nächsten Schritt.
5. **Kommentare und Dokumentation auf Deutsch, Code-Bezeichner auf Englisch.**
6. **Kotlin-Stil:** offizielle Kotlin-Konventionen, vier Leerzeichen,
   `camelCase` für Funktionen/Variablen und `PascalCase` für Klassen. Neue
   Abhängigkeiten im Commit oder PR begründen.
7. **Sicherheitsrelevantes bevorzugt behandeln.** Änderungen an Android-
   Permissions, Supabase-RLS, Storage-Policies, Edge-Function-Authentifizierung,
   Upload-Zielen oder Push-Token-Verarbeitung besonders sorgfältig prüfen und
   im PR explizit nennen.
8. **Kleine, nachvollziehbare Commits.** Ein Commit enthält eine
   zusammenhängende Änderung. Format und Ablauf stehen in `CONTRIBUTING.md`.

## Verbindliche Schnittstellen

- Dashboard → `send-command`: JSON mit `deviceId` und `command`.
- FCM an das Zweithandy: data-only Payload mit `command`.
- Erlaubte Zweithandy-Befehle: `photo`, `audio`, `location`, `usage`.
- Tagesbericht an die Besitzer-App: data-only Payload mit
  `command=DAILY_REPORT`, `device_id` und `date`.
- Zentrale Supabase-Tabellen: `devices`, `locations`, `usage_logs`, `reports`,
  `owner` und `error_logs`.
- Geräte-FCM-Token: `devices.fcm_token`.
- Owner-Push-Tokens: `owner.fcm_token` für Web und
  `owner.android_fcm_token` für die Besitzer-App. Die beiden Kanäle nicht
  vermischen.
- Private Supabase-Storage-Buckets: `photos` und `audio`. Objektpfade werden
  vom Zweithandy unterhalb der jeweiligen Buckets geschrieben.
- `reports` ist pro Kombination aus `date` und `device_id` eindeutig; der
  Tagesbericht nutzt deshalb ein Upsert.

Wer eine dieser Schnittstellen ändert, muss Android-Apps, Dashboard, Edge
Functions, Migrationen, Tests und Dokumentation auf Konsistenz prüfen.

## Nicht ohne Rücksprache ändern

- Package-Name der Zweithandy-App (`com.ikianti.app`)
- grundlegendes Android-Berechtigungsmodell
- data-only Charakter der FCM-Fernbefehle und der nativen Tagesbericht-Pushs
- Trennung zwischen Zweithandy-App und Besitzer-App
- Supabase als persistentes Backend oder Firebase als FCM-Dienst
- RLS- und Storage-Zugriffsmodell

## Prüfen und testen

- Android-Änderungen mit dem Gradle-Wrapper unter `android-app/` bauen bzw.
  testen; beide Module (`app` und `owner`) berücksichtigen.
- Edge-Function-Tests mit Deno nach den Hinweisen in `docs/OWNER_APP.md`
  ausführen.
- Dashboard-/Owner-Prüfungen aus `tools/` verwenden, wenn der betroffene
  Bereich geändert wurde.
- Wenn ein Test mangels physischem Android-Gerät, Zugangsdaten oder externem
  Dienst nicht möglich ist, das im PR klar festhalten.

## Definition of Done

Eine Änderung ist fertig, wenn:

- sie zu den verbindlichen Schnittstellen passt oder alle betroffenen Stellen
  konsistent migriert wurden,
- keine privaten Secrets oder Test-Tokens im Diff stehen,
- relevante Builds und Tests erfolgreich liefen oder Einschränkungen
  dokumentiert sind,
- `docs/SETUP.md`, `docs/OWNER_APP.md`, README und Architektur-Dokumente bei
  geändertem Verhalten aktualisiert wurden,
- neue Schemaänderungen als nachvollziehbare Supabase-Migration vorliegen,
- offene TODOs im geänderten Bereich gelöst oder präzisiert wurden.
