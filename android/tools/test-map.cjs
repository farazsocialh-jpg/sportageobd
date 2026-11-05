// Tests the actual Android asset script without a CDN, DOM package, or tile network.
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { runInNewContext } = require('node:vm');
const assert = require('node:assert/strict');
const html = readFileSync(resolve(__dirname, '../app/src/main/assets/live_map.html'), 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)];
const script = scripts.at(-1)[1];
let passed = 0;
function test(name, fn) { fn(); console.log(`PASS ${name}`); passed++; }
function setup(search = '?lat=25.1712&lon=51.5689', engine = true) {
  const elements = new Map();
  const events = {};
  const timers = new Map();
  const layers = [];
  let timerId = 0;
  const get = id => {
    if (!elements.has(id)) elements.set(id, { style: {}, textContent: '', classList: { toggle() {} } });
    return elements.get(id);
  };
  const map = {
    attributionControl: { setPrefix() {}, setPosition() {} },
    center: null, panCount: 0, resizeCount: 0,
    setView(coords) { this.center = coords; return this; },
    panTo(coords) { this.center = coords; this.panCount++; },
    removeLayer() {}, invalidateSize() { this.resizeCount++; }
  };
  const marker = { coords: null, addTo() { return this; }, setLatLng(coords) { this.coords = coords; } };
  const window = { location: { search }, addEventListener(name, callback) { events[name] = callback; } };
  const context = {
    window, document: { getElementById: get }, URLSearchParams, console,
    setTimeout(fn, ms) { const id = ++timerId; timers.set(id, { fn, ms }); return id; },
    clearTimeout(id) { timers.delete(id); },
    L: engine ? {
      map() { return map; }, marker(coords) { marker.coords = coords; return marker; }, divIcon(x) { return x; },
      tileLayer(url, opts) {
        const layer = { url, opts, handlers: {}, on(name, fn) { this.handlers[name] = fn; return this; }, addTo() { return this; } };
        layers.push(layer); return layer;
      }
    } : undefined
  };
  runInNewContext(script, context);
  return { window, events, timers, map, marker, layers, get,
    fireTimer(ms) {
      const entry = [...timers].find(([, t]) => t.ms === ms);
      assert.ok(entry, `Expected ${ms}ms timer`);
      timers.delete(entry[0]); entry[1].fn();
    }
  };
}

test('map fills older Android WebViews without CSS inset support', () => {
  assert.match(html, /#map\s*\{[^}]*top:\s*0;[^}]*width:\s*100%;[^}]*height:\s*100%/);
  assert.match(html, /#offline\s*\{[^}]*display:\s*block/);
});
test('initial GPS fix is used before the first tile request', () => {
  const t = setup();
  assert.equal(t.window.mapReady, true);
  assert.equal(t.map.center.join(','), '25.1712,51.5689');
  assert.equal(t.get('offlineCoords').textContent, '25.17120, 51.56890');
});
test('invalid initial coordinates fall back safely', () => {
  assert.equal(setup('?lat=NaN&lon=999').map.center.join(','), '25.2854,51.531');
});
test('GPS moves marker without rebuilding map or layers', () => {
  const t = setup(); t.window.updateVehicleLocation(25.2, 51.6);
  assert.equal(t.map.center.join(','), '25.2,51.6');
  assert.equal(t.marker.coords.join(','), '25.2,51.6');
  assert.equal(t.layers.length, 1);
});
test('telemetry recompositions do not pan an unchanged location', () => {
  const t = setup(); t.window.updateVehicleLocation(25.1712, 51.5689);
  assert.equal(t.map.panCount, 0);
});
test('invalid GPS updates are ignored', () => {
  const t = setup(); t.window.updateVehicleLocation(NaN, 51); t.window.updateVehicleLocation(99, 51);
  assert.equal(t.map.panCount, 0);
});
test('a successful tile hides the loading/offline overlay', () => {
  const t = setup(); t.layers[0].handlers.tileload();
  assert.equal(t.get('offline').style.display, 'none');
});
test('blocked tiles show a message and stale callbacks cannot replace the current layer', () => {
  const t = setup(), old = t.layers[0];
  for (let i = 0; i < 4; i++) old.handlers.tileerror();
  assert.equal(t.get('offline').style.display, 'block');
  t.events.online();
  for (let i = 0; i < 12; i++) old.handlers.tileerror();
  assert.equal(t.layers.length, 2);
  assert.ok(t.layers[1].url.includes('tile.openstreetmap.org'));
});
test('all providers unavailable show an honest message and recover automatically', () => {
  const t = setup();
  t.fireTimer(5000);
  assert.equal(t.get('offline').style.display, 'block');
  assert.match(t.get('mapNotice').textContent, /NO STREET TILES/);
  t.fireTimer(30000);
  t.layers.at(-1).handlers.tileload();
  assert.equal(t.get('offline').style.display, 'none');
});
test('network restoration retries immediately', () => {
  const t = setup(); t.events.online();
  assert.equal(t.layers.length, 2);
});
test('losing internet after driving away retries rather than leaving an empty map', () => {
  const t = setup(); t.layers[0].handlers.tileload();
  t.layers[0].handlers.loading(); t.fireTimer(5000);
  assert.equal(t.get('offline').style.display, 'block');
  t.fireTimer(30000);
  assert.equal(t.layers.length, 2);
});
test('legacy theme parameters do not change standard street tiles', () => {
  const t = setup('?lat=25.1712&lon=51.5689&theme=dark');
  assert.equal(t.window.setMapTheme, undefined);
  assert.equal(t.layers.length, 1);
  assert.equal(t.get('status').textContent, 'TILES: OPENSTREETMAP');
});
test('missing map engine is visible rather than silently blank', () => {
  const t = setup('', false);
  assert.equal(t.window.mapReady, false);
  assert.match(t.get('mapNotice').textContent, /MAP ENGINE UNAVAILABLE/);
});
test('orientation change invalidates viewport size', () => {
  const t = setup(); t.events.resize(); assert.equal(t.map.resizeCount, 1);
});
test('map retains provider attribution', () => {
  assert.ok(setup().layers[0].opts.attribution.includes('OpenStreetMap'));
});
test('street tiles have no local dark styling or API-key-gated services', () => {
  assert.ok(setup().layers.every(x => x.url.startsWith('https://tile.openstreetmap.org/')));
  assert.doesNotMatch(html, /dark-map|setMapTheme|hue-rotate|filter:\s*invert/);
});
console.log(`${passed} Android map regression tests passed`);
