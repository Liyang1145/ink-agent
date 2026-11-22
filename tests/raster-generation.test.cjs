// Node-only reproduction of fonts/animation-frame ordering. This does not validate Android pixels.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const html = fs.readFileSync(process.env.RASTER_HTML ||
  path.join(__dirname, '../app/src/main/assets/raster.html'), 'utf8');
const script = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].at(-1)[1];

function setup() {
  const frames = [], fonts = [], done = [];
  let touches = 0;
  const box = {
    style: new Proxy({}, { set(obj, k, v) { touches++; obj[k] = v; return true; } }),
    querySelector: () => null,
    getBoundingClientRect: () => ({ left: 0, top: 0, right: 300, bottom: 300, width: 300, height: 300 })
  };
  const document = {
    getElementById: () => box, createTreeWalker: () => ({ nextNode: () => null }),
    createRange: () => ({}), fonts: { ready: { then: fn => fonts.push(fn) } }
  };
  const host = {
    console, document, NodeFilter: { SHOW_TEXT: 4 },
    window: { innerWidth: 800, innerHeight: 800 },
    requestAnimationFrame: fn => frames.push(fn),
    Raster: { done: (...args) => done.push(args) }
  };
  vm.runInNewContext(script, host);
  function render(id, kind) {
    // Accept the old host contract too, to reproduce behavior on the unfixed asset.
    if (host.render.length === 5) host.render(kind, '', 0, 800, 800);
    else host.render(id, kind, '', 0, 800, 800);
  }
  function nextFrame() { assert.ok(frames.length); frames.shift()(); }
  function drain() { while (frames.length) nextFrame(); }
  return { render, nextFrame, drain, fonts, done, touches: () => touches };
}

test('an obsolete fonts-ready continuation never schedules measurement or alters styles', () => {
  const e = setup();
  e.render('1', 'svg'); e.render('2', 'calib');
  const initial = e.touches();
  e.fonts.shift()(); e.drain();
  assert.equal(e.touches(), initial);
  assert.equal(e.done.length, 0);
  e.fonts.shift()(); e.drain();
  assert.deepEqual(e.done.map(([id, , , , diag]) => [id, JSON.parse(diag).error]), [['2', undefined]]);
});

test('an already queued obsolete measurement never touches the new DOM', () => {
  const e = setup();
  e.render('1', 'calib'); e.fonts.shift()();
  e.render('2', 'svg');
  const initial = e.touches();
  e.nextFrame();
  assert.equal(e.touches(), initial);
  assert.equal(e.done.length, 0);
  e.fonts.shift()(); e.drain();
  assert.equal(e.done.length, 1);
  assert.equal(e.done[0][0], '2');
  assert.match(JSON.parse(e.done[0][4]).error, /svg/);
});

test('obsolete nested frames never complete the replacement request', () => {
  for (const steps of [1, 2]) {
    const e = setup();
    e.render('1', 'svg'); e.fonts.shift()();
    for (let i = 0; i < steps; i++) e.nextFrame();
    e.render('2', 'calib');
    e.fonts.shift()(); e.drain();
    assert.deepEqual(e.done, [['2', 300, 300, 1, '{"notes":[],"warnings":[]}']]);
  }
});
