const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const root = require('node:path').resolve(__dirname, '..');
let callback;
let shown = 0;
vm.runInNewContext(fs.readFileSync(root + '/dashboard/firebase-messaging-sw.js', 'utf8'), {
  importScripts() {},
  firebase: { initializeApp() {}, messaging: () => ({ onBackgroundMessage(fn) { callback = fn; } }) },
  self: { registration: { showNotification() { shown++; } }, addEventListener() {} },
});
callback({ notification: { title: 'Report', body: 'Body' } });
assert.equal(shown, 0, 'Firebase notification must not be shown twice');
callback({ data: { title: 'Data report', body: 'Body' } });
assert.equal(shown, 1);
callback({ data: {} });
assert.equal(shown, 1);
console.log('Service-Worker: automatische und manuelle Anzeige korrekt getrennt.');
