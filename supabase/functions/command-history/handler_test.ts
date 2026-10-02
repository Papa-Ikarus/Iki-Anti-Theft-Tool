import { createCommandHistoryHandler } from "./index.ts";

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

const config = {
  url: "https://history-test.invalid",
  serviceKey: "local-service-key",
  ownerUserId: "owner-test",
  origin: "https://control-test.invalid",
};

const deviceId = "device-test";

Deno.test("Befehlsverlauf: Besitzerzugriff und sichere Ausgabe", async (t) => {
  const cases = [
    {
      name: "Besitzer erhält gefilterten Verlauf",
      expected: 200,
      authCalls: 1,
      dbCalls: 1,
    },
    {
      name: "leerer Verlauf",
      empty: true,
      expected: 200,
      authCalls: 1,
      dbCalls: 1,
    },
    {
      name: "fehlende Sitzung",
      noToken: true,
      expected: 401,
      authCalls: 0,
      dbCalls: 0,
    },
    {
      name: "ungültige Sitzung",
      authStatus: 401,
      expected: 401,
      authCalls: 1,
      dbCalls: 0,
    },
    {
      name: "anderer Benutzer",
      otherUser: true,
      expected: 403,
      authCalls: 1,
      dbCalls: 0,
    },
    {
      name: "anonymer Benutzer",
      anonymous: true,
      expected: 403,
      authCalls: 1,
      dbCalls: 0,
    },
    {
      name: "fremder Ursprung",
      wrongOrigin: true,
      expected: 403,
      authCalls: 0,
      dbCalls: 0,
    },
    {
      name: "fehlende Konfiguration",
      missingConfig: true,
      expected: 503,
      authCalls: 0,
      dbCalls: 0,
    },
    {
      name: "ungültiges JSON",
      invalidJson: true,
      expected: 400,
      authCalls: 1,
      dbCalls: 0,
    },
    {
      name: "leere Geräte-ID",
      invalidDevice: true,
      expected: 400,
      authCalls: 1,
      dbCalls: 0,
    },
    {
      name: "Datenbankfehler",
      dbError: true,
      expected: 503,
      authCalls: 1,
      dbCalls: 1,
    },
    {
      name: "fremdes Gerät in Datenbankantwort",
      wrongDevice: true,
      expected: 502,
      authCalls: 1,
      dbCalls: 1,
    },
    {
      name: "mehr als 50 Ergebnisse",
      tooMany: true,
      expected: 502,
      authCalls: 1,
      dbCalls: 1,
    },
  ];

  for (const testCase of cases) {
    await t.step(testCase.name, async () => {
      let authCalls = 0;
      let dbCalls = 0;

      const send: typeof fetch = async (input, init) => {
        const request = new Request(input, init);
        const url = new URL(request.url);

        assert(url.origin === config.url, "Unerwartetes Netzwerkziel");

        if (url.pathname === "/auth/v1/user") {
          authCalls++;

          assert(
            request.headers.get("Authorization") === "Bearer local-session",
            "Sitzung wird nicht zur Prüfung weitergegeben",
          );

          if (testCase.authStatus) {
            return Response.json(
              { error: "Invalid session" },
              { status: testCase.authStatus },
            );
          }

          return Response.json({
            id: testCase.otherUser ? "other-user" : config.ownerUserId,
            is_anonymous: testCase.anonymous ?? false,
          });
        }

        assert(
          url.pathname === "/rest/v1/commands",
          "Unerwarteter Datenbankaufruf",
        );

        dbCalls++;

        assert(request.method === "GET", "Verlauf darf nur lesen");
        assert(
          request.headers.get("Authorization") ===
            `Bearer ${config.serviceKey}`,
          "Serverseitige Datenbankautorisierung fehlt",
        );
        assert(
          url.searchParams.get("device_id") === `eq.${deviceId}`,
          "Gerätefilter fehlt",
        );
        assert(url.searchParams.get("limit") === "50", "Limit fehlt");
        assert(
          url.searchParams.get("order") === "created_at.desc,id.desc",
          "Sortierung fehlt",
        );

        const selected = url.searchParams.get("select")?.split(",") ?? [];

        assert(!selected.includes("*"), "Keine vollständigen Zeilen abfragen");
        assert(
          !selected.includes("ack_token_hash") &&
            !selected.includes("ack_token_expires_at") &&
            !selected.includes("error_message"),
          "Sensible Felder werden abgefragt",
        );

        if (testCase.dbError) {
          return Response.json({ error: "Database failure" }, { status: 500 });
        }

        const row = {
          id: "11111111-1111-4111-8111-111111111111",
          device_id: testCase.wrongDevice ? "device-other" : deviceId,
          command: "location",
          status: "success",
          created_at: "2026-10-02T10:00:00Z",
          // Zusätzliche Felder müssen selbst dann aus der Ausgabe verschwinden.
          ack_token_hash: "secret-marker",
          ack_token: "secret-marker",
          error_message: "private-error-marker",
        };

        const rows = testCase.empty
          ? []
          : testCase.tooMany
          ? Array.from({ length: 51 }, () => ({ ...row }))
          : [row];

        return Response.json(rows);
      };

      const handler = createCommandHistoryHandler(
        testCase.missingConfig ? { ...config, serviceKey: "" } : config,
        send,
      );

      const headers: Record<string, string> = {
        "Content-Type": "application/json",
        Origin: testCase.wrongOrigin ? "https://other.invalid" : config.origin,
      };

      if (!testCase.noToken) {
        headers.Authorization = "Bearer local-session";
      }

      const response = await handler(
        new Request("https://local.invalid/command-history", {
          method: "POST",
          headers,
          body: testCase.invalidJson ? "{" : JSON.stringify({
            deviceId: testCase.invalidDevice ? "" : deviceId,
          }),
        }),
      );

      const text = await response.text();
      const body = JSON.parse(text);

      assert(
        response.status === testCase.expected,
        `HTTP ${response.status} statt ${testCase.expected}`,
      );
      assert(authCalls === testCase.authCalls, "Falsche Anzahl Auth-Aufrufe");
      assert(dbCalls === testCase.dbCalls, "Falsche Anzahl Datenbankaufrufe");
      assert(
        response.headers.get("Cache-Control") === "no-store",
        "Cache-Schutz fehlt",
      );
      assert(
        !text.includes("secret-marker") &&
          !text.includes("private-error-marker"),
        "Sensible Felder gelangen in die Antwort",
      );

      if (response.status === 200) {
        assert(body.success === true, "Erfolgsbestätigung fehlt");
        assert(Array.isArray(body.commands), "Verlauf fehlt");
        assert(
          body.commands.length === (testCase.empty ? 0 : 1),
          "Falsche Anzahl Befehle",
        );

        if (!testCase.empty) {
          assert(body.commands[0].device_id === deviceId, "Falsches Gerät");
          assert(body.commands[0].status === "success", "Status fehlt");
          assert(
            Object.keys(body.commands[0]).length === 10,
            "Unerwartete Ausgabefelder",
          );
        }
      }
    });
  }
});
