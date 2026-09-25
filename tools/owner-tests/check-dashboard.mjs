import { parseHTML } from 'linkedom';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
const { window, document } = parseHTML('<html><body><div id="status">Gerät</div></body></html>');
globalThis.window = window;
globalThis.document = document;
globalThis.location = { search: '?owner_app=1' };
const tick = () => new Promise(resolve => setTimeout(resolve, 0));
let sent = 0;
let session = null;
let authChange;
let serverError = null;
window.IkiOwner = { postMessage(message) { assert.equal(message, 'getToken'); sent++; } };
const source = readFileSync(new URL('../../dashboard/owner-push.js', import.meta.url), 'utf8');
const { setupOwnerPush } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
const invocations = [];
const supabase = {
  auth: { getSession: async () => ({ data: { session } }), onAuthStateChange(callback) { authChange = callback; } },
  functions: { invoke: async (name, options) => { invocations.push({ name, options }); return { error: serverError }; } },
};
assert.equal(setupOwnerPush(supabase), true);
const button = document.querySelector('button');
const status = document.querySelector('span');
assert.equal(button.hidden, false, 'Ausgangszustand sichtbar');
assert.equal(button.disabled, true, 'Automatischer Start läuft');
await tick();
assert.equal(button.hidden, false);
assert.equal(button.disabled, false, 'Ohne Sitzung bleibt Aktion verfügbar');
assert.equal(sent, 0);
session = { access_token: 'test' };
authChange();
await tick();
assert.equal(sent, 1);
assert.equal(button.disabled, true, 'Laufende Registrierung sperrt Button');
await button.onclick();
assert.equal(sent, 1, 'Keine parallele Registrierung');
const reply = data => window.IkiOwner.onmessage({ data: JSON.stringify(data) });
const assertSuccess = () => {
  assert.equal(status.textContent, 'Dieses Handy ist für Iki-Tagesberichte registriert.');
  assert.equal(button.hidden, true, 'Nach Erfolg vollständig ausgeblendet');
};
await reply({ token: 'native-test-token' });
assert.equal(invocations[0].name, 'register-owner-push');
assert.equal(invocations[0].options.body.token, 'native-test-token');
assertSuccess();

// App-Rückkehr erneuert die Registrierung trotz ausgeblendetem Button.
window.dispatchEvent(new window.Event('iki-owner-resume'));
await tick();
assert.equal(sent, 2);
assert.equal(button.disabled, true);
assert.equal(button.hidden, true);
await reply({ error: 'Permission denied' });
assert.match(status.textContent, /Permission denied/);
assert.equal(button.hidden, false, 'Fehler macht Wiederholung sichtbar');
assert.equal(button.disabled, false);
await button.onclick();
assert.equal(sent, 3);
assert.equal(button.disabled, true);
await reply({ token: 'rotated-token' });
assertSuccess();
assert.equal(invocations.at(-1).options.body.token, 'rotated-token');

// Netzwerkwiederkehr und Serverfehler, danach erneute erfolgreiche Registrierung.
window.dispatchEvent(new window.Event('online'));
await tick();
assert.equal(sent, 4);
serverError = new Error('server unavailable');
await reply({ token: 'rotated-token' });
assert.equal(button.hidden, false);
assert.equal(button.disabled, false);
serverError = null;
await button.onclick();
await reply({ token: 'rotated-token' });
assertSuccess();
authChange();
await tick();
assert.equal(sent, 6, 'Auth-Änderung registriert trotz ausgeblendetem Button');
await reply({ token: 'new-token' });
assertSuccess();

// Logout stellt die sichtbare Aktion wieder her.
session = null;
authChange();
await tick();
assert.equal(button.hidden, false);
assert.equal(button.disabled, false);
assert.equal(sent, 6);
delete window.IkiOwner;
assert.equal(setupOwnerPush(supabase), true, 'Owner-Modus bleibt ohne native Bridge aktiv');
assert.match(document.body.textContent, /Native Push-Verbindung nicht verfügbar/);
location.search = '';
assert.equal(setupOwnerPush(supabase), false);
console.log('Dashboard: Start, laufend, Erfolg, Fehler, Wiederholung, Tokenwechsel, Resume, Online, Auth und Browser-Abgrenzung bestanden.');
