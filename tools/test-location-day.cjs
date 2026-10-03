const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const html = fs.readFileSync(
  path.join(__dirname, "../dashboard/index.html"),
  "utf8"
);

const match = html.match(
  /(let locationHistoryRequest = 0;[\s\S]*?)\n\s*function getMarkerIcon/
);

assert.ok(match, "Block für den Standortverlauf nicht gefunden.");

function environment(responses) {
  const queries = [];
  const layers = new Set();

  const input = {
    value: "2026-10-03",
    addEventListener() {}
  };
  const summary = { textContent: "" };
  const button = { addEventListener() {} };

  function layer(kind, position) {
    return {
      kind,
      position,
      bindPopup(popup) {
        this.popup = popup;
        return this;
      },
      addTo() {
        layers.add(this);
        return this;
      }
    };
  }

  const context = vm.createContext({
    Date,
    AbortController,
    setTimeout,
    clearTimeout,
    selectedDevice: "device-a",
    allDevices: [{ id: "device-a" }, { id: "device-b" }],
    DEVICE_COLORS: ["#2563eb", "#dc2626"],
    routeLayers: {},
    historyMarkers: {},
    getLocalDate: () => "2026-10-03",
    document: {
      getElementById(id) {
        return {
          "location-day": input,
          "location-history-summary": summary,
          "location-today-btn": button
        }[id];
      },
      createElement() {
        return { style: {}, textContent: "" };
      }
    },
    map: {
      zoomCalls: 0,
      removeLayer(item) {
        layers.delete(item);
      },
      fitBounds() {
        this.zoomCalls++;
      }
    },
    L: {
      circleMarker(position) {
        return layer("marker", position);
      },
      polyline(position) {
        return layer("route", position);
      },
      featureGroup() {
        return { getBounds: () => ({}) };
      }
    },
    supabase: {
      from(table) {
        assert.equal(table, "locations");

        const query = {};
        queries.push(query);
        const builder = {};

        for (const method of [
          "select", "eq", "gte", "lt", "order", "range"
        ]) {
          builder[method] = (...args) => {
            (query[method] ??= []).push(args);
            return builder;
          };
        }

        builder.abortSignal = signal => {
          query.signal = signal;

          assert.ok(
            responses.length,
            "Unerwartete zusätzliche Standortabfrage."
          );

          const response = responses.shift();
          return Promise.resolve(
            typeof response === "function" ? response(query) : response
          );
        };

        return builder;
      }
    }
  });

  vm.runInContext(
    match[1] + "\nglobalThis.load = loadLocationHistory;",
    context
  );

  return { context, input, summary, layers, queries };
}

function result(data, count = data.length) {
  return { data, count, error: null };
}

function point(index = 0) {
  return {
    lat: 51 + index / 100000,
    lng: 7,
    timestamp: Date.parse("2026-10-03T10:00:00Z") + index * 1000
  };
}

async function run() {
  const previousTimezone = process.env.TZ;
  process.env.TZ = "Europe/Berlin";

  try {
    // Kalendergrenzen, einschließlich der beiden Zeitumstellungen.
    for (const [day, start, end] of [
      [
        "2026-10-03",
        "2026-10-02T22:00:00Z",
        "2026-10-03T22:00:00Z"
      ],
      [
        "2026-03-29",
        "2026-03-28T23:00:00Z",
        "2026-03-29T22:00:00Z"
      ],
      [
        "2026-10-25",
        "2026-10-24T22:00:00Z",
        "2026-10-25T23:00:00Z"
      ]
    ]) {
      const test = environment([result([])]);
      test.input.value = day;
      await test.context.load("device-a");

      const query = test.queries[0];
      assert.equal(query.eq[0][1], "device-a");
      assert.equal(query.gte[0][1], Date.parse(start));
      assert.equal(query.lt[0][1], Date.parse(end));
    }

    // Ein einzelner Punkt hat eine gemeinsame Start-/Endmarkierung.
    {
      const test = environment([result([point()])]);
      await test.context.load("device-a");

      assert.match(test.summary.textContent, /1 Standortpunkt/);
      assert.ok(
        [...test.layers].some(item =>
          item.popup?.textContent.startsWith("Start- und Endpunkt")
        )
      );
      assert.equal(
        [...test.layers].filter(item => item.kind === "route").length,
        0
      );
    }

    // Ein leerer neuer Tag entfernt den bisherigen Tagesverlauf.
    {
      const test = environment([
        result([point(), point(1)]),
        result([])
      ]);

      await test.context.load("device-a");
      assert.ok(test.layers.size > 0);

      test.input.value = "2026-10-04";
      await test.context.load("device-a");

      assert.equal(test.layers.size, 0);
      assert.match(test.summary.textContent, /Keine Standortpunkte/);
    }

    // Mehr als 1000 Punkte werden über mehrere Seiten geladen.
    {
      const points = Array.from({ length: 1001 }, (_, i) => point(i));
      const test = environment([
        result(points.slice(0, 500), 1001),
        result(points.slice(500, 1000), 1001),
        result(points.slice(1000), 1001)
      ]);

      await test.context.load("device-a");

      assert.equal(test.queries.length, 3);
      assert.equal(test.queries[0].range[0][0], 0);
      assert.equal(test.queries[1].range[0][0], 500);
      assert.equal(test.queries[2].range[0][0], 1000);
      assert.match(test.summary.textContent, /1001 Standortpunkte/);
    }

    // Verspätete Antworten dürfen weder Tag noch Gerät überschreiben.
    for (const switchDevice of [false, true]) {
      let resolveOld;
      const oldResponse = new Promise(resolve => {
        resolveOld = resolve;
      });

      const test = environment([
        oldResponse,
        result([])
      ]);

      const oldLoad = test.context.load("device-a");

      if (switchDevice) {
        test.context.selectedDevice = "device-b";
      } else {
        test.input.value = "2026-10-04";
      }

      await test.context.load(test.context.selectedDevice);
      const currentMessage = test.summary.textContent;

      // Der Mock ignoriert den Abbruch absichtlich.
      resolveOld(result([point()]));
      await oldLoad;

      assert.equal(test.summary.textContent, currentMessage);
      assert.equal(test.layers.size, 0);
      assert.equal(test.queries[0].signal.aborted, true);
    }

    // Aktualisierung desselben Tages verändert den Kartenausschnitt nicht.
    {
      const test = environment([
        result([point(), point(1)]),
        result([point(), point(1), point(2)])
      ]);

      await test.context.load("device-a");
      await test.context.load("device-a");

      assert.equal(test.context.map.zoomCalls, 1);
      assert.match(test.summary.textContent, /3 Standortpunkte/);
    }

    // Ein Datenbankfehler wird angezeigt, ohne den letzten Stand zu löschen.
    {
      const test = environment([
        result([point()]),
        { data: null, count: null, error: { message: "Testfehler" } }
      ]);

      await test.context.load("device-a");
      const previousLayers = test.layers.size;
      await test.context.load("device-a");

      assert.equal(test.layers.size, previousLayers);
      assert.match(test.summary.textContent, /nicht aktualisiert/);
    }

    console.log(
      "Standortverlauf: Tagesgrenzen, Zeitumstellung, leere Tage, " +
      "Pagination, Wechsel, Kartenansicht und Fehler erfolgreich."
    );
  } finally {
    if (previousTimezone === undefined) {
      delete process.env.TZ;
    } else {
      process.env.TZ = previousTimezone;
    }
  }
}

run().catch(error => {
  console.error(error);
  process.exitCode = 1;
});