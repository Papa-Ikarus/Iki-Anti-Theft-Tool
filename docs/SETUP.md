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

## 5. Firestore-Sicherheitsregeln (wichtig!)

Im Testmodus ist Firestore anfangs offen für alle. Vor dem produktiven
Einsatz unbedingt Regeln setzen, die nur authentifizierten Zugriff
erlauben (z.B. Firebase Auth mit deinem eigenen Account), sonst kann
theoretisch jeder deine Standortdaten lesen oder Befehle auslösen.
