const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const source = fs.readFileSync(
  path.join(__dirname, "../supabase/functions/send-command/index.ts"),
  "utf8"
);

const start = source.indexOf("if (isUnregistered) {");
const end = source.indexOf(
  "// Andere FCM-Fehler normal zurückgeben",
  start
);

assert.ok(start >= 0 && end > start, "Bereinigungsblock nicht gefunden.");

const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;

const execute = new AsyncFunction(
  "supabase",
  "isUnregistered",
  "commandId",
  "deviceId",
  "fcmToken",
  "corsHeaders",
  "console",
  source.slice(start, end) + "\nreturn null;"
);

const OLD_TOKEN = "private-old-token-for-test";
const NEW_TOKEN = "private-new-token-for-test";
const ERROR_DETAIL = "private-database-error-detail";

function environment(rows, databaseError = null) {
  const devices = structuredClone(rows);
  const logs = [];
  let queryCount = 0;

  const supabase = {
    from(table) {
      assert.equal(table, "devices");
      queryCount++;

      let update = null;
      const filters = [];
      const builder = {
        update(values) {
          update = values;
          return builder;
        },
        eq(column, value) {
          filters.push([column, value]);
          return builder;
        },
        async select(columns) {
          assert.equal(columns, "id");
          assert.ok(update, "Update fehlt.");

          if (databaseError) {
            return { data: null, error: databaseError };
          }

          const matched = devices.filter(row =>
            filters.every(([column, value]) => row[column] === value)
          );

          for (const row of matched) {
            Object.assign(row, update);
          }

          return {
            data: matched.map(row => ({ id: row.id })),
            error: null
          };
        }
      };

      return builder;
    }
  };

  const logger = Object.fromEntries(
    ["log", "warn", "error"].map(level => [
      level,
      (...args) => logs.push(JSON.stringify(args))
    ])
  );

  return {
    devices,
    logs,
    get queryCount() {
      return queryCount;
    },
    async run(unregistered = true) {
      const response = await execute(
        supabase,
        unregistered,
        "test-command-id",
        "device-a",
        OLD_TOKEN,
        { "Content-Type": "application/json" },
        logger
      );

      const output = logs.join("\n");

      for (const sensitiveValue of [
        OLD_TOKEN,
        NEW_TOKEN,
        ERROR_DETAIL
      ]) {
        assert.ok(
          !output.includes(sensitiveValue),
          "Sensible Testdaten wurden protokolliert."
        );
      }

      return response;
    }
  };
}

async function run() {
  // Nur der betroffene Token wird geleert; das Gerät bleibt erhalten.
  {
    const original = [
      {
        id: "device-a",
        fcm_token: OLD_TOKEN,
        last_seen: 12345,
        last_boot: 6789,
        created_at: "2026-10-01"
      },
      {
        id: "device-b",
        fcm_token: OLD_TOKEN,
        last_seen: 999
      }
    ];

    const test = environment(original);
    const response = await test.run();

    assert.equal(response.status, 410);
    assert.equal((await response.json()).code, "FCM_TOKEN_INVALID");

    const expected = structuredClone(original);
    expected[0].fcm_token = "";

    assert.deepEqual(test.devices, expected);
    assert.equal(test.devices.length, original.length);
  }

  // Ein inzwischen erneuerter Token darf nicht entfernt werden.
  {
    const original = [
      { id: "device-a", fcm_token: NEW_TOKEN },
      { id: "device-b", fcm_token: OLD_TOKEN }
    ];

    const test = environment(original);
    const response = await test.run();

    assert.equal(response.status, 410);
    assert.deepEqual(test.devices, original);
  }

  // Ein bereits geleerter Token bleibt unverändert.
  {
    const original = [{ id: "device-a", fcm_token: "" }];
    const test = environment(original);

    assert.equal((await test.run()).status, 410);
    assert.deepEqual(test.devices, original);
  }

  // Bei fehlendem Zielgerät bleiben andere Geräte unangetastet.
  {
    const original = [{ id: "device-b", fcm_token: OLD_TOKEN }];
    const test = environment(original);

    assert.equal((await test.run()).status, 410);
    assert.deepEqual(test.devices, original);
  }

  // Ohne UNREGISTERED-Freigabe wird keine Bereinigung ausgeführt.
  {
    const original = [{ id: "device-a", fcm_token: OLD_TOKEN }];
    const test = environment(original);

    assert.equal(await test.run(false), null);
    assert.equal(test.queryCount, 0);
    assert.deepEqual(test.devices, original);
  }

  // Datenbankfehler werden gemeldet und verändern keinen Token.
  {
    const original = [{ id: "device-a", fcm_token: OLD_TOKEN }];
    const test = environment(original, {
      code: "XX000",
      message: ERROR_DETAIL
    });

    const response = await test.run();

    assert.equal(response.status, 503);
    assert.equal(
      (await response.json()).code,
      "FCM_TOKEN_CLEANUP_FAILED"
    );
    assert.deepEqual(test.devices, original);
  }

  console.log(
    "FCM-Token-Bereinigung: 6 Fälle erfolgreich; " +
    "Token-Erneuerung, Geräteerhalt, Fehler und bereinigte Logs geprüft."
  );
}

run().catch(error => {
  console.error(error);
  process.exitCode = 1;
});