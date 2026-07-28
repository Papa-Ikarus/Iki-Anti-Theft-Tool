// Service Worker für Web-Push-Empfang im Hintergrund (Handy 1 / Dashboard-Browser)
// Diese Datei MUSS im Root des Hosting-Ordners liegen.

importScripts("https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js");

// TODO: gleiche Firebase-Config wie in index.html eintragen
firebase.initializeApp({
  apiKey: "AIzaSyAT_Ggfx13HUyiFlRSIMqcPFHnkSI6z9h0",
  authDomain: "iki-anti-theft.firebaseapp.com",
  projectId: "iki-anti-theft",
  storageBucket: "iki-anti-theft.firebasestorage.app",
  messagingSenderId: "472144471117",
  appId: "1:472144471117:web:47e2f30ed040b58b55a804",
});

const messaging = firebase.messaging();

// Notification im Hintergrund empfangen und anzeigen
messaging.onBackgroundMessage(payload => {
  const { title, body } = payload.notification;
  const mapsUrl = payload.fcmOptions?.link;

  self.registration.showNotification(title, {
    body,
    icon: "/icon-192.png",
    badge: "/icon-192.png",
    requireInteraction: true,
    data: { mapsUrl },
    actions: mapsUrl ? [{ action: "open_maps", title: "Route öffnen" }] : [],
  });
});

// Klick auf Notification: Maps-Link öffnen
self.addEventListener("notificationclick", event => {
  event.notification.close();
  const url = event.notification.data?.mapsUrl;
  if (url) {
    event.waitUntil(clients.openWindow(url));
  } else {
    event.waitUntil(clients.openWindow("/"));
  }
});
