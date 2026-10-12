import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
function result(reply) {
  function find(value) {
    if (!value || typeof value !== 'object') return;
    if (typeof value.json === 'string' && typeof value.classSimpleName === 'string') return JSON.parse(value.json);
    for (const child of Object.values(value)) { const found = find(child); if (found !== undefined) return found; }
  }
  return find(reply.response.result);
}

test('built worker.js executes framework commands independently of the browser event loop', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',args:['--no-sandbox']});
  try {
    const context = await browser.newContext();
    await context.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/worker-test') return route.fulfill({contentType:'text/html',body:'<!doctype html><p id="status">Ready</p>'});
      if (url.pathname === '/artifacts/newest/worker.js') return route.fulfill({contentType:'text/javascript',body:await fs.readFile(path.join(root,'artifacts/newest/worker.js'))});
      return route.abort();
    });
    const page = await context.newPage();
    await page.goto('http://localhost:9000/worker-test');
    await page.evaluate(() => {
      const worker = new Worker('/artifacts/newest/worker.js');
      const pending = new Map();
      worker.onmessage = event => { const reply = JSON.parse(event.data); const promise = pending.get(reply.requestId); if (promise) { pending.delete(reply.requestId); promise.resolve(reply); } };
      worker.onerror = event => { for (const promise of pending.values()) promise.reject(new Error(event.message)); pending.clear(); };
      window.worker = worker;
      window.request = (requestId, name, json) => new Promise((resolve,reject) => { pending.set(requestId,{resolve,reject}); worker.postMessage(JSON.stringify({requestId,command:{name,json}})); });
    });
    const health = await page.evaluate(() => request('health','worker.health',{}));
    assert.equal(result(health).service,'edusquirrel-worker');
    assert.deepEqual(new Set(result(health).commands),new Set(['worker.health','worker.parseSnapXml','worker.calculateQrCode']));
    const replies = await page.evaluate(() => Promise.all([
      request('xml','worker.parseSnapXml',{xml:'<project><block s="Grüße"><block s="外"/></block></project>',tag:'block'}),
      request('qr','worker.calculateQrCode',{text:'Hello',version:2,mask:3})
    ]));
    assert.deepEqual(replies.map(reply=>reply.requestId),['xml','qr']);
    assert.deepEqual(result(replies[0]).map(element=>element.attributes.s),['Grüße','外']);
    assert(result(replies[1]),JSON.stringify(replies[1]));
    assert.equal(result(replies[1]).modules.length,25);
    const unsupported = await page.evaluate(() => request('no-handler','server-only',{}));
    assert.equal(result(unsupported),undefined);
    assert.equal(unsupported.requestId,'no-handler');
    const heavy = await page.evaluate(async () => {
      let ticks = 0;
      const interval = setInterval(() => { ticks++; document.querySelector('#status').textContent = String(ticks); },1);
      const reply = await request('heavy','worker.calculateQrCode',{text:'A'.repeat(500),version:40});
      clearInterval(interval);
      return {reply,ticks};
    });
    assert.equal(result(heavy.reply).modules.length,177);
    assert(heavy.ticks > 0,'the GUI event loop continues while the worker calculates all masks');
    await page.evaluate(() => worker.terminate());
  } finally { await browser.close(); }
});
