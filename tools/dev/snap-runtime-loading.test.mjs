import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';

const source = await fs.readFile(new URL('../../resources/programs/20260212TurtleStitch/fullturtle.js', import.meta.url), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));

function fixture({loading = false, idle = true} = {}) {
  const tasks = [], scripts = [], listeners = [];
  const window = {};
  if (idle) window.requestIdleCallback = (run, options) => tasks.push({run, delay:options.timeout});
  const document = {
    readyState: loading ? 'loading' : 'complete',
    addEventListener: (name, run, options) => listeners.push({name,run,options}),
    createElement: tag => {
      assert.equal(tag, 'script', 'preheating creates no editor/canvas');
      return {attributes:{}, setAttribute(name,value) {this.attributes[name] = value;}};
    },
    head: {appendChild: script => scripts.push(script)}
  };
  vm.runInNewContext(source, {window,document,Promise,console:{log(){},warn(){},error(){}},
    setTimeout:(run,delay) => tasks.push({run,delay})});
  return {window,tasks,scripts,listeners};
}

async function loadAll(f) {
  while (f.tasks.length) {
    const task = f.tasks.shift();
    const before = f.scripts.length;
    task.run();
    await flush();
    if (f.scripts.length > before) {
      assert.equal(f.scripts.length, before + 1, 'dependencies load sequentially');
      const script = f.scripts.at(-1);
      assert.equal(script.async, false);
      assert.equal(script.attributes.fetchpriority, 'low');
      script.onload();
      await flush();
    }
  }
  await f.window.TurtleStitchPoCReady;
}

test('Snap starts once at DOM readiness, yields at idle priority and loads every dependency without an editor', async () => {
  const f = fixture({loading:true});
  assert(f.window.TurtleStitchPoC);
  assert.equal(f.scripts.length, 0);
  assert.equal(f.tasks.length, 0);
  assert.equal(f.listeners.length, 1);
  assert.equal(f.listeners[0].name, 'DOMContentLoaded');
  assert.equal(f.listeners[0].options.once, true);
  f.listeners[0].run();
  assert.equal(f.tasks[0].delay, 2000);
  assert.equal(f.scripts.length, 0);
  const ready = f.window.TurtleStitchPoCReady;
  await loadAll(f);
  assert.equal(f.window.TurtleStitchPoCReady, ready);
  assert.equal(f.scripts.length, 22);
  assert.equal(new Set(f.scripts.map(script => script.src)).size, 22);
  assert(f.scripts[0].src.endsWith('/adjusted/adjustedMorphic.js'));
  assert(f.scripts.at(-1).src.endsWith('/turtlestitchsrc/embroider.js'));
  assert(f.window.TurtleStitchPoC._impl);
});

test('Snap preheats after a short timer when idle callbacks are unavailable', async () => {
  const f = fixture({idle:false});
  assert.equal(f.tasks[0].delay, 250);
  assert.equal(f.scripts.length, 0);
  await loadAll(f);
  assert.equal(f.scripts.length, 22);
});

test('failed background loading rejects the shared readiness promise and does not load later dependencies', async () => {
  const f = fixture();
  f.tasks.shift().run();
  await flush();
  f.tasks.shift().run();
  await flush();
  f.scripts[0].onerror();
  await assert.rejects(f.window.TurtleStitchPoCReady, /Failed to load adjusted\/adjustedMorphic.js/);
  assert.match(f.window.TurtleStitchPoCError.message, /Failed to load/);
  assert.equal(f.scripts.length, 1);
  assert.equal(f.tasks.length, 0);
});
