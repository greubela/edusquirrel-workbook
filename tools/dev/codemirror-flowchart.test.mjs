import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createServer} from 'node:http';
import {resolve, extname, sep} from 'node:path';
import {test} from 'node:test';
import {chromium} from 'playwright';

// Uses the production CDN modules by default. An optional bundle of the same
// loader/modules lets restricted/offline environments run the browser check.
test('flowchart gutter tracks CodeMirror lines, fonts, edits and scrolling', async t => {
  const root = resolve(import.meta.dirname, '../..');
  const mime = {'.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css',
    '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png'};
  const server = createServer(async (request, response) => {
    try {
      const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
      const name = pathname === '/homepage/js/loader.js' && process.env.CODEMIRROR_TEST_BUNDLE
        ? resolve(process.env.CODEMIRROR_TEST_BUNDLE)
        : resolve(root, '.' + pathname + (pathname.endsWith('/') ? 'index.html' : ''));
      if (pathname !== '/homepage/js/loader.js' && !name.startsWith(root + sep)) throw new Error('Outside repository');
      response.setHeader('Content-Type', mime[extname(name)] || 'application/octet-stream');
      response.end(await readFile(name));
    } catch { response.writeHead(404).end(); }
  });
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const base = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({
    ...(process.env.CHROMIUM_PATH ? {executablePath: process.env.CHROMIUM_PATH} : {}),
    headless: true, chromiumSandbox: true
  });
  try {
    const page = await browser.newPage({viewport: {width: 900, height: 600}});
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(base + '/homepage/workbookDesign/');
    await page.waitForFunction(() => typeof globalThis.EduSquirrelCodeMirrorScala?.createEditor === 'function');
    await page.evaluate(() => {
      document.body.replaceChildren();
      document.body.insertAdjacentHTML('beforeend', '<style>#flow .cm-editor, #plain .cm-editor {height:260px} #flow .cm-scroller, #plain .cm-scroller {overflow:auto} #flow {--code-font-size:14px}</style><div id="flow"></div><div id="plain"></div>');
    });
    await page.addScriptTag({type: 'module', url: `${base}/homepage/js/${process.env.CODEMIRROR_TEST_BUNDLE ? 'loader.js' : 'CodeMirrorLoader.js'}`});
    await page.waitForFunction(() => !!globalThis.EduSquirrelCodeMirror);
    await page.evaluate(() => {
      window.changes = [];
      // Semantic graph generation is exercised by PythonFlowchartGutterSpec;
      // these fixtures exercise the actual editor and marker layout lifecycle.
      window.graphCalls = 0;
      const chart = doc => {
        window.graphCalls++;
        if (doc.includes('INVALID')) return {nodes: [], edges: [], entries: []};
        const nodes = doc.split('\n').flatMap((label, index) => label.trim() ? [{
          line: index + 1, kind: label.startsWith('if ') ? 'decision' : 'process', depth: label.startsWith('    ') ? 1 : 0, label
        }] : []);
        const edges = nodes.slice(1).map((node, index) => ({from: nodes[index].line, to: node.line, kind: 'next'}));
        return {nodes, edges, entries: nodes.length ? [nodes[0].line] : []};
      };
      window.editor = EduSquirrelCodeMirror.createEditor({parent: document.querySelector('#flow'),
        doc: Array.from({length: 100}, (_, index) => index === 2 ? '' : `print(${index})`).join('\n'),
        flowchart: chart, onDocChange: doc => window.changes.push(doc)});
      window.plain = EduSquirrelCodeMirror.createEditor({parent: document.querySelector('#plain'), doc: 'print(1)'});
      const emptyHost = document.body.appendChild(document.createElement('div'));
      window.empty = EduSquirrelCodeMirror.createEditor({parent: emptyHost});
    });
    assert.equal(await page.locator('#plain .cm-flowchart-gutter').count(), 0, 'regular editor stays unchanged');
    assert.equal(await page.evaluate(() => empty.getDoc()), '', 'omitted document and language retain their defaults');
    await page.waitForFunction(() => document.querySelector('#flow .cm-flowchart-row[title]'));
    const initialCalls = await page.evaluate(() => window.graphCalls);
    const aligned = async () => {
      const result = await page.evaluate(() => {
        const gutter = document.querySelector('#flow .cm-flowchart-gutter');
        const numbers = document.querySelector('#flow .cm-lineNumbers');
        const code = document.querySelector('#flow .cm-content');
        const rows = [...gutter.querySelectorAll('.cm-gutterElement')].filter(row => row.getBoundingClientRect().height > 2);
        const numberRows = [...numbers.querySelectorAll('.cm-gutterElement')].filter(row => row.getBoundingClientRect().height > 2);
        const codeRows = [...code.querySelectorAll(".cm-line")];
        const differences = rows.map((row, index) => {
          const a = row.getBoundingClientRect(), b = numberRows[index].getBoundingClientRect();
          const c = codeRows[index].getBoundingClientRect();
          const svg = row.querySelector('svg').getBoundingClientRect();
          return Math.max(Math.abs(a.top - b.top), Math.abs(a.height - b.height), Math.abs(a.top - c.top), Math.abs(a.height - c.height), Math.abs(svg.height - a.height));
        });
        return {differences, order: gutter.getBoundingClientRect().right <= numbers.getBoundingClientRect().left && numbers.getBoundingClientRect().right <= code.getBoundingClientRect().left};
      });
      assert(result.order, 'flowchart precedes line numbers and code');
      assert(result.differences.length > 5, 'visible rows have gutter markers');
      assert(result.differences.every(value => value < 1), `SVG and number gutters share physical row bounds: ${JSON.stringify(result)}`);
    };
    await aligned();
    await page.evaluate(() => { document.querySelector('#flow').style.setProperty('--code-font-size', '22px'); });
    await page.waitForTimeout(100);
    await aligned();
    await page.evaluate(() => { document.querySelector('#flow .cm-scroller').scrollTop = 650; });
    await page.waitForTimeout(100);
    await aligned();
    assert.equal(await page.evaluate(() => window.graphCalls), initialCalls, "scrolling and font changes do not reparse source");
    await page.evaluate(() => editor.focus());
    await page.keyboard.press('Control+Home');
    await page.keyboard.type('# edited\n');
    await page.waitForFunction(() => window.graphCalls > 1);
    assert(await page.evaluate(() => window.changes.length > 0 && window.graphCalls > 1), 'user edits refresh the graph and invoke the callback');
    const changeCount = await page.evaluate(() => window.changes.length);
    await page.evaluate(() => editor.setDoc('if True:\n    print(1)\nprint(2)'));
    await page.waitForFunction(() => document.querySelector('#flow .cm-flowchart-row[title^="decision:"]'));
    assert.equal(await page.evaluate(() => window.changes.length), changeCount, 'external updates do not fire user input callbacks');
    assert.equal(await page.locator('#flow .cm-flowchart-row[title^="decision:"]').count(), 1);
    await page.evaluate(() => editor.setDiagnostics([{line: 2, message: 'test', severity: 'warning'}]));
    assert.equal(await page.locator('#flow .cm-edusquirrel-diagnostic').count(), 1, 'diagnostics still work');
    const callsBeforeInvalid = await page.evaluate(() => window.graphCalls);
    await page.evaluate(() => editor.setDoc('INVALID'));
    await page.waitForFunction(count => window.graphCalls > count &&
      document.querySelectorAll('#flow .cm-flowchart-row[title]').length === 0, callsBeforeInvalid);
    assert.equal(await page.locator('#flow .cm-flowchart-row[title]').count(), 0, 'unsupported source clears stale nodes');
    await page.evaluate(() => { editor.destroy(); plain.destroy(); empty.destroy(); });
    assert.equal(await page.locator('.cm-editor').count(), 0, 'destroy removes the editors');
    await t.test('async graphs ignore stale requests, debounce edits and survive failure or destroy', async () => {
      const deferred = await page.evaluate(() => {
        const host = document.body.appendChild(document.createElement('div'));
        host.id = 'async-flow';
        window.graphRequests = [];
        window.asyncEditor = EduSquirrelCodeMirror.createEditor({parent: host, doc: 'A',
          flowchart: source => new Promise((resolve, reject) => graphRequests.push({source, resolve, reject}))});
        return graphRequests.length;
      });
      assert.equal(deferred, 0, 'initial analysis does not block editor construction');
      const requested = count => page.waitForFunction(count => graphRequests.length === count, count);
      const settle = (index, label, reject = false) => page.evaluate(async ({index, label, reject}) => {
        if (reject) graphRequests[index].reject(new Error(label));
        else graphRequests[index].resolve({nodes: [{line: 1, kind: 'process', depth: 0, label}], edges: [], entries: [1]});
        await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      }, {index, label, reject});
      const marker = label => page.waitForFunction(label =>
        document.querySelector('#async-flow .cm-flowchart-row[title]')?.title === `process: ${label}`, label);
      const markerCount = () => page.locator('#async-flow .cm-flowchart-row[title]').count();
      await requested(1);
      assert.equal(await markerCount(), 0, 'pending analysis has no fabricated graph');
      await page.evaluate(() => asyncEditor.setDoc('B'));
      await requested(2);
      await page.evaluate(() => asyncEditor.setDoc('A'));
      await requested(3);
      await settle(2, 'current A');
      await marker('current A');
      await settle(1, 'stale B');
      await settle(0, 'old A');
      assert.equal(await page.locator('#async-flow .cm-flowchart-row[title]').getAttribute('title'), 'process: current A',
        'generation checks reject older requests even when their source matches again');
      await page.evaluate(() => asyncEditor.setDoc('FAILED'));
      assert.equal(await markerCount(), 1, 'an edit keeps the previous graph until analysis finishes');
      await requested(4);
      await settle(3, 'analysis failed', true);
      assert.equal(await markerCount(), 0, 'failed analysis leaves an empty gutter');
      assert.equal(await page.evaluate(() => asyncEditor.getDoc()), 'FAILED', 'failure preserves the document');
      await page.evaluate(() => asyncEditor.setDoc('RECOVERED'));
      await requested(5);
      await settle(4, 'recovered');
      await marker('recovered');
      await page.evaluate(() => {
        asyncEditor.setDoc('burst 1');
        asyncEditor.setDoc('burst 2');
        asyncEditor.setDoc('burst 3');
      });
      await requested(6);
      assert.deepEqual(await page.evaluate(() => graphRequests.map(request => request.source)),
        ['A', 'B', 'A', 'FAILED', 'RECOVERED', 'burst 3'], 'a burst sends only the latest document for analysis');
      await settle(5, 'burst 3');
      await marker('burst 3');
      await page.evaluate(() => asyncEditor.setDoc('DESTROYED'));
      await requested(7);
      await page.evaluate(() => asyncEditor.destroy());
      await settle(6, 'late result');
      assert.equal(await page.locator('#async-flow .cm-editor').count(), 0, 'late results cannot recreate a destroyed editor');
      const cancelled = await page.evaluate(async () => {
        let calls = 0;
        const host = document.body.appendChild(document.createElement('div'));
        const editor = EduSquirrelCodeMirror.createEditor({parent: host, doc: 'cancelled',
          flowchart: () => { calls++; return {nodes:[],edges:[],entries:[]}; }});
        editor.destroy();
        await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
        return calls;
      });
      assert.equal(cancelled, 0, 'destroy cancels scheduled analysis before it starts');
    });
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
    await new Promise(done => server.close(done));
  }
});
