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

## Android-Rückmeldungen für Remote-Befehle

`send-command` überträgt neben `command_id` einen zufälligen,
befehlsbezogenen Rückmeldetoken per FCM. Die Datenbank speichert
ausschließlich dessen SHA-256-Hash und einen Ablaufzeitpunkt nach
24 Stunden. Tokens dürfen nicht protokolliert werden.

Die Edge Function `command-ack` prüft Token, Gerätezuordnung und
Ablaufzeit. Direkter Client-Zugriff auf `commands` bleibt gesperrt.
Statusänderungen erfolgen gegen den aktuell gelesenen Status;
verspätete Zwischenmeldungen setzen ihn nicht zurück und ein
Endstatus kann nicht durch einen anderen ersetzt werden.

Android meldet `received`, `running` und abschließend `success`,
`error` oder `timeout`. Fehler und Timeouts werden erst nach
Ausschöpfung der maximal drei Ausführungsversuche endgültig gemeldet.
Nach einem Neustart wird ein bereits ausgeschöpfter Befehl mit
unbekanntem Ausgang als `error` abgeschlossen.

Rückmeldungen werden vor dem Versand in einer privaten lokalen Queue
gespeichert. Vorübergehende Versandfehler behalten den Eintrag.
Der Foreground-Service versucht den Versand beim Heartbeat erneut.
Endgültig abgelehnte Rückmeldungen werden entfernt.
Befehle ohne Rückmeldetoken bleiben ausführbar, melden aber keinen Status.

Die Speicherung verwendet private SharedPreferences. Die vorhandenen
Backup-Regeln schließen diese von Cloud-Backups und Gerätetransfers aus.

### Bestätigter Teststand vom 02.10.2026

- Android-Debug-Build und Lint erfolgreich.
- 14 lokale Handler-Testfälle erfolgreich, einschließlich falscher
  Tokens, Gerätezuordnung, Ablaufzeit und konkurrierender Updates.
- Standortbefehle erreichen `success` mit Empfangs-, Start- und Abschlusszeit.
- Fehlende Standortdaten führen nach drei Versuchen zu `error`.
- Offline-Nachversand bestätigt: Ein lokal um 13:01:06 Uhr
  abgeschlossener Befehl wurde nach Wiederherstellung der Verbindung
  um 13:02:57 Uhr im Backend abgeschlossen (Europe/Berlin).
- `finished_at` bezeichnet den serverseitigen Eingang der Abschlussmeldung.
- Prozessneustart während eines Offline-Abschlusses und der endgültige
  Timeoutpfad wurden noch nicht praktisch getestet.
- Eine exakt einmalige Befehlsausführung wird nicht garantiert.

### Deploymentstand

Die Migration `20260930145210_command_ack_token.sql` ist angewendet.
`command-ack` und `send-command` sind deployt; die aktualisierte
Remote-App wurde auf dem Zweithandy getestet.

Vor einer erneuten Anwendung die Migrationshistorie abgleichen.
Die bereits angewendete Migration nicht verändern oder erneut ausführen.

## Befehlsverlauf in Iki Control

Unterhalb der Steuerung zeigt „Befehlsverlauf“ die letzten 50 Befehle
des ausgewählten Geräts, neueste zuerst. Angezeigt werden Befehl,
Status, Anforderungszeit, gegebenenfalls Abschlussmeldung und Befehls-ID.

Der Verlauf lädt beim Gerätewechsel und lässt sich manuell aktualisieren.
Bei sichtbarem Dashboard wird er außerdem alle zehn Sekunden aktualisiert.
Verspätete Antworten für zuvor ausgewählte Geräte werden verworfen.

Die Edge Function `command-history` prüft die Sitzung online und erlaubt
ausschließlich den konfigurierten Besitzer `IKI_OWNER_USER_ID`.
Direkter Client-Zugriff auf `commands` bleibt gesperrt.
Rückmeldetokens, Token-Hashes und freie Fehlertexte werden nicht ausgegeben.

