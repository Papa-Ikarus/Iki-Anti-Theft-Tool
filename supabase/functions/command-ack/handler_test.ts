import { handleCommandAck } from "./index.ts";

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

const commandId = "11111111-1111-4111-8111-111111111111";
const deviceId = "device-test";
const token = "ab".repeat(32);

Deno.test("Command-ACK: Autorisierung und Statusübergänge", async (t) => {
  const originalFetch = globalThis.fetch;
  const originalUrl = Deno.env.get("SUPABASE_URL");
  const originalKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

  Deno.env.set("SUPABASE_URL", "https://ack-test.invalid");
  Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", "local-test-key");

  const tokenHash = Array.from(
    new Uint8Array(
      await crypto.subtle.digest(
        "SHA-256",
        new TextEncoder().encode(token),
      ),
    ),
    (byte) => byte.toString(16).padStart(2, "0"),
  ).join("");

  const cases = [
    {
      name: "gültiger Empfang",
      current: "sent",
      next: "received",
      expectedHttp: 200,
      expectedStatus: "received",
      expectedWrites: 1,
      timestamp: "received_at",
    },
    {
      name: "Ausführung startet",
      current: "received",
      next: "running",
      expectedHttp: 200,
      expectedStatus: "running",
      expectedWrites: 1,
      timestamp: "started_at",
    },
    {
      name: "erfolgreicher Abschluss",
      current: "running",
      next: "success",
      expectedHttp: 200,
      expectedStatus: "success",
      expectedWrites: 1,
      timestamp: "finished_at",
    },
    {
      name: "endgültiger Fehler",
      current: "running",
      next: "error",
      expectedHttp: 200,
      expectedStatus: "error",
      expectedWrites: 1,
      timestamp: "finished_at",
    },
    {
      name: "endgültiger Timeout",
      current: "running",
      next: "timeout",
      expectedHttp: 200,
      expectedStatus: "timeout",
      expectedWrites: 1,
      timestamp: "finished_at",
    },
    {
      name: "Wiederholung bleibt ohne Änderung",
      current: "received",
      next: "received",
      expectedHttp: 200,
      expectedStatus: "received",
      expectedWrites: 0,
    },
    {
      name: "verspätete Meldung setzt Status nicht zurück",
      current: "running",
      next: "received",
      expectedHttp: 200,
      expectedStatus: "running",
      expectedWrites: 0,
    },
    {
      name: "Endstatus bleibt unverändert",
      current: "success",
      next: "error",
      expectedHttp: 409,
      expectedStatus: "success",
      expectedWrites: 0,
      expectedCode: "COMMAND_ALREADY_FINISHED",
    },
    {
      name: "falscher Token",
      current: "sent",
      next: "received",
      requestToken: "cd".repeat(32),
      expectedHttp: 401,
      expectedStatus: "sent",
      expectedWrites: 0,
      expectedCode: "ACK_UNAUTHORIZED",
    },
    {
      name: "falsches Gerät",
      current: "sent",
      next: "received",
      requestDevice: "device-other",
      expectedHttp: 401,
      expectedStatus: "sent",
      expectedWrites: 0,
      expectedCode: "ACK_UNAUTHORIZED",
    },
    {
      name: "falscher Befehl",
      current: "sent",
      next: "received",
      requestCommand: "22222222-2222-4222-8222-222222222222",
      expectedHttp: 401,
      expectedStatus: "sent",
      expectedWrites: 0,
      expectedCode: "ACK_UNAUTHORIZED",
    },
    {
      name: "abgelaufener Token",
      current: "sent",
      next: "received",
      expired: true,
      expectedHttp: 401,
      expectedStatus: "sent",
      expectedWrites: 0,
      expectedCode: "ACK_UNAUTHORIZED",
    },
    {
      name: "konkurrierende Änderung wird nicht überschrieben",
      current: "sent",
      next: "received",
      race: true,
      expectedHttp: 409,
      expectedStatus: "running",
      expectedWrites: 0,
      expectedCode: "ACK_RETRY",
    },
    {
      name: "ungültiger Status",
      current: "sent",
      next: "unknown",
      expectedHttp: 400,
      expectedStatus: "sent",
      expectedWrites: 0,
      expectedCode: "INVALID_ACK",
    },
  ];

  try {
    for (const testCase of cases) {
      await t.step(testCase.name, async () => {
        let storedStatus = testCase.current;
        let writes = 0;
        let written: Record<string, unknown> = {};

        const expiresAt = testCase.expired
          ? "2000-01-01T00:00:00.000Z"
          : "2100-01-01T00:00:00.000Z";

        globalThis.fetch = async (input, init) => {
          const request = new Request(input, init);
          const url = new URL(request.url);

          assert(
            url.origin === "https://ack-test.invalid" &&
              url.pathname === "/rest/v1/commands",
            "Unerwarteter Netzwerkaufruf",
          );

          const filters = url.searchParams;

          const authorized =
            filters.get("id") === `eq.${commandId}` &&
            filters.get("device_id") === `eq.${deviceId}` &&
            filters.get("ack_token_hash") === `eq.${tokenHash}`;

          const expiryFilter = filters.get("ack_token_expires_at");

          assert(
            expiryFilter?.startsWith("gt.") === true,
            "Ablaufprüfung fehlt",
          );

          const unexpired =
            Date.parse(expiresAt) >
              Date.parse(expiryFilter!.slice(3));

          if (request.method === "GET") {
            return Response.json(
              authorized && unexpired ? [{ status: storedStatus }] : [],
            );
          }

          assert(request.method === "PATCH", "Unerwartete HTTP-Methode");

          if (testCase.race) storedStatus = "running";

          if (
            !authorized ||
            !unexpired ||
            filters.get("status") !== `eq.${storedStatus}`
          ) {
            return Response.json([]);
          }

          written = await request.json();
          storedStatus = String(written.status);
          writes++;

          return Response.json([{ status: storedStatus }]);
        };

        const response = await handleCommandAck(
          new Request("https://local.invalid/command-ack", {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              "X-Command-Ack-Token": testCase.requestToken ?? token,
            },
            body: JSON.stringify({
              commandId: testCase.requestCommand ?? commandId,
              deviceId: testCase.requestDevice ?? deviceId,
              status: testCase.next,
            }),
          }),
        );

        const body = await response.json();

        assert(
          response.status === testCase.expectedHttp,
          `HTTP ${response.status} statt ${testCase.expectedHttp}`,
        );
        assert(
          storedStatus === testCase.expectedStatus,
          `Unerwarteter gespeicherter Status: ${storedStatus}`,
        );
        assert(
          writes === testCase.expectedWrites,
          `Unerwartete Anzahl Änderungen: ${writes}`,
        );

        if (testCase.expectedCode) {
          assert(body.code === testCase.expectedCode, "Falscher Fehlercode");
        }

        if (testCase.timestamp) {
          assert(
            typeof written[testCase.timestamp] === "string" &&
              Number.isFinite(Date.parse(String(written[testCase.timestamp]))),
            "Status-Zeitstempel fehlt oder ist ungültig",
          );
        }

        if (response.status === 200) {
          assert(body.success === true, "Erfolgsbestätigung fehlt");
          assert(
            body.applied === (writes === 1),
            "Unzutreffende Änderungsbestätigung",
          );
          assert(body.status === storedStatus, "Falscher Antwortstatus");
        }
      });
    }
  } finally {
    globalThis.fetch = originalFetch;

    if (originalUrl === undefined) {
      Deno.env.delete("SUPABASE_URL");
    } else {
      Deno.env.set("SUPABASE_URL", originalUrl);
    }

    if (originalKey === undefined) {
      Deno.env.delete("SUPABASE_SERVICE_ROLE_KEY");
    } else {
      Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", originalKey);
    }
  }
});