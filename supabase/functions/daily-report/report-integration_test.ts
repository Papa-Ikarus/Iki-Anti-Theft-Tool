// Echten Berichtslauf mit lokalen DB-/Netzwerk-Doubles ausführen, ohne Produktionszugriff.
import { createReportPushSender } from "./send-push.ts";

function assert(value: unknown, message = "Assertion failed"): asserts value {
  if (!value) throw new Error(message);
}

for (const hasLocations of [true, false]) {
  Deno.test(`Berichtslauf trotz zweier Push-Fehler; Standortdaten=${hasLocations}`, async () => {
    const stored: Record<string, unknown>[] = [];
    const logs: string[] = [];
    let attempts = 0;
    const fake = {
      env: { get: (key: string) => key === "DAILY_REPORT_SECRET" ? "test-cron" : "test-value" },
      console: { log: (...args: unknown[]) => logs.push(String(args)), warn: (...args: unknown[]) => logs.push(String(args)), error: (...args: unknown[]) => logs.push(String(args)) },
      createClient: () => ({ from(table: string) {
        let columns = "";
        const filters: Record<string, unknown> = {};
        const builder = {
          select(value: string) { columns = value; return builder; },
          eq(key: string, value: unknown) { filters[key] = value; return builder; },
          gte() { return builder; }, lt() { return builder; }, order() { return builder; }, limit() { return builder; },
          single() { return builder; },
          upsert(value: Record<string, unknown>) { assert(table === "reports"); stored.push(value); return builder; },
          then(resolve: (value: unknown) => unknown) {
            if (table === "owner") {
              assert(columns === "fcm_token, android_fcm_token");
              assert(filters.id === "dashboard");
            }
            const data = table === "devices" ? [{ id: "phone" }]
              : table === "owner" ? { fcm_token: "PRIVATE-web", android_fcm_token: "PRIVATE-android" }
              : table === "locations" && hasLocations ? [{ lat: 52, lng: 13, timestamp: Date.now() - 86400000 }]
              : [];
            return Promise.resolve({ data, error: null }).then(resolve);
          },
        };
        return builder;
      } }),
      createReportPushSender: () => createReportPushSender({
        projectId: "test", getAccessToken: async () => "PRIVATE-oauth",
        log: message => logs.push(message), send: async () => {
          attempts++;
          assert(stored.length === Number(hasLocations), "Report muss vor dem Push gespeichert sein");
          return new Response("PRIVATE-token-and-oauth-error", { status: 400 });
        },
      }),
    };
    const key = `__ikiReportTest${Number(hasLocations)}`;
    const globals = globalThis as unknown as Record<string, unknown>;
    globals[key] = fake;
    try {
      let source = await Deno.readTextFile(new URL("./index.ts", import.meta.url));
      source = source.replace(/^import .*;\r?\n/gm, "")
        .replace("Deno.serve(async (req) => {", "export const run = async (req: Request) => {")
        .replace(/^\}\);\s*\/\/ ─/m, "};\n// ─")
        .replaceAll("Deno.env", "fake.env").replaceAll("console.", "fake.console.");
      source = `const fake = (globalThis as any)[${JSON.stringify(key)}];\nconst { createClient, createReportPushSender } = fake;\n` + source;
      const module = await import("data:application/typescript;base64," + btoa(unescape(encodeURIComponent(source))));
      const response = await module.run(new Request("https://local.test", { headers: { Authorization: "Bearer test-cron" } }));
      const result = await response.json();
      assert(response.status === 200 && result.success);
      assert(result.reports === Number(hasLocations));
      assert(result.results[0].report_created === hasLocations);
      assert(!result.results[0].push_sent && !result.results[0].android_push_sent);
      assert(attempts === 2);
      assert(!JSON.stringify({ logs, result }).includes("PRIVATE"));
    } finally { delete globals[key]; }
  });
}
