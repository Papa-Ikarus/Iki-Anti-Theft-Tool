# Setup

## Überblick: Was wofür zuständig ist

| Dienst    | Zweck                                      | Kosten  |
|-----------|--------------------------------------------|---------|
| Firebase  | Nur FCM (Push-Nachrichten)                 | Kostenlos (Spark-Plan) |
| Supabase  | Datenbank, Storage (Fotos/Audio), Auth     | Kostenlos (Free-Tier)  |

---

## 1. Firebase-Projekt einrichten (nur für FCM)

1. https://console.firebase.google.com → Projekt anlegen
2. Android-App hinzufügen, Package: `com.ikianti.app`
3. `google-services.json` herunterladen → ablegen unter `android-app/app/`
4. Web-App hinzufügen → Firebase-Config notieren (für Dashboard)
5. Cloud Messaging → Web-Push-Zertifikat generieren → VAPID-Key notieren
6. Firebase Service Account herunterladen:
   - Projekteinstellungen → Service Accounts → "Neuen privaten Schlüssel generieren"
   - JSON-Datei sicher aufbewahren (wird als Supabase Secret gespeichert)

---

## 2. Supabase-Projekt einrichten

1. https://supabase.com → kostenloses Konto + neues Projekt anlegen
2. Region: **eu-central-1** (Frankfurt)
3. Nach Erstellung: Project Settings → API → notieren:
   - **Project URL** (z.B. https://xyz.supabase.co)
   - **anon/public key**
   - **service_role key** (nur für Edge Functions)

---

## 3. Datenbank + Storage einrichten

1. Supabase Dashboard → SQL Editor
2. Inhalt von `supabase/migrations/001_init.sql` einfügen und ausführen
3. Danach unter Storage prüfen, ob die Buckets `photos` und `audio` angelegt wurden

---

## 4. Supabase Auth einrichten

1. Authentication → Users → "Invite user" (oder "Add user")
2. Deine eigene E-Mail-Adresse + sicheres Passwort
3. Diese Zugangsdaten verwendest du zum Einloggen im Dashboard (Handy 1)

---

## 5. TODOs in den Dateien befüllen

### Android-App (`android-app/app/src/main/java/com/ikianti/app/SupabaseApi.kt`)
```
SUPABASE_URL      → deine Project URL
SUPABASE_ANON_KEY → dein anon/public key
```

### Dashboard (`dashboard/index.html`)
```
SUPABASE_URL      → deine Project URL
SUPABASE_ANON_KEY → dein anon/public key
FIREBASE_CONFIG   → aus Firebase Console (Web-App)
VAPID_KEY         → aus Firebase Console (Cloud Messaging)
EDGE_URL          → deine Project URL + /functions/v1/send-command
Google Maps Key   → aus Google Cloud Console
```

### Service Worker (`dashboard/firebase-messaging-sw.js`)
```
Firebase-Config   → gleiche Werte wie in index.html
```

---

## 6. Supabase Edge Functions deployen

```bash
npm install -g supabase
supabase login
supabase link --project-ref DEINE_PROJECT_REF

# Secrets setzen
supabase secrets set FIREBASE_PROJECT_ID=dein-projekt-id
supabase secrets set FIREBASE_SERVICE_ACCOUNT='{ ... JSON ... }'

# Deployen
supabase functions deploy send-command
supabase functions deploy daily-report
```

---

## 7. Täglichen Bericht um 03:00 Uhr einrichten (cron-job.org)

1. https://cron-job.org → kostenloses Konto
2. Neuer Cronjob:
   - URL: `https://DEINE_PROJECT_URL/functions/v1/daily-report`
   - Header: `Authorization: Bearer DEIN_SERVICE_ROLE_KEY`
   - Zeitplan: **täglich 03:00 Uhr** (02:00 UTC)
3. Speichern → fertig

---

## 8. Dashboard deployen (Firebase Hosting)

```bash
npm install -g firebase-tools
firebase login
firebase init hosting   # dashboard/ als public-Ordner wählen
firebase deploy --only hosting
```

---

## 9. Android-App bauen und installieren

1. `android-app/` in Android Studio öffnen
2. `google-services.json` muss unter `android-app/app/` liegen
3. App auf das Zweithandy installieren
4. Beim ersten Start: alle Permissions erlauben
5. App registriert sich und versteckt das Icon automatisch

---

## 10. Push-Benachrichtigungen auf Handy 1 aktivieren

1. Dashboard im Browser öffnen (Firebase Hosting URL)
2. Mit E-Mail + Passwort einloggen
3. Auf "Push aktivieren" tippen, Browser-Permission erlauben
4. Ab jetzt: täglich um 03:00 Uhr kommt der Tagesbericht

---

## App erneut öffnen (nach Icon-Versteckung)

```bash
adb shell am start -n com.ikianti.app/.MainActivity
```
