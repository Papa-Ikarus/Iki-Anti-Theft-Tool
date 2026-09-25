const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const html = fs.readFileSync(require('node:path').join(__dirname, '../dashboard/index.html'), 'utf8');
// Echten Dashboard-Startcode ausführen; nur Netzwerk und spätere Datenfunktionen ersetzen.
const source = html.match(/<script type="module">([\s\S]*?)<\/script>/)[1]
  .split('window.refreshDashboard =')[0]
  .replace(/^\s*import .* from .*;\s*$/gm, '')
  .replace(/import\("https:\/\/www.gstatic.com\/firebasejs\/10.12.2\/firebase-messaging.js"\)/g, 'loadMessaging()');

async function run({ owner = false, bridge = false, notification = true, supported = true, fail = false, nativeFail = false } = {}) {
  const calls = { imports: 0, messaging: 0, worker: 0, token: 0, stored: 0, foreground: 0, dashboard: 0 };
  const elements = new Map();
  const registration = { scope: '/', pushManager: { getSubscription: async () => null } };
  const context = {
    URLSearchParams, location: { search: owner ? '?owner_app=1' : '' },
    console: { log() {}, error() {} },
    document: { getElementById(id) { if (!elements.has(id)) elements.set(id, { style: {}, textContent: '' }); return elements.get(id); } },
    navigator: { serviceWorker: { register: async () => { calls.worker++; if (fail) throw Error('worker failed'); return registration; }, ready: Promise.resolve(), getRegistration: async () => registration } },
    createClient: () => ({ from: () => ({ upsert(data) { assert.equal(data.fcm_token, 'browser-token'); calls.stored++; return { select: async () => ({}) }; } }) }),
    initializeApp: () => ({}), setupOwnerPush() { if (nativeFail) throw Error('bridge failed'); },
    async loadMessaging() { calls.imports++; return { isSupported: async () => supported, getMessaging() { calls.messaging++; return {}; }, getToken: async () => { calls.token++; return 'browser-token'; }, onMessage() { calls.foreground++; } }; },
    initMap() { calls.dashboard++; }, async loadDevices() { calls.dashboard++; }, startLiveStatusRefresh() { calls.dashboard++; }, subscribeLocationUpdates() { calls.dashboard++; },
  };
  context.window = context;
  if (bridge) context.IkiOwner = {};
  if (notification) context.Notification = { permission: 'granted' };
  if (owner || bridge) {
    Object.defineProperty(context, 'Notification', { get() { throw Error('Owner accessed Notification'); } });
    Object.defineProperty(context.navigator, 'serviceWorker', { get() { throw Error('Owner accessed serviceWorker'); } });
  }
  vm.createContext(context);
  vm.runInContext(source, context);
  await vm.runInContext('init()', context);
  await context.registerWebPush();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls.dashboard, 4, 'Push darf den Dashboard-Start nicht abbrechen');
  if (owner || bridge || !notification) assert.equal(calls.imports, 0);
  if (owner || bridge || !notification || !supported || fail) assert.equal(calls.stored, 0);
  else {
    assert.equal(calls.imports, 1);
    assert.equal(calls.worker, 1);
    assert.equal(calls.foreground, 1);
    assert.ok(calls.token > 0);
    assert.equal(calls.stored, calls.token);
  }
}
(async () => {
  for (const scenario of [
    { owner: true, bridge: true }, { owner: true }, { bridge: true },
    { owner: true, nativeFail: true }, { notification: false },
    { supported: false }, { fail: true }, {},
  ]) await run(scenario);
  console.log('8 Szenarien bestanden: Owner-Isolation, fehlende APIs, Push-Fehler und Browser-FCM.');
})().catch(error => { console.error(error); process.exitCode = 1; });
