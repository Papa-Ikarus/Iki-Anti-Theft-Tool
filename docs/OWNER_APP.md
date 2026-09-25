# Iki Besitzer: Dashboard und native Tagesberichte

Die neue Android-App `com.ikianti.owner` ist ausschließlich für das normale
Besitzer-Handy. Das Modul `android-app/owner` lädt das vorhandene Dashboard in
einer WebView und empfängt native FCM-Tagesberichte. Die Testhandy-App im Modul
`app` bleibt unverändert. Die Besitzer-App registriert sich niemals in `devices`
und besitzt keine Kamera-, Mikrofon-, Standort- oder Nutzungsstatistik-Berechtigung.

## Einrichtung

1. Im bestehenden Firebase-Projekt eine weitere Android-App mit Paketnamen
   `com.ikianti.owner` registrieren. Deren `google-services.json` lokal unter
   `android-app/owner/` ablegen. Nicht die Datei der Testhandy-App kopieren.
   Ohne diese Datei ist ein Dashboard-Build möglich; Push meldet ausdrücklich
   die fehlende Konfiguration. Die Datei ist von Git ausgeschlossen.
2. Die neue Migration `20260923150620_owner_android_push.sql` zunächst in einer
   Testdatenbank prüfen, dann auf dem verbundenen Projekt anwenden. Sie ergänzt
   `owner.android_fcm_token`. Bestehende Web-Felder bleiben schreibbar; die neue
   Spalte und das Löschen des Owner-Datensatzes sind für Clients gesperrt.
3. Als Server-Konfiguration `IKI_OWNER_USER_ID` auf die UUID des bestehenden
   Supabase-Besitzer-Kontos setzen. Das ist die Benutzer-ID aus Auth, keine
   E-Mail-Adresse und nicht die Geräte-ID. Die Registrierung prüft eine echte
   Sitzung mit Supabase Auth und lässt ausschließlich diese UUID zu.
4. `IKI_DASHBOARD_ORIGIN` ist standardmäßig `https://iki-anti-theft.web.app`.
   Bei einer anderen Hosting-Adresse diesen Wert und `DASHBOARD_URL` im
   Owner-Modul gemeinsam anpassen. Nur dieser HTTPS-Ursprung bekommt Zugriff
   auf die native Token-Schnittstelle; Unterframes werden abgewiesen.
5. Deployments getrennt freigeben. Der vorbereitete `daily-report` erhält Web-Push
   und sendet zusätzlich Android-Push bei vorhandenem Owner-Token.
   `DAILY_REPORT_PUSH_CHANNEL` wird nicht mehr ausgewertet; kein Secret-Wechsel nötig.
6. Im Verzeichnis `android-app` mit `gradlew.bat :owner:assembleDebug
   :owner:lintDebug` bauen. Die APK liegt unter
   `owner/build/outputs/apk/debug/owner-debug.apk`. Auf dem normalen Handy
   installieren, Benachrichtigungen erlauben und mit dem bisherigen
   Dashboard-Konto anmelden. Die Weboberfläche zeigt den Registrierungsstatus.

## Dualer Tagesbericht-Push

- FCM-Test als Data-Message an das registrierte Besitzer-Token senden:
  `command=DAILY_REPORT`, `device_id=<vorhandenes Testgerät>`, `date=YYYY-MM-DD`.
  Ohne `notification`-Payload senden. Eine native Meldung muss erscheinen;
  Antippen muss den passenden Tagesbericht im Dashboard öffnen.
- Erst nach Freigabe und Deployment einen kontrollierten Tagesbericht auslösen.
  Web verwendet `owner.fcm_token`, Android ausschließlich `owner.android_fcm_token`,
  jeweils aus dem festen Datensatz `id=dashboard`. Fehlende Tokens werden übersprungen.
- Genau eine Meldung pro Gerät und Berichtstag prüfen. Die Android-App merkt
  sich kürzlich zugestellte Berichte auch über Prozessneustarts. Für einen
  erneuten Test desselben Berichts App-Daten im Testaufbau zurücksetzen oder
  ein anderes Berichtsdatum verwenden.
- Browser und Iki Control erhalten jeweils ihre eigene Meldung. Jeder Kanal hat
  eigene Fehlerbehandlung und begrenzte Netzwerk-Timeouts. Fehlgeschlagene OAuth-
  Anforderungen sperren den anderen Kanal nicht; erfolgreiche OAuth-Tokens werden
  im selben Lauf wiederverwendet. Fehlerantworten werden nicht geloggt.
- `push_sent` bezeichnet weiterhin Web; `android_push_sent` meldet separat die
  FCM-Annahme für Android. Annahme ist keine Empfangsbestätigung.
- Ohne Standortdaten bleibt das Verhalten unverändert: kein gespeicherter Report,
  aber ein Push-Versuch. Android kann deshalb auf eine leere Berichtsliste führen.
- Die vorhandene Android-Anzeige bleibt: „Tagesbericht · Datum“ und
  „Bericht für Gerät öffnen“. Gerät und Berichtsdatum kommen aus der Data-Payload.

## Grenzen der ersten Version

- Internet wird für Dashboard und Anmeldung benötigt; bei Ladefehlern gibt es
  einen Hinweis und eine Schaltfläche zum erneuten Laden.
- Ein Besitzer-Handy pro bestehendem Owner-Datensatz. Eine Registrierung auf
  einem zweiten Besitzer-Handy ersetzt den ersten Android-Empfänger.
