const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

/**
 * Vom Dashboard aufgerufen (httpsCallable). Holt den FCM-Token des
 * Zielgeräts aus Firestore und schickt eine "data only" Push-Nachricht
 * (kein "notification"-Feld -> kommt still an, App entscheidet selbst,
 * ob/wie sie eine Notification zeigt).
 *
 * TODO: Auth ergänzen (z.B. context.auth prüfen), damit nicht jeder
 * mit der Function-URL beliebige Befehle auslösen kann.
 */
exports.sendCommand = functions.https.onCall(async (data, context) => {
  const { deviceId, command } = data;

  if (!deviceId || !command) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "deviceId und command sind erforderlich"
    );
  }

  const deviceDoc = await admin.firestore().collection("devices").doc(deviceId).get();
  const fcmToken = deviceDoc.data()?.fcmToken;

  if (!fcmToken) {
    throw new functions.https.HttpsError("not-found", "Kein FCM-Token für dieses Gerät gefunden");
  }

  await admin.messaging().send({
    token: fcmToken,
    data: { command },
    android: { priority: "high" },
  });

  return { success: true };
});
