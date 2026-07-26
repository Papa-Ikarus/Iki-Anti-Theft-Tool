const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

// ─────────────────────────────────────────────────────────────────────────────
// sendCommand
// Vom Dashboard aufgerufen (httpsCallable). Schickt einen FCM-Trigger
// an das Zweithandy.
// ─────────────────────────────────────────────────────────────────────────────
exports.sendCommand = functions.https.onCall(async (data, context) => {
  const { deviceId, command } = data;

  if (!deviceId || !command) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "deviceId und command sind erforderlich"
    );
  }

  const deviceDoc = await admin.firestore()
    .collection("devices").doc(deviceId).get();
  const fcmToken = deviceDoc.data()?.fcmToken;

  if (!fcmToken) {
    throw new functions.https.HttpsError(
      "not-found",
      "Kein FCM-Token für dieses Gerät gefunden"
    );
  }

  await admin.messaging().send({
    token: fcmToken,
    data: { command },
    android: { priority: "high" },
  });

  return { success: true };
});

// ─────────────────────────────────────────────────────────────────────────────
// dailyLocationReport
// Läuft täglich um 20:00 Uhr (Europe/Berlin).
// Liest alle Standortpunkte der letzten 24h aus Firestore,
// erstellt einen kompakten Bericht und schickt ihn als Push-Notification
// an das Dashboard (Browser auf Handy 1, via Web-Push FCM-Token).
// ─────────────────────────────────────────────────────────────────────────────
exports.dailyLocationReport = functions.pubsub
  .schedule("0 3 * * *")            // täglich 03:00 Uhr
  .timeZone("Europe/Berlin")
  .onRun(async () => {
    const DEVICE_ID = "phone-1";
    const now = Date.now();
    const since = now - 24 * 60 * 60 * 1000; // letzte 24h

    // 1. Standortpunkte der letzten 24h laden
    const snapshot = await admin.firestore()
      .collection("devices")
      .doc(DEVICE_ID)
      .collection("locations")
      .where("timestamp", ">=", since)
      .orderBy("timestamp", "asc")
      .get();

    const locations = snapshot.docs.map(doc => doc.data());

    // 2. Bericht zusammenstellen
    const count = locations.length;

    if (count === 0) {
      console.log("Keine Standortdaten in den letzten 24h.");
      await sendOwnerNotification(
        "📍 Tagesbericht",
        "Heute keine Standortdaten empfangen.",
        null
      );
      return null;
    }

    const first = locations[0];
    const last  = locations[locations.length - 1];

    const startTime = new Date(first.timestamp).toLocaleTimeString("de-DE", {
      hour: "2-digit", minute: "2-digit", timeZone: "Europe/Berlin"
    });
    const endTime = new Date(last.timestamp).toLocaleTimeString("de-DE", {
      hour: "2-digit", minute: "2-digit", timeZone: "Europe/Berlin"
    });

    // Google Maps Link mit bis zu 10 Wegpunkten (URL-Limit)
    const mapsUrl = buildMapsUrl(locations);

    const title = `📍 Tagesbericht – ${count} Standorte`;
    const body  = `Erster: ${startTime} Uhr · Letzter: ${endTime} Uhr`;

    // 3. Notification an Owner-Browser schicken
    await sendOwnerNotification(title, body, mapsUrl);

    // 4. Bericht auch in Firestore speichern (für Dashboard-Archiv)
    await admin.firestore()
      .collection("reports")
      .add({
        date: new Date().toISOString().split("T")[0], // YYYY-MM-DD
        deviceId: DEVICE_ID,
        locationCount: count,
        firstTimestamp: first.timestamp,
        lastTimestamp: last.timestamp,
        firstLat: first.lat,
        firstLng: first.lng,
        lastLat: last.lat,
        lastLng: last.lng,
        mapsUrl,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

    console.log(`Tagesbericht gesendet: ${count} Standorte.`);
    return null;
  });

// ─────────────────────────────────────────────────────────────────────────────
// Hilfsfunktionen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Baut eine Google Maps Directions-URL mit bis zu 10 gleichmäßig
 * verteilten Wegpunkten aus der Standortliste.
 */
function buildMapsUrl(locations) {
  if (locations.length === 0) return null;
  if (locations.length === 1) {
    const { lat, lng } = locations[0];
    return `https://www.google.com/maps?q=${lat},${lng}`;
  }

  // Maximal 10 Punkte (Google Maps URL-Limit für Wegpunkte)
  const maxPoints = 10;
  const step = Math.max(1, Math.floor(locations.length / maxPoints));
  const sampled = [];
  for (let i = 0; i < locations.length; i += step) {
    sampled.push(locations[i]);
    if (sampled.length >= maxPoints) break;
  }
  // Letzten Punkt immer mitaufnehmen
  if (sampled[sampled.length - 1] !== locations[locations.length - 1]) {
    sampled.push(locations[locations.length - 1]);
  }

  const origin      = `${sampled[0].lat},${sampled[0].lng}`;
  const destination = `${sampled[sampled.length - 1].lat},${sampled[sampled.length - 1].lng}`;
  const waypoints   = sampled.slice(1, -1)
    .map(p => `${p.lat},${p.lng}`)
    .join("|");

  let url = `https://www.google.com/maps/dir/${origin}/${destination}`;
  if (waypoints) url += `/@?api=1&waypoints=${encodeURIComponent(waypoints)}`;
  return url;
}

/**
 * Schickt eine Web-Push-Notification an das Dashboard (Handy 1).
 * Der Web-Push-Token wird aus Firestore gelesen (Dokument "owner/dashboard").
 */
async function sendOwnerNotification(title, body, mapsUrl) {
  const ownerDoc = await admin.firestore()
    .collection("owner").doc("dashboard").get();
  const webPushToken = ownerDoc.data()?.fcmToken;

  if (!webPushToken) {
    console.warn("Kein Web-Push-Token für Owner registriert – Notification übersprungen.");
    return;
  }

  const message = {
    token: webPushToken,
    notification: { title, body },
    webpush: {
      notification: {
        title,
        body,
        icon: "/icon-192.png",
        badge: "/icon-192.png",
        requireInteraction: true,   // bleibt sichtbar bis der Nutzer tippt
      },
      fcmOptions: mapsUrl ? { link: mapsUrl } : {},
    },
  };

  try {
    await admin.messaging().send(message);
    console.log("Owner-Notification gesendet.");
  } catch (err) {
    console.error("Owner-Notification fehlgeschlagen:", err);
  }
}