- Token-Erneuerungen werden beim nächsten Öffnen/Fortsetzen der App mit der
  vorhandenen Web-Anmeldung registriert. Bis dahin kann Push ausbleiben.
- Noch keine eigene Abmelde-/Entkoppel-Oberfläche. Vor Weitergabe eines Handys
  den Android-Token serverseitig entfernen und die App-Daten löschen.
- Die App ist auf das bereits vorhandene private Ein-Besitzer-Dashboard
  zugeschnitten. Die älteren Zugriffspolicies für dessen übrige Tabellen wurden
  nicht zu einem Mehrbenutzersystem umgebaut.
- Keine Garantie für exakt einmaligen Transport durch FCM. Wiederholte
  Cron-Aufrufe senden weiterhin erneut; die App unterdrückt doppelte Anzeigen.

## Untersuchung der mehrfachen Brave-Meldungen

Im bisherigen Versand enthielt die FCM-Nachricht eine Notification-Payload.
Firebase zeigt diese im Hintergrund automatisch an. Zusätzlich rief der
Service Worker `showNotification` auf. Dieser zweite Anzeigeweg wurde für
Notification-Payloads entfernt; reine Data-Payloads kann der Worker weiterhin
anzeigen. Das erklärt einen Verdopplungspfad, aber nicht allein vier Meldungen.
Für die vollständige Ursache sind Cron-Historie und Edge-Function-Protokolle
zu vergleichen. Pro Funktionsaufruf läuft genau eine Sendeschleife über die
registrierten Geräte; erneute Aufrufe sind derzeit nicht serverseitig dedupliziert.
Die am 23.09.2026 abgefragten Supabase-Protokolle der vorherigen 24 Stunden
zeigen einen erfolgreichen Aufruf von `daily-report` (HTTP 200). Für diesen
Zeitraum ist eine mehrfache Cron-Ausführung somit nicht belegt; ältere
Vierfach-Meldungen sind dadurch nicht abschließend erklärt.

## Prüfungen

`deno test supabase/functions/register-owner-push/handler_test.ts` prüft
Authentifizierung, Besitzerfreigabe, Fehlerfälle und Versandkanäle.
`node tools/test-owner-dashboard.cjs` prüft den doppelten Anzeigeweg.
Nach `npm ci --prefix tools/owner-tests` prüfen
`node tools/owner-tests/check-dashboard.mjs` die native Web-Anbindung und
`node tools/owner-tests/check-migration.mjs` die Migration mit PostgreSQL/PGlite.
PGlite und LinkeDOM sind ausschließlich Testabhängigkeiten; die Besitzer-App
und das Dashboard verwenden sie nicht zur Laufzeit.
Android-Build, Lint sowie echte Anmeldung, FCM-Zustellung und Antippen auf einem
Handy sind zusätzlich erforderlich. Lokale Tests ersetzen keine Live-Prüfung.

## Dokumentation

- https://developer.android.com/reference/androidx/webkit/WebViewCompat
- https://firebase.google.com/docs/cloud-messaging/android/receive-messages
- https://supabase.com/docs/reference/javascript/auth-getuser

## Tests für dualen Versand

`deno test --no-config --allow-read=supabase/functions/daily-report supabase/functions/daily-report/send-push_test.ts supabase/functions/daily-report/report-integration_test.ts supabase/functions/register-owner-push/handler_test.ts`

Die Tests verwenden lokale Doubles, keine Produktionszugriffe. Sie prüfen Token-Kombinationen,
getrennte FCM-/OAuth-/Netzwerkfehler, bereinigte Logs sowie den echten Berichtslauf
mit zwei fehlgeschlagenen Push-Kanälen und den Sonderfall ohne Standortdaten.

## Bestätigter Produktionsstand (25.09.2026)

- `register-owner-push` v1 ist produktiv und erfolgreich getestet.
- `daily-report` v19 versendet parallel Web-Push und nativen Android-Push.
- Ein echter nativer Tagesbericht wurde auf Iki Control empfangen; Antippen
  öffnete den zugehörigen Tagesbericht für das richtige Gerät und Datum.
- Der Owner-UI-Fix wurde erfolgreich getestet: Nach bestätigter Registrierung
  bleibt der Erfolgstext sichtbar und der Verbindungsbutton ist ausgeblendet.
- Nach Firebase-Hosting-Deployments kann die Iki-Control-WebView alten HTML-/JS-Code
  aus dem Cache anzeigen. Bei scheinbar altem Verhalten zuerst den App-Cache
  leeren und erneut testen.

## Bereits angewendete Owner-Migration

Die lokale Datei `20260923150620_owner_android_push.sql` wurde in Produktion
als `20260924145251 — owner_android_push` registriert und ist bereits angewendet.
Bei späteren CLI-/Migration-Läufen zuerst die lokale und produktive Historie
abgleichen; diese Migration nicht blind erneut ausführen. Die bereits angewendete
Produktionsmigration nicht ändern. Dieser Dokumentationsstand nimmt keine
Änderung an der produktiven Migrationshistorie vor.

## Feste Supabase-Entwicklungsregel

Bei jeder NEUEN Tabelle müssen explizite Data-API-GRANTs sowie RLS und passende
Policies bewusst geprüft und definiert werden. Nicht auf automatische
Standardrechte verlassen. Die Owner-Migration erweitert dagegen eine bestehende
Tabelle und erstellt keine neue Tabelle.