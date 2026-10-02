const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const assert = require("node:assert/strict");
const { parseHTML } = require("./owner-tests/node_modules/linkedom");

const html = fs.readFileSync(
  path.resolve(__dirname, "../dashboard/index.html"),
  "utf8"
);

const start = html.indexOf("let commandHistoryRequest = 0;");
const end = html.indexOf("// ── Steuerung", start);

assert(start >= 0 && end > start, "Befehlsverlauf-Code nicht gefunden");

const source = html.slice(start, end);
const tick = () => new Promise(resolve => setImmediate(resolve));

function setup() {
  const { document } = parseHTML(html);
  const requests = [];
  const timers = new Map();
  let timerId = 0;

  const context = vm.createContext({
    window: {},
    document,
    selectedDevice: "device-A",
    SUPABASE_URL: "https://local.invalid",
    SUPABASE_ANON_KEY: "local-public-key",
    AbortController,
    Date,
    console,
    supabase: {
      auth: {
        getSession: async () => ({
          data: { session: { access_token: "local-session" } },
          error: null
        })
      }
    },
    fetch: async (url, options) => {
      assert.equal(url, "https://local.invalid/functions/v1/command-history");
      assert.equal(options.method, "POST");
      assert.equal(options.headers.Authorization, "Bearer local-session");

      return new Promise(resolve => {
        requests.push({
          deviceId: JSON.parse(options.body).deviceId,
          resolve
        });
      });
    },
    setInterval(fn, milliseconds) {
      assert.equal(milliseconds, 10000);
      timers.set(++timerId, fn);
      return timerId;
    },
    clearInterval(id) {
      timers.delete(id);
    }
  });

  vm.runInContext(source, context);

  return {
    context,
    document,
    requests,
    timers,
    list: document.getElementById("command-history-list")
  };
}

function row(deviceId, id = "command-test") {
  return {
    id,
    device_id: deviceId,
    command: "location",
    status: "success",
    created_at: "2026-10-02T10:00:00Z",
    finished_at: "2026-10-02T10:00:01Z"
  };
}

function answer(request, commands, status = 200) {
  request.resolve(
    Response.json(
      status === 200 ? { success: true, commands } : { code: "TEST_ERROR" },
      { status }
    )
  );
}

async function main() {
  // Serverdaten dürfen nicht als HTML ausgeführt werden.
  {
    const env = setup();
    const pending = env.context.window.loadCommandHistory();
    await tick();

    assert.equal(env.requests[0].deviceId, "device-A");

    const id = '<img src=x onerror="alert(1)">';
    answer(env.requests[0], [row("device-A", id)]);
    await pending;

    assert(env.list.textContent.includes("Standort"));
    assert(env.list.textContent.includes("Erfolgreich"));
    assert(env.list.textContent.includes(id));
    assert.equal(env.list.querySelector("img"), null);
  }

  // Leere Ergebnisse erhalten einen verständlichen Hinweis.
  {
    const env = setup();
    const pending = env.context.window.loadCommandHistory();
    await tick();
    answer(env.requests[0], []);
    await pending;

    assert(env.list.textContent.includes("noch keine Befehle"));
  }

  // Daten eines anderen Geräts werden nicht angezeigt.
  {
    const env = setup();
    const pending = env.context.window.loadCommandHistory();
    await tick();
    answer(env.requests[0], [row("device-other", "foreign-command")]);
    await pending;

    assert(!env.list.textContent.includes("foreign-command"));
    assert(env.list.textContent.includes("nicht geladen"));
  }

  // Alte Antworten dürfen einen neu ausgewählten Verlauf nicht überschreiben.
  {
    const env = setup();
    const oldRequest = env.context.window.loadCommandHistory();
    await tick();

    env.context.selectedDevice = "device-B";
    const newRequest = env.context.window.loadCommandHistory();
    await tick();

    assert.equal(env.requests[1].deviceId, "device-B");

    answer(env.requests[1], [row("device-B", "new-command")]);
    await newRequest;

    answer(env.requests[0], [row("device-A", "old-command")]);
    await oldRequest;

    assert(env.list.textContent.includes("new-command"));
    assert(!env.list.textContent.includes("old-command"));
  }

  // Fehler werden verständlich angezeigt.
  for (const [status, expected] of [
    [401, "erneut anmelden"],
    [403, "Kein Zugriff"],
    [503, "nicht geladen"]
  ]) {
    const env = setup();
    const pending = env.context.window.loadCommandHistory();
    await tick();
    answer(env.requests[0], [], status);
    await pending;

    assert(env.list.textContent.includes(expected));
  }

  // Polling: nur ein Timer, keine überlappenden Anfragen, kein Ladeflackern.
  {
    const env = setup();

    vm.runInContext("startCommandHistoryRefresh()", env.context);
    vm.runInContext("startCommandHistoryRefresh()", env.context);
    assert.equal(env.timers.size, 1);

    const refresh = [...env.timers.values()][0];

    Object.defineProperty(env.document, "hidden", {
      value: true,
      configurable: true
    });

    refresh();
    await tick();
    assert.equal(env.requests.length, 0);

    Object.defineProperty(env.document, "hidden", {
      value: false,
      configurable: true
    });

    env.list.textContent = "Bestehender Verlauf";

    refresh();
    await tick();
    refresh();
    await tick();

    assert.equal(env.requests.length, 1);
    assert.equal(env.list.textContent, "Bestehender Verlauf");

    answer(env.requests[0], [row("device-A", "updated-command")]);
    await tick();

    assert(env.list.textContent.includes("updated-command"));
  }

  console.log(
    "Befehlsverlauf: sichere Ausgabe, Gerätewechsel, Fehler und Polling erfolgreich."
  );
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});