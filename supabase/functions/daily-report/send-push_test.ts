import { createReportPushSender } from "./send-push.ts";

function assert(value: unknown, message = "Assertion failed"): asserts value {
  if (!value) throw new Error(message);
}
const report = { deviceId: "phone", date: "2026-09-23", title: "Bericht", body: "Zusammenfassung", mapsUrl: "https://maps.example" };
const tokens = { fcm_token: "private-web-token", android_fcm_token: "private-android-token" };

for (const scenario of [
  { name: "beide Tokens", web: true, android: true, failWeb: false, failAndroid: false },
  { name: "nur Web, kein Android-Versuch", web: true, android: false, failWeb: false, failAndroid: false },
  { name: "nur Android", web: false, android: true, failWeb: false, failAndroid: false },
  { name: "keine Tokens", web: false, android: false, failWeb: false, failAndroid: false },
  { name: "Web-Fehler blockiert Android nicht", web: true, android: true, failWeb: true, failAndroid: false },
  { name: "Android-Fehler beeinflusst Web nicht", web: true, android: true, failWeb: false, failAndroid: true },
  { name: "beide FCM-Wege fehlerhaft", web: true, android: true, failWeb: true, failAndroid: true },
]) {
  Deno.test(scenario.name, async () => {
    const sent: string[] = [];
    const logs: string[] = [];
    let oauth = 0;
    const run = createReportPushSender({
      projectId: "iki", getAccessToken: async () => { oauth++; return "private-oauth-secret"; },
      log: message => logs.push(message),
      send: async (_url, init) => {
        const { message } = JSON.parse(String(init?.body));
        sent.push(message.token);
        const android = message.token === tokens.android_fcm_token;
        assert(android || message.token === tokens.fcm_token);
        if (android) {
          assert(!message.notification && !message.webpush);
          assert(JSON.stringify(message.data) === JSON.stringify({ command: "DAILY_REPORT", device_id: report.deviceId, date: report.date }));
        } else {
          assert(message.notification.title === report.title && message.notification.body === report.body);
          assert(message.webpush.fcm_options.link === report.mapsUrl);
        }
        return new Response("private-response-with-token-and-secret", {
          status: (android ? scenario.failAndroid : scenario.failWeb) ? 400 : 200,
        });
      },
    });
    const result = await run({
      fcm_token: scenario.web ? tokens.fcm_token : null,
      android_fcm_token: scenario.android ? tokens.android_fcm_token : null,
    }, report);
    assert(sent.length === Number(scenario.web) + Number(scenario.android));
    assert(result.web === (scenario.web && !scenario.failWeb));
    assert(result.android === (scenario.android && !scenario.failAndroid));
    assert(oauth === (sent.length ? 1 : 0));
    assert(!JSON.stringify({ logs, result }).includes("private-"));
  });
}

Deno.test("OAuth-Fehler wird bereinigt und für anderen Kanal erneut versucht", async () => {
  const logs: string[] = [];
  let oauth = 0;
  let sent = 0;
  const run = createReportPushSender({
    projectId: "iki", log: message => logs.push(message),
    getAccessToken: async () => { if (++oauth === 1) throw Error("PRIVATE service account / OAuth response"); return "secret"; },
    send: async () => { sent++; return new Response(null, { status: 200 }); },
  });
  const result = await run(tokens, report);
  assert(!result.web && result.android && oauth === 2 && sent === 1);
  assert(!JSON.stringify({ logs, result }).includes("PRIVATE"));
});

Deno.test("Netzwerkfehler wird bereinigt und verhindert den zweiten Kanal nicht", async () => {
  const logs: string[] = [];
  let sent = 0;
  const run = createReportPushSender({ projectId: "iki", log: message => logs.push(message),
    getAccessToken: async () => "secret", send: async () => {
      if (++sent === 1) throw Error("PRIVATE token in transport error");
      return new Response(null, { status: 200 });
    },
  });
  const result = await run(tokens, report);
  assert(!result.web && result.android && sent === 2);
  assert(!JSON.stringify({ logs, result }).includes("PRIVATE"));
});

Deno.test("Dauerhafter OAuth-Fehler beider Kanäle bleibt kontrolliert", async () => {
  const logs: string[] = [];
  const run = createReportPushSender({ projectId: "iki", log: message => logs.push(message),
    getAccessToken: async () => { throw Error("PRIVATE OAuth body"); },
    send: () => { throw Error("FCM must not be called"); },
  });
  const result = await run(tokens, report);
  assert(!result.web && !result.android && logs.length === 2);
  assert(!JSON.stringify({ logs, result }).includes("PRIVATE"));
});
