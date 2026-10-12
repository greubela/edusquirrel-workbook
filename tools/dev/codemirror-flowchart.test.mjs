import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createServer} from 'node:http';
import {resolve} from 'node:path';
import {test} from 'node:test';
import {chromium} from 'playwright';

// Uses the production CDN modules by default. An optional bundle of the same
// loader/modules lets restricted/offline environments run the browser check.
test('flowchart gutter tracks CodeMirror lines, fonts, edits and scrolling', async () => {
  const server = createServer(async (request, response) => {
    try {
      if (request.url === "/") {
        response.setHeader("Content-Type", "text/html");
        response.end("<!doctype html><html><body></body></html>");
        return;
      }
      const name = request.url === '/loader.js' && process.env.CODEMIRROR_TEST_BUNDLE
        ? resolve(process.env.CODEMIRROR_TEST_BUNDLE)
        : resolve('homepage/js', request.url.slice(1));
      response.setHeader('Content-Type', 'text/javascript');
      response.end(await readFile(name));
    } catch { response.writeHead(404).end(); }
  });
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const base = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({
    ...(process.env.CHROMIUM_PATH ? {executablePath: process.env.CHROMIUM_PATH} : {}),
    headless: true, args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage({viewport: {width: 900, height: 600}});
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(base);
    await page.setContent('<style>.cm-editor {height:260px}.cm-scroller {overflow:auto} #flow {--code-font-size:14px}</style><div id="flow"></div><div id="plain"></div>');
    await page.addScriptTag({type: 'module', url: `${base}/${process.env.CODEMIRROR_TEST_BUNDLE ? 'loader.js' : 'CodeMirrorLoader.js'}`});
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
    });
    assert.equal(await page.locator('#plain .cm-flowchart-gutter').count(), 0, 'regular editor stays unchanged');
    await page.waitForTimeout(100);
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
      assert(result.differences.every(value => value < 1), 'SVG and number gutters share physical row bounds');
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
    await page.waitForTimeout(100);
    assert(await page.evaluate(() => window.changes.length > 0 && window.graphCalls > 1), 'user edits refresh the graph and invoke the callback');
    const changeCount = await page.evaluate(() => window.changes.length);
    await page.evaluate(() => editor.setDoc('if True:\n    print(1)\nprint(2)'));
    await page.waitForTimeout(100);
    assert.equal(await page.evaluate(() => window.changes.length), changeCount, 'external updates do not fire user input callbacks');
    assert.equal(await page.locator('#flow .cm-flowchart-row[title^="decision:"]').count(), 1);
    await page.evaluate(() => editor.setDiagnostics([{line: 2, message: 'test', severity: 'warning'}]));
    assert.equal(await page.locator('#flow .cm-edusquirrel-diagnostic').count(), 1, 'diagnostics still work');
    await page.evaluate(() => editor.setDoc('INVALID'));
    await page.waitForTimeout(100);
    assert.equal(await page.locator('#flow .cm-flowchart-row[title]').count(), 0, 'unsupported source clears stale nodes');
    await page.evaluate(() => { editor.destroy(); plain.destroy(); });
    assert.equal(await page.locator('.cm-editor').count(), 0, 'destroy removes the editors');
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
    await new Promise(done => server.close(done));
  }
});
