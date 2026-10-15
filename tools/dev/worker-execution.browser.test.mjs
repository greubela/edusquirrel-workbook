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
      const pyodideRoot = 'https://cdn.jsdelivr.net/pyodide/v0.29.3/full/';
      if (url.href.startsWith(pyodideRoot)) {
        const name = url.pathname.split('/').at(-1);
        return route.fulfill({body:await fs.readFile(path.join(root,'node_modules/pyodide',name)),
          contentType:name.endsWith('.wasm')?'application/wasm':name.endsWith('.js')?'text/javascript':'application/octet-stream',
          headers:{'access-control-allow-origin':'*'}});
      }
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
    assert.deepEqual(new Set(result(health).commands),new Set(['worker.health','worker.parseSnapXml','worker.pythonFlowchart','worker.calculateQrCode','worker.runPython']));
    const replies = await page.evaluate(() => Promise.all([
      request('xml','worker.parseSnapXml',{xml:'<project><block s="Grüße"><block s="外"/></block></project>',tag:'block'}),
      request('qr','worker.calculateQrCode',{text:'Hello',version:2,mask:3})
    ]));
    assert.deepEqual(replies.map(reply=>reply.requestId),['xml','qr']);
    assert.deepEqual(result(replies[0]).map(element=>element.attributes.s),['Grüße','外']);
    assert(result(replies[1]),JSON.stringify(replies[1]));
    assert.equal(result(replies[1]).modules.length,25);
    const chart = result(await page.evaluate(() => request('chart','worker.pythonFlowchart',{
      source:'# Grüße 外\r\nx = 1\r\nif x > 0:\r\n\tprint(x)\r\nprint(2)\r\n'
    })));
    assert.deepEqual(chart.nodes.map(node => node.line),[2,3,4,5]);
    assert.deepEqual(chart.entries,[2]);
    const flowchartHeavy = await page.evaluate(async () => {
      const source = 'import turtle\nimport java_turtle\n\n' + Array.from({length:8},(_,index) =>
        `def shape${index}(edges: int, size: float, zigzag: bool) -> None:\n` +
        '    edges = java_turtle.cell(edges)\n    size = java_turtle.cell(size)\n    zigzag = java_turtle.cell(zigzag)\n' +
        '    if java_turtle.compare("le", edges[0], 0, False):\n        return\n' +
        '    i = java_turtle.cell(0)\n    while java_turtle.compare("lt", i[0], edges[0], False):\n' +
        '        step = java_turtle.cell(java_turtle.double_op("add", size[0], float(i[0])))\n' +
        '        if (zigzag[0] and java_turtle.compare("eq", java_turtle.int_op("rem", i[0], 2), 0, False)):\n' +
        '            step[0] = java_turtle.double_op("add", step[0], 1.0)\n' +
        '        turtle.forward(step[0])\n        turtle.right(java_turtle.double_op("div", 360.0, float(edges[0])))\n' +
        '        java_turtle.update(i, 1, False, False)\n').join('\n') + '\nshape0(4, 10.0, True)\n';
      let ticks = 0, frames = 0, frame;
      const interval = setInterval(() => { ticks++; document.querySelector('#status').textContent = String(ticks); },1);
      const animate = () => { frames++; frame = requestAnimationFrame(animate); };
      frame = requestAnimationFrame(animate);
      try {
        const reply = await request('large-chart','worker.pythonFlowchart',{source});
        return {reply,ticks,frames,chars:source.length};
      } finally { clearInterval(interval); cancelAnimationFrame(frame); }
    });
    assert(flowchartHeavy.chars > 5000,'the fixture exercises generated typed cell operations');
    assert.deepEqual(result(flowchartHeavy.reply),{nodes:[],edges:[],entries:[]},'unsupported generated code returns an empty chart');
    assert(flowchartHeavy.ticks > 0 && flowchartHeavy.frames > 0,'the GUI can update and render during worker parsing');
    assert.equal(result(await page.evaluate(() => request('chart-recovered','worker.pythonFlowchart',{source:'print(1)'}))).nodes.length,1);
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
    const python = result(await page.evaluate(() => request('python','worker.runPython',{
      code:"print('Grüße')\nimport sys\nprint('warning',file=sys.stderr)\n{'sum':sum(numbers),'label':settings['label']}",
      context:{numbers:[2,3],settings:{label:'学校'}}
    })));
    assert.deepEqual(python.value,{sum:5,label:'学校'});
    assert.equal(python.stdout,'Grüße\n');
    assert.equal(python.stderr,'warning\n');
    const partial = result(await page.evaluate(() => request('partial','worker.runPython',{
      code:"print('学校😀',end='')\nimport sys\nprint('warn',end='',file=sys.stderr)"
    })));
    assert.equal(partial.stdout,'学校😀');
    assert.equal(partial.stderr,'warn');
    const muted = result(await page.evaluate(() => request('muted','worker.runPython',{
      code:"print('hidden')\nimport sys\nprint('hidden',file=sys.stderr)",captureStdout:false,captureStderr:false
    })));
    assert.equal(muted.stdout,'');
    assert.equal(muted.stderr,'');
    const callbacks = result(await page.evaluate(() => request('callbacks','worker.runPython',{
      code:'from pen import forward\nfor i in range(3): forward(i)',callbacks:{pen:['forward']}
    })));
    assert.deepEqual(callbacks.callbackOps.map(op=>op.args),[[0],[1],[2]]);
    const retainedCallback = result(await page.evaluate(() => request('retained-callback','worker.runPython',{
      code:'forward(10)',callbacks:{pen:['forward']},resetGlobals:false
    })));
    assert.deepEqual(retainedCallback.callbackOps.map(op=>op.args),[[10]]);
    await page.evaluate(() => request('state','worker.runPython',{code:'counter=40',resetGlobals:false}));
    assert.equal(result(await page.evaluate(() => request('state2','worker.runPython',{code:'counter+2',resetGlobals:false}))).value,42);
    assert.equal(result(await page.evaluate(() => request('reset','worker.runPython',{code:"'counter' in globals()"}))).value,false);
    const concurrent = await page.evaluate(() => Promise.all([
      request('first','worker.runPython',{code:"import asyncio\nprint('first')\nawait asyncio.sleep(0.05)\nprint('end')\n'one'"}),
      request('second','worker.runPython',{code:"print('second')\n'other'"})
    ]));
    assert.equal(result(concurrent[0]).stdout,'first\nend\n');
    assert.equal(result(concurrent[1]).stdout,'second\n');
    const failed = await page.evaluate(() => request('python-error','worker.runPython',{code:"print('before error')\nraise ValueError('boom')"}));
    assert.equal(result(failed),undefined);
    assert(JSON.stringify(failed.response).includes('boom'));
    assert(failed.response.additionalOutput.loggerOut.includes('before error'));
    for (const code of ["float('nan')",'{1,2}', '10**30']) {
      const invalid = await page.evaluate(code => request('invalid-result','worker.runPython',{code}),code);
      assert.equal(result(invalid),undefined,'non-JSON results fail instead of silently losing data');
    }
    assert.equal(result(await page.evaluate(() => request('recover','worker.runPython',{code:'6*7'}))).value,42);
    const pythonHeavy = await page.evaluate(async () => {
      let ticks=0; const interval=setInterval(()=>ticks++,1);
      const reply=await request('python-heavy','worker.runPython',{code:'sum(i % 97 for i in range(1500000))'});
      clearInterval(interval); return {reply,ticks};
    });
    assert(result(pythonHeavy.reply));
    assert(pythonHeavy.ticks>0,'the GUI stays responsive during real Pyodide execution');
    await page.evaluate(() => worker.terminate());
  } finally { await browser.close(); }
});
