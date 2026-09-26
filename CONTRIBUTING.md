# CONTRIBUTING

Prozessregeln, damit mehrere KIs oder Menschen nicht gleichzeitig denselben
Stand beschädigen. Inhaltliche und sicherheitsrelevante Regeln stehen in
`AGENTS.md`.

## Branches

- Nicht direkt auf `main` pushen, sobald mehrere Personen oder KIs parallel
  arbeiten.
- Ein Feature oder Fix entspricht einem Branch. Empfohlene Präfixe:
  - `app/...` für die Zweithandy-App unter `android-app/app/`
  - `owner/...` für die Besitzer-App unter `android-app/owner/`
  - `dashboard/...` für `dashboard/`
  - `supabase/...` für Edge Functions, Migrationen und Policies
  - `tools/...` für Prüf- und Hilfsskripte
  - `docs/...` für reine Dokumentationsänderungen

## Commits

- Format: `<bereich>: <kurze beschreibung>`, zum Beispiel
  `supabase: Auth-Prüfung in send-command ergänzt`.
- Ein Commit soll für sich nachvollziehbar und nach Möglichkeit build- bzw.
  testbar sein.
- Im Commit-Text angeben, wenn eine in `AGENTS.md` dokumentierte Schnittstelle,
  ein Berechtigungsmodell oder eine Security-Policy berührt wird.
- Schemaänderungen nicht nur direkt in Supabase vornehmen, sondern als neue
  Migration unter `supabase/migrations/` versionieren.

## Pull Requests

- Kurz beschreiben: Was wurde geändert, warum und wie wurde es getestet?
- Nicht durchführbare Tests und deren Grund nennen, etwa ein fehlendes
  physisches Android-Gerät oder fehlende externe Zugangsdaten.
- Bei Android-Änderungen angeben, welches Modul betroffen ist: `app`, `owner`
  oder beide.
- Bei Supabase-Änderungen Tabellen, RLS-/Storage-Policies, Edge Functions und
  erforderliche Deployments nennen.
- Vor dem Merge den Diff auf Secrets, private Tokens und echte Testdaten
  prüfen, auch wenn `.gitignore` greifen sollte.
- Geänderte Einrichtung oder Bedienung in `docs/SETUP.md`,
  `docs/OWNER_APP.md`, README oder der passenden Architektur-Doku nachziehen.

## Konflikte bei parallelen Änderungen

- Bestehende Arbeit nicht einfach überschreiben.
- Wenn Branches dieselbe Schnittstelle unterschiedlich ändern, den Konflikt
  im PR sichtbar machen und die Entscheidung dem Projektbesitzer überlassen.
- Vor dem Zusammenführen insbesondere Befehls-Payloads, Datenbankschema,
  Push-Token-Felder und Storage-Pfade vergleichen.
