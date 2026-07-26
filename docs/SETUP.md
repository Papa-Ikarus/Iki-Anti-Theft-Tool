# Setup

## 1. Firebase-Projekt anlegen

1. https://console.firebase.google.com → "Projekt hinzufügen"
2. Firestore Database aktivieren (im Testmodus starten, Regeln später verschärfen)
3. Storage aktivieren
4. Cloud Messaging ist automatisch aktiv
5. Functions aktivieren (Blaze-Plan nötig, hat aber ein kostenloses Kontingent)

## 2. Android-App verbinden

1. In der Firebase Console: App hinzufügen → Android
2. Package-Name: `com.ikianti.app`
3. `google-services.json` herunterladen und ablegen unter:
   `android-app/app/google-services.json`
   (Datei ist in `.gitignore`, wird nicht committet)
4. Projekt in Android Studio öffnen (`android-app/` als Projektordner)
5. TODOs in `CameraCapture.kt` bezüglich LifecycleOwner lösen (für
   Kamera-Zugriff ohne sichtbare Activity)
6. App auf das Zweithandy installieren, Permissions beim ersten Start
   erlauben

## 3. Cloud Function deployen

```bash
cd functions
npm install
firebase login
firebase init functions   # falls noch nicht geschehen, bestehendes Projekt wählen
firebase deploy --only functions
```

## 4. Dashboard konfigurieren & deployen

1. In `dashboard/index.html` die `firebaseConfig`-Werte aus der
   Firebase Console eintragen (Projekteinstellungen → "Meine Apps" →
   Web-App hinzufügen)
2. Google Maps API-Key erstellen (Google Cloud Console → APIs &
   Dienste → Anmeldedaten) und in `index.html` eintragen
3. Deployen:

```bash
firebase init hosting     # dashboard/ als public-Ordner wählen
firebase deploy --only hosting
```

## 5. Firebase Auth einrichten (Pflicht vor dem ersten echten Betrieb)

Die Sicherheitsregeln (Firestore + Storage) basieren auf deiner
persönlichen Firebase-UID. Ohne Auth ist Firestore offen für alle.

1. Firebase Console → Authentication → "Jetzt loslegen"
2. Anmeldemethode aktivieren: E-Mail/Passwort
3. Einen Nutzer anlegen (deine eigene E-Mail-Adresse + sicheres Passwort)
4. Danach in der Nutzerliste deine **UID** kopieren (lange Zeichenkette)
5. UID in zwei Dateien eintragen (Platzhalter `DEINE-FIREBASE-UID-HIER`):
   - `firestore.rules` (Zeile mit `request.auth.uid ==`)
   - `storage.rules` (Zeile mit `request.auth.uid ==`)
6. Regeln deployen:

```bash
firebase deploy --only firestore:rules,storage
```

## 6. Alle Dienste auf einmal deployen

```bash
firebase deploy
```

Deployed: Firestore-Regeln, Storage-Regeln, Cloud Functions, Hosting.

## 7. Firestore-Index deployen (für Standort-Abfragen)

Der Index für die Standort-Sortierung nach Zeitstempel liegt in
`firestore.indexes.json` und wird mit `firebase deploy` automatisch
mitdeployed. Alternativ einzeln:

```bash
firebase deploy --only firestore:indexes
```

## 8. Stealth-Modus

Nach dem ersten Start der App auf dem Zweithandy (Permissions erlauben +
Firebase-Registrierung) schließt sich die Activity selbst und deaktiviert
das Launcher-Icon. Die App verschwindet danach aus der App-Schublade.

**Was weiterhin funktioniert:**
- FCM-Empfang im Hintergrund (keine dauerhaft laufenden Prozesse nötig)
- Auto-Start nach Neustart via Boot-Receiver

**Während einer Aufnahme (Kamera/Audio):**
Android 9+ erzwingt eine sichtbare Notification für diese Zeit (~10 Sek.).
Sie erscheint als "Systemdienst" ganz unten in der Benachrichtigungsleiste,
ohne Ton oder Vibration. Das ist eine Android-Systemvorschrift und lässt
sich nicht umgehen.

**App erneut öffnen (z.B. für Updates):**
```bash
adb shell am start -n com.ikianti.app/.MainActivity
```
Oder den LauncherAlias per ADB wieder aktivieren:
```bash
adb shell pm enable com.ikianti.app/.LauncherAlias
```

## 9. Täglicher Standort-Bericht (Push auf Handy 1)

Der Bericht läuft als geplante Cloud Function täglich um 20:00 Uhr
(Europe/Berlin) und schickt eine Push-Notification ans Dashboard-Browser
auf Handy 1.

### Einmalige Einrichtung auf Handy 1

1. Dashboard im Browser öffnen (Firebase Hosting URL)
2. Auf "Push-Benachrichtigungen aktivieren" tippen
3. Browser-Permission erlauben
4. Fertig – ab jetzt kommt täglich um 20:00 Uhr eine Notification

### VAPID-Key eintragen

Für Web-Push braucht Firebase einen VAPID-Key:
1. Firebase Console → Projekteinstellungen → Cloud Messaging
2. Unter "Web-Push-Zertifikate" → "Schlüsselpaar generieren"
3. Den erzeugten Key in `dashboard/index.html` bei `VAPID_KEY` eintragen
4. Den gleichen Key in `dashboard/firebase-messaging-sw.js` ist nicht nötig
   (der SW nutzt die Messaging-Config)

### Was der Bericht enthält

- Anzahl der Standortpunkte des Tages
- Erster und letzter Zeitpunkt
- Direktlink zu Google Maps mit der Route (bis zu 10 Wegpunkte)

### Berichte im Dashboard

Die letzten 7 Tagesberichte werden im Dashboard unter "Tagesberichte"
angezeigt, mit direktem Maps-Link pro Tag.

### Uhrzeit anpassen

In `functions/index.js` die Cron-Zeile ändern:
```js
.schedule("0 20 * * *")  // täglich 20:00 Uhr
.schedule("0 8 * * *")   // täglich 08:00 Uhr
```