Die angezeigte Abschlusszeit bezeichnet den serverseitigen Eingang
der Abschlussmeldung. „Gesendet“ bestätigt noch keine Ausführung.

Lokale Prüfungen:
- Dashboard-JavaScript besteht die Syntaxprüfung.
- 13 Handler-Testfälle für Autorisierung und sichere Ausgabe erfolgreich.
- Dashboard-Tests für sichere Textausgabe, Gerätewechsel,
  Fehleranzeigen und automatische Aktualisierung erfolgreich.

Praktischer Test am 02.10.2026 bestätigt:
- Vorhandene Befehle werden in Iki Control angezeigt.
- Ein neuer Standortbefehl wird automatisch als „Erfolgreich“ angezeigt.
- Gerätewechsel zwischen zwei echten Remote-Geräten mangels zweitem Gerät
  nicht praktisch getestet; lokal mit simulierten Antworten geprüft.

`command-history` und das Dashboard sind veröffentlicht.

## Standortverlauf mit Tagesauswahl

Iki Control zeigt den Standortverlauf des ausgewählten Geräts für
einen gewählten Kalendertag. Tagesgrenzen und Uhrzeiten entsprechen
der lokalen Zeitzone des Browsers; Zeitumstellungen werden berücksichtigt.

Die Ansicht enthält:
- Tagesauswahl und einen Heute-Button
- chronologisch verbundene Standortpunkte
- Start- und Endmarkierung
- Datum, Uhrzeit und Koordinaten beim Anklicken eines Punkts
- Punktanzahl sowie erste und letzte Uhrzeit

Bei einem Tag ohne Standortpunkte wird die bisherige Tagesroute entfernt.
Verspätete Antworten überschreiben keinen neu gewählten Tag oder
Geräteverlauf. Längere Verläufe werden seitenweise geladen.

Der aktuelle Standortmarker zeigt den gespeicherten Standortzeitpunkt.
Die Google-Maps-Routenlinks in den Tagesberichten bleiben verfügbar.

Lokale Prüfungen für Tagesgrenzen, Zeitumstellung, leere Tage,
Pagination, Wechsel, Kartenansicht und Fehler sind erfolgreich.
Der bestehende Befehlsverlauf wurde ebenfalls erfolgreich geprüft.

Praktischer Kartentest auf der Firebase-Vorschau und anschließend im produktiven Dashboard erfolgreich:
Heute, vorhandener und leerer Tag, schneller Tageswechsel sowie
gespeicherter Zeitstempel des aktuellen Standortmarkers.
Gerätewechsel mit zwei echten Remote-Geräten weiterhin nicht praktisch getestet.

## Ungültige FCM-Tokens der Remote-Geräte

Bei einem FCM-Fehler mit `UNREGISTERED` leert `send-command` den
betroffenen Eintrag in `devices.fcm_token`.

Das Update prüft Geräte-ID und den beim Versand verwendeten Token
gemeinsam. Ein zwischenzeitlich erneuerter Token bleibt erhalten.
Gerätedatensatz und Verlauf werden nicht gelöscht.

Da die Spalte `NOT NULL` ist, kennzeichnet eine leere Zeichenfolge
den fehlenden Token. Weitere Befehle werden mit `NO_FCM_TOKEN`
abgewiesen, bis Android wieder einen Token registriert.

Eine erfolgreiche Bereinigung oder ein inzwischen geänderter Token
führt zur bisherigen Antwort `FCM_TOKEN_INVALID` mit HTTP 410.
Bei einem Datenbankfehler wird `FCM_TOKEN_CLEANUP_FAILED` mit HTTP 503
zurückgegeben. Tokens und freie Datenbankfehlertexte werden nicht geloggt.

`node tools/test-fcm-token-cleanup.cjs` prüft lokal sechs Fälle:
Bereinigung, zwischenzeitliche Erneuerung, bereits leerer Token,
fehlendes Zielgerät, übersprungene Bereinigung und Datenbankfehler.

Ein praktischer Test mit einem tatsächlich ungültigen FCM-Token
steht noch aus.
