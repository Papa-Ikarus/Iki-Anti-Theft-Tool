# CONTRIBUTING

Prozessregeln, damit mehrere KIs (oder Menschen) nicht gleichzeitig
denselben Stand kaputt machen. Inhaltliche Regeln stehen in
`AGENTS.md` – diese Datei hier ist nur Ablauf.

## Branches

- Nicht direkt auf `main` pushen, sobald mehr als eine KI gleichzeitig
  am Projekt arbeitet.
- Ein Feature/Fix = ein Branch, benannt nach Bereich:
  - `app/...` für Änderungen in `android-app/`
  - `dashboard/...` für Änderungen in `dashboard/`
  - `functions/...` für Änderungen in `functions/`
  - `docs/...` für reine Doku-Änderungen
  - Beispiel: `app/camera-lifecycle-fix`

## Commits

- Format: `<bereich>: <kurze beschreibung>`
  Beispiel: `functions: Auth-Check in sendCommand ergänzt`
- Ein Commit soll für sich genommen nachvollziehbar und – wenn möglich
  – buildbar sein.
- Im Commit-Text kurz erwähnen, wenn eine der in `AGENTS.md`
  gelisteten Schnittstellen (FCM-Format, Firestore-Struktur,
  Storage-Pfade) berührt wird.

## Pull Requests

- Kurze Beschreibung: was wurde geändert, warum, was wurde getestet
  (bzw. was noch nicht getestet werden konnte, z. B. weil kein
  physisches Testgerät verfügbar war).
- Vor dem Merge: Diff auf versehentlich eingefügte Secrets/Keys
  prüfen (auch wenn `.gitignore` greifen sollte – doppelt hält besser).

## Bei Konflikten zwischen mehreren KI-Änderungen

- Nicht einfach überschreiben. Wenn zwei Branches dieselbe Schnittstelle
  unterschiedlich ändern wollen, das im PR-Text explizit benennen,
  damit ein Mensch (Matze) entscheidet, welche Richtung weiterverfolgt
  wird.
