import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const pyodideRoot = 'https://cdn.jsdelivr.net/pyodide/v0.29.5/full/';
function result(reply) {
  function find(value) {
    if (!value || typeof value !== 'object') return;
    if (typeof value.json === 'string' && typeof value.classSimpleName === 'string') return JSON.parse(value.json);
    for (const child of Object.values(value)) { const found = find(child); if (found !== undefined) return found; }
  }
  return find(reply.response.result);
}

test('Pyodide runs on the main thread and in a worker, with independent interpreters and a responsive GUI', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',args:['--no-sandbox']});
  try {
    const context = await browser.newContext();
    await context.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/edusquirrel-workbook/worker-test') return route.fulfill({contentType:'text/html',body:'<!doctype html><p id="status">Ready</p>'});
      if (url.pathname === '/edusquirrel-workbook/artifacts/newest/worker.js') return route.fulfill({contentType:'text/javascript',body:await fs.readFile(path.join(root,'artifacts/newest/worker.js'))});
      if (url.href.startsWith(pyodideRoot)) {
        const name = url.pathname.split('/').at(-1);
        return route.fulfill({body:await fs.readFile(path.join(root,'node_modules/pyodide',name)),
          contentType:name.endsWith('.wasm')?'application/wasm':name.endsWith('.js')?'text/javascript':'application/octet-stream',
          headers:{'access-control-allow-origin':'*'}});
      }
      return route.abort();
    });
    const page = await context.newPage();
    await installCodeMirrorModules(page);
    await page.goto('http://localhost:9000/edusquirrel-workbook/worker-test');
    await page.evaluate(() => {
      const worker = new Worker('/edusquirrel-workbook/artifacts/newest/worker.js');
      const pending = new Map();
      worker.onmessage = event => { const reply = JSON.parse(event.data); const promise = pending.get(reply.requestId); if (promise) { pending.delete(reply.requestId); promise.resolve(reply); } };
      worker.onerror = event => { for (const promise of pending.values()) promise.reject(new Error(event.message)); pending.clear(); };
      window.worker = worker;
      window.request = (requestId, name, json) => new Promise((resolve,reject) => { pending.set(requestId,{resolve,reject}); worker.postMessage(JSON.stringify({requestId,command:{name,json}})); });
    });
    const health = await page.evaluate(() => request('health','worker.health',{}));
    assert.equal(result(health).service,'edusquirrel-worker');
    assert.deepEqual(new Set(result(health).commands),new Set(['worker.health','worker.parseSnapXml','worker.pythonFlowchart','worker.calculateQrCode','worker.runPython','worker.javaExecutionEvidence']));
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
    assert(chart.flow[0].some(row => row.line === 3 && row.kind === "if"), "worker sends the source-aligned nested flow plan");
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
    const pythonReply = await page.evaluate(() => request('python','worker.runPython',{
      code:"print('Grüße')\nimport sys\nprint('warning',file=sys.stderr)\n{'sum':sum(numbers),'label':settings['label']}",
      context:{numbers:[2,3],settings:{label:'学校'}}
    }));
    const python = result(pythonReply);
    assert(python, JSON.stringify(pythonReply));
    assert.deepEqual(python.value,{sum:5,label:'学校'});
    assert.equal(python.stdout,'Grüße\n');
    assert.equal(python.stderr,'warning\n');
    const pythonNone = result(await page.evaluate(() => request('python-none','worker.runPython',{
      code:"{'status':'Completed','problem':None,'commands':[],'steps':2}"
    })));
    assert.deepEqual(pythonNone.value,{status:'Completed',problem:null,commands:[],steps:2});
    assert.equal(Object.hasOwn(pythonNone.value,'problem'),true,'Python None remains a JSON null');
    const pythonContext = result(await page.evaluate(() => request('python-json-context','worker.runPython',{
      code:"{'top':optional is None,'nested':data['items'][0] is None,'proto':data['__proto__']['value'] is None,'constructor':data['constructor'],'keys':sorted(data.keys()),'values':data['items'][1:]}",
      context:{optional:null,data:JSON.parse('{"items":[null,1,3.5,true,"学校😀"],"__proto__":{"value":null},"constructor":"kept"}')}
    })));
    assert.deepEqual(pythonContext.value,{top:true,nested:true,proto:true,constructor:'kept',
      keys:['__proto__','constructor','items'],values:[1,3.5,true,'学校😀']});
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
    const wrap = (main, helpers = '') => `class Drawing { ${helpers} public static void main(String[] args) { ${main} } }`;
    const evidenceCases = [
      {id:'empty',source:wrap(''),count:0},
      {id:'four-sides',source:wrap('for(int i=0;i<4;i++){Turtle.forward(10);Turtle.turnRight(90);}'),count:8},
      {id:'three-sides',source:wrap('for(int i=0;i<3;i++){Turtle.forward(10);Turtle.turnRight(90);}'),count:6},
      {id:'named-helper',source:wrap('Turtle.forward(99);',
        'static void draw(double length,int n){for(int i=0;i<n;i++){Turtle.forward(length);}}'),
        method:'draw',arguments:[{$type:'Float64',bits:'3fc0000000000000'},{$type:'Int32',value:2}],count:2,onlyMethod:'draw'},
      {id:'recursive-helper',source:wrap('draw(3);',
        'static void draw(int n){if(n==0){Turtle.forward(1);return;}draw(n-1);Turtle.turnRight(60);draw(n-1);}'),count:15},
      {id:'unicode-zero',source:'class Drawing {\r\n// Grüße 🐢\r\npublic static void main(String[] args){\r\n double z=-0.0;Turtle.forward(z);double n=0.0/0.0;if(n!=n){Turtle.turnRight(90);}\r\n}}',
        count:2,bits:['8000000000000000','nan']},
      {id:'boolean-argument',source:wrap('', 'static void draw(boolean enabled){if(enabled){Turtle.forward(4);}}'),
        method:'draw',arguments:[{$type:'Bool',value:true}],count:1},
      {id:'zero-argument',source:wrap('', 'static void draw(double length){Turtle.forward(length);}'),
        method:'draw',arguments:[{$type:'Float64',bits:'8000000000000000'}],count:1,bits:['8000000000000000']},
      ...['0000000000000001','7fefffffffffffff','nan'].map((bits,index) => ({id:'double-argument-' + index,
        source:wrap('', 'static void draw(double length){Turtle.forward(length);}'),
        method:'draw',arguments:[{$type:'Float64',bits}],count:bits === 'nan' ? 0 : 1,
        outcome:bits === 'nan' ? 'NonFiniteCommand' : 'Completed',bits:[bits]})),
      {id:'division-prefix',source:wrap('Turtle.forward(2);int n=1/0;Turtle.forward(n);'),count:1,outcome:'DivisionByZero'},
      {id:'nonfinite-prefix',source:wrap('Turtle.forward(2);Turtle.forward(1.0/0.0);'),count:1,outcome:'NonFiniteCommand'},
      {id:'all-expressions',source:wrap('for(int i=0;i<40;i++){Turtle.forward(i);}'),count:40,minExpressions:65},
      {id:'limited-loop',source:wrap('while(true){}'),limits:{maxSteps:40},count:0,outcome:'LimitExceeded'},
      {id:'incomplete',source:'class Drawing { public static void main(String[] args){',reject:true},
      {id:'unknown-method',source:wrap(''),method:'missing',reject:true},
      {id:'wrong-argument',source:wrap('', 'static void draw(int n){Turtle.forward(n);}'),
        method:'draw',arguments:[{$type:'Bool',value:true}],reject:true},
      {id:'fractional-argument',source:wrap('', 'static void draw(int n){Turtle.forward(n);}'),
        method:'draw',arguments:[{$type:'Int32',value:1.9}],reject:true,decodeReject:true},
      {id:'wrapped-argument',source:wrap('', 'static void draw(int n){Turtle.forward(n);}'),
        method:'draw',arguments:[{$type:'Int32',value:2147483648}],reject:true,decodeReject:true},
      {id:'fractional-limit',source:wrap(''),limits:{maxSteps:1.9},reject:true,decodeReject:true},
      {id:'trace-overflow',source:wrap('for(int i=0;i<1000;i++){Turtle.forward(i);}'),reject:true}
    ];
    for (const fixture of evidenceCases) {
      const {id,count,outcome,onlyMethod,bits,minExpressions,reject,decodeReject,...requestData} = fixture;
      const reply = await page.evaluate(({id,requestData}) => request(id,'worker.javaExecutionEvidence',requestData),{id,requestData});
      assert.equal(reply.requestId,id);
      const snapshot = result(reply);
      if (reject) {
        assert.equal(snapshot,undefined,id);
        const messages = JSON.stringify([reply.response.result,reply.response.additionalOutput]);
        assert(messages.includes(decodeReject ? 'sourceLength' : 'Java execution observations are unavailable.'),id);
        assert(!messages.includes('class Drawing') && !messages.includes('Turtle.forward'),id);
        assert.equal(result(await page.evaluate(() => request('evidence-recovery','worker.runPython',{code:'6*7'}))).value,42);
        continue;
      }
      assert(snapshot,JSON.stringify(reply));
      assert.equal(snapshot.outcome,outcome || 'Completed',id);
      assert.equal(snapshot.commandCount,count,id);
      assert(snapshot.steps > 0 && snapshot.steps <= (requestData.limits?.maxSteps || 100000),id);
      for (const expression of snapshot.expressions) {
        assert(Number.isInteger(expression.invocation) && expression.invocation >= 0,id);
        assert(expression.start >= 0 && expression.start < expression.end && expression.end <= requestData.source.length,id);
        if (onlyMethod) assert.equal(expression.method,onlyMethod,id);
      }
      for (const value of bits || []) assert(snapshot.expressions.some(expression => expression.value.bits === value),id);
      if (minExpressions) assert(snapshot.expressions.length >= minExpressions,id);
      if (id === 'empty') assert.deepEqual(snapshot.expressions,[]);
    }
    const evidenceConcurrent = await page.evaluate(fixtures => Promise.all(fixtures.map(({id,source,method,arguments:args}) =>
      request('parallel-' + id,'worker.javaExecutionEvidence',{source,method,arguments:args}))),
      [evidenceCases[1],evidenceCases[3]]);
    assert.deepEqual(evidenceConcurrent.map(reply => result(reply).commandCount),[8,2]);
    assert(evidenceConcurrent[1] && result(evidenceConcurrent[1]).expressions.every(expression => expression.method === 'draw'));
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
    // Scala DOM tests cover facade loading; verify the real pinned runtime in both contexts here.
    await page.addScriptTag({url:pyodideRoot + 'pyodide.js'});
    const localValue = await page.evaluate(async indexURL => {
      const interpreter = await loadPyodide({indexURL});
      return interpreter.runPythonAsync('main_thread_only = 21\nmain_thread_only * 2');
    }, pyodideRoot);
    assert.equal(localValue,42,'Python also executes directly on the browser main thread');
    const isolated = result(await page.evaluate(() => request('isolated','worker.runPython',{
      code: "'main_thread_only' in globals()"
    })));
    assert.equal(isolated.value,false,'main-thread globals do not leak into the worker interpreter');
    await page.evaluate(() => worker.terminate());
  } finally { await browser.close(); }
});
