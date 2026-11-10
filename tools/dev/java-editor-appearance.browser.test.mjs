import {readCommandRequest} from './command-request.mjs';
import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const prefix = '/edusquirrel-workbook/';
const mime = {'.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css',
  '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png'};

test('Java hints retain in-flight feedback across editor close and display-language changes', async () => {
  const browser = await chromium.launch({chromiumSandbox: true,
    executablePath: process.env.CHROMIUM_PATH || chromium.executablePath()});
  try {
    const page = await browser.newPage({viewport: {width: 1440, height: 1000}});
    page.setDefaultTimeout(60000);
    const errors = [], requests = [], arrivals = [];
    const policy = {hold: false};
    const headers = {'access-control-allow-origin': origin, 'access-control-allow-credentials': 'true',
      'access-control-allow-methods': 'POST, OPTIONS', 'access-control-allow-headers': 'Content-Type, Authorization, Content-Encoding'};
    page.on('pageerror', error => errors.push(error.message));
    async function answer(pending, question = 'Wo beginnt dein Programm?', ids = ['source-1']) {
      const now = '2026-10-22T12:00:00';
      const value = JSON.stringify({hints: [{question, evidenceIds: ids}]});
      await pending.route.fulfill({headers, contentType: 'application/json', body: JSON.stringify({
        timestamps: {timestampReceived: now, timestampStarted: now, timestampFinished: now},
        result: [1, {json: JSON.stringify(value), classSimpleName: 'String'}],
        receivedExecutionCommand: pending.command, additionalOutput: {loggerOut: '', loggerError: ''}
      })});
    }
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/executeCommand') {
        if (route.request().method() === 'OPTIONS') return route.fulfill({status: 204, headers});
        const command = readCommandRequest(route.request());
        if (command.name !== 'feedback-llm-request') return route.abort();
        const pending = {route, command};
        requests.push(JSON.parse(command.json.prompt));
        arrivals.shift()?.(pending);
        if (!policy.hold) await answer(pending);
        return;
      }
      if (url.origin !== origin || !url.pathname.startsWith(prefix)) return route.abort();
      const relative = decodeURIComponent(url.pathname.slice(prefix.length));
      if (relative.split('/').includes('..')) return route.abort();
      const file = path.resolve(root, relative + (relative.endsWith('/') ? 'index.html' : ''));
      try { await route.fulfill({body: await readFile(file), contentType: mime[path.extname(file)] || 'application/octet-stream'}); }
      catch { await route.fulfill({status: 404, body: 'Not found'}); }
    });
    await installCodeMirrorModules(page);
    await page.goto(origin + prefix + 'homepage/workbookDesign/');
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.section-block').first().click();
    await page.locator('.select-language-line > div:has(.flag-fill-german-red)').click();
    await page.getByRole('heading', {name: 'Ein Quadrat in Java', exact: true}).waitFor();
    const inlineHint = page.locator('.scaffolding-info').first();
    assert.equal(requests.length, 0);
    await inlineHint.click();
    await page.waitForFunction(() => [...document.querySelectorAll('.scaffolding-info')].some(
      icon => icon.dataset.tooltip === '1. Wo beginnt dein Programm?'));
    assert.equal(requests.length, 1);
    await inlineHint.click();
    assert.equal(requests.length, 1, 'completed feedback is cached');
    await page.getByRole('button', {name: /Editor.*öffnen|Open editor/i}).last().click();
    const editor = page.locator('dialog[open] .eva-editor');
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
    const code = editor.locator('.cm-content');
    const draft = 'class Drawing {\n  static void square(int side) {';
    await code.click();
    await page.keyboard.press('ControlOrMeta+A');
    await page.keyboard.insertText(draft);
    policy.hold = true;
    const received = new Promise(resolve => arrivals.push(resolve));
    await page.locator('dialog[open] .scaffolding-info').click();
    let requestDeadline;
    const pending = await Promise.race([received, new Promise((_, reject) => {
      requestDeadline = setTimeout(() => reject(new Error('Expected a hint request for the edited Java answer.')), 30000);
    })]).finally(() => clearTimeout(requestDeadline));
    assert.equal(requests.length, 2);
    assert.equal(requests.at(-1).source.code, draft);
    assert.equal(requests.at(-1).feedbackLanguage, 'German');
    await page.locator('.fullscreen-close-button').click();
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').click();
    await inlineHint.click();
    assert.equal(requests.length, 2, 'a new display language joins the pending hint');
    await answer(pending, 'Welcher Teil der Methode fehlt noch?');
    await page.waitForFunction(() => [...document.querySelectorAll('.scaffolding-info')].some(
      icon => icon.dataset.tooltip === '1. Welcher Teil der Methode fehlt noch?'));
    await inlineHint.click();
    await page.locator('.select-language-line > div:has(.flag-fill-german-red)').click();
    await inlineHint.click();
    assert.equal(requests.length, 2, 'switching back reuses the completed hint');
    assert.equal(await page.locator('.java-full-editor-preview pre').last().textContent(), draft);
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});

test('Java method editing and theme changes preserve source and undo history', async () => {
  const browser = await chromium.launch({chromiumSandbox: true,
    executablePath: process.env.CHROMIUM_PATH || chromium.executablePath()});
  try {
    const page = await browser.newPage({viewport: {width: 1440, height: 1000}, colorScheme: 'light'});
    page.setDefaultTimeout(60000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin || !url.pathname.startsWith(prefix)) return route.abort();
      const relative = decodeURIComponent(url.pathname.slice(prefix.length));
      if (relative.split('/').includes('..')) return route.abort();
      let file = path.resolve(root, relative + (relative.endsWith('/') ? 'index.html' : ''));
      try { await route.fulfill({body: await readFile(file), contentType: mime[path.extname(file)] || 'application/octet-stream'}); }
      catch { await route.fulfill({status: 404, body: 'Not found'}); }
    });
    await installCodeMirrorModules(page);
    await page.goto(origin + prefix + 'homepage/workbookDesign/');
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.section-block').first().click();
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').click();
    const open = page.getByRole('button', {name: /Editor.*öffnen|Open editor/i});
    await open.last().click();
    const editor = page.locator('dialog[open] .eva-editor');
    await editor.locator('.cm-content').waitFor();
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
    await page.waitForFunction(() => !!globalThis.EduSquirrelCodeMirrorLibraries?.EditorView);
    async function waitForDocumentPrefix(prefix) {
      await page.waitForFunction(prefix => {
        const node = document.querySelector('dialog[open] .cm-editor');
        const view = node && EduSquirrelCodeMirrorLibraries.EditorView.findFromDOM(node);
        return view && view.state.doc.toString().startsWith(prefix);
      }, prefix);
    }
    async function checkFirstLine(expected) {
      await page.waitForFunction(expected => {
        const gutter = document.querySelector('dialog[open] .cm-lineNumbers');
        const first = gutter && [...gutter.children].find(node => getComputedStyle(node).visibility !== 'hidden');
        return first?.textContent.trim() === String(expected);
      }, expected);
    }
    await waitForDocumentPrefix('public class Drawing {');
    const original = await page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
      .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString());
    await checkFirstLine(1);
    await editor.getByRole('button', {name: 'Method', exact: true}).click();
    await waitForDocumentPrefix('public static void main(String[] args)');
    const main = await page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
      .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString());
    assert(original.includes(main), 'the method document remains an exact source fragment');
    const mainFirstLine = original.slice(0, original.indexOf(main)).split(/\r\n|\r|\n/).length;
    await checkFirstLine(mainFirstLine);
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
    await waitForDocumentPrefix(original);
    await checkFirstLine(1);
    await page.evaluate(() => {
      const node = document.querySelector('dialog[open] .cm-editor');
      const view = EduSquirrelCodeMirrorLibraries.EditorView.findFromDOM(node);
      globalThis.appearanceProbe = {node, view, source: view.state.doc.toString()};
      view.dispatch({changes: {from: view.state.doc.length, insert: '\n// appearance check'},
        selection: {anchor: 5}});
    });
    for (const theme of ['dark', 'light']) {
      await page.evaluate(theme => document.documentElement.setAttribute('data-theme', theme), theme);
      await page.waitForFunction(theme => document.querySelector('dialog[open] .eva-editor')?.getAttribute('data-editor-theme') === theme, theme);
      await page.waitForFunction(theme => {
        const {view} = appearanceProbe;
        return view.state.facet(EduSquirrelCodeMirrorLibraries.EditorView.darkTheme) === (theme === 'dark');
      }, theme);
      const preserved = await page.evaluate(() => ({
        sameNode: appearanceProbe.node === document.querySelector('dialog[open] .cm-editor'),
        sameView: appearanceProbe.view === EduSquirrelCodeMirrorLibraries.EditorView.findFromDOM(appearanceProbe.node),
        source: appearanceProbe.view.state.doc.toString(), selection: appearanceProbe.view.state.selection.main.anchor
      }));
      assert(preserved.sameNode && preserved.sameView, 'theme does not remount CodeMirror');
      assert.equal(preserved.source, original + '\n// appearance check');
      assert.equal(preserved.selection, 5);

    }
    await editor.locator('.cm-content').click();
    await page.keyboard.press(process.platform === 'darwin' ? 'Meta+z' : 'Control+z');
    assert.equal(await page.evaluate(() => appearanceProbe.view.state.doc.toString()), original, 'undo survives theme changes');
    await editor.getByRole('button', {name: 'Hide classes', exact: true}).click();
    await editor.getByRole('combobox', {name: 'Java method', exact: true})
      .selectOption({label: 'Drawing.square(int side)'});
    await waitForDocumentPrefix('static void square(int side)');
    const square = await page.evaluate(() => {
      globalThis.methodNumberingView = EduSquirrelCodeMirrorLibraries.EditorView
        .findFromDOM(document.querySelector('dialog[open] .cm-editor'));
      return methodNumberingView.state.doc.toString();
    });
    assert(original.includes(square));
    await checkFirstLine(original.slice(0, original.indexOf(square)).split(/\r\n|\r|\n/).length);
    await editor.getByRole('combobox', {name: 'Java method', exact: true})
      .selectOption({label: 'Drawing.main(String[] args)'});
    await waitForDocumentPrefix(main);
    await checkFirstLine(mainFirstLine);
    assert(await page.evaluate(() => methodNumberingView === EduSquirrelCodeMirrorLibraries.EditorView
      .findFromDOM(document.querySelector('dialog[open] .cm-editor'))), 'method numbering reuses the mounted editor');
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
    await waitForDocumentPrefix(original);
    assert.equal(await page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
      .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString()), original,
    'compact method navigation preserves the full program');
    await page.locator('.fullscreen-close-button').click();
    await open.last().click();
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
    await waitForDocumentPrefix(original);
    assert.equal(await page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
      .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString()), original, 'source survives reopening');
    async function addMethod(name, parameters, isStatic) {
      const showClasses = editor.getByRole('button', {name: 'Show classes', exact: true});
      if (await showClasses.count()) await showClasses.click();
      await editor.locator('.java-function-editor__class-header').getByRole('button', {name: 'Add function', exact: true}).click();
      const dialog = page.getByRole('dialog', {name: 'Add function', exact: true});
      const staticOption = dialog.getByRole('checkbox', {name: 'Static method', exact: true});
      await staticOption.setChecked(isStatic);
      await dialog.getByLabel('Function name', {exact: true}).fill(name);
      await dialog.getByLabel('Parameters (for example: int count, String label)', {exact: true}).fill(parameters);
      await dialog.getByRole('button', {name: 'Add function', exact: true}).click();
      await waitForDocumentPrefix(`public ${isStatic ? 'static ' : ''}void ${name}(${parameters})`);
      return page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
        .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString());
    }
    assert.match(await addMethod('drawLine', 'int distance', true), /^public static void drawLine\(int distance\)/);
    assert.match(await addMethod('instanceMethod', '', false), /^public void instanceMethod\(\)/);
    assert.match(await addMethod('staticAgain', '', true), /^public static void staticAgain\(\)/);
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
    await waitForDocumentPrefix('public class Drawing {');
    const withMethods = await page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
      .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString());
    assert(withMethods.startsWith(original.slice(0, original.lastIndexOf('}'))), 'adding methods preserves the original class contents');
    assert.match(withMethods, /public static void drawLine\(int distance\)/);
    assert.match(withMethods, /public void instanceMethod\(\)/);
    await editor.locator('.java-function-editor__function').filter({hasText: 'instanceMethod()'}).click();
    await waitForDocumentPrefix('public void instanceMethod()');
    await page.evaluate(() => {
      const view = EduSquirrelCodeMirrorLibraries.EditorView.findFromDOM(document.querySelector('dialog[open] .cm-editor'));
      view.dispatch({changes: {from: view.state.doc.toString().indexOf('{') + 1, insert: '\n    Turtle.forward(7);'}});
    });
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
    await waitForDocumentPrefix('public class Drawing {');
    await page.waitForFunction(() => {
      const node = document.querySelector('dialog[open] .cm-editor');
      return node && EduSquirrelCodeMirrorLibraries.EditorView.findFromDOM(node).state.doc.toString().includes('Turtle.forward(7);');
    });
    const edited = await page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
      .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString());
    assert.match(edited, /public static void drawLine\(int distance\)/);
    assert.match(edited, /public static void staticAgain\(\)/);
    assert.match(edited, /public void instanceMethod\(\)\s*\{\s*Turtle\.forward\(7\);/,
      'method edits update the central full program and preserve other methods');
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});

test('language tabs follow the current draft and commit converted representations', async () => {
  const browser = await chromium.launch({chromiumSandbox: true,
    executablePath: process.env.CHROMIUM_PATH || chromium.executablePath()});
  try {
    const page = await browser.newPage({viewport: {width: 1440, height: 1000}});
    page.setDefaultTimeout(60000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    assert(process.env.CODEMIRROR_TEST_BUNDLE, 'run via npm run test:workbooks to use pinned offline modules');
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin || !url.pathname.startsWith(prefix)) return route.abort();
      const relative = decodeURIComponent(url.pathname.slice(prefix.length));
      if (relative.split('/').includes('..')) return route.abort();
      const file = path.resolve(root, relative + (relative.endsWith('/') ? 'index.html' : ''));
      try { await route.fulfill({body: await readFile(file), contentType: mime[path.extname(file)] || 'application/octet-stream'}); }
      catch { await route.fulfill({status: 404, body: 'Not found'}); }
    });
    await installCodeMirrorModules(page);
    await page.goto(origin + prefix + 'homepage/workbookDesign/');
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.section-block').first().click();
    const open = page.getByRole('button', {name: /Editor.*öffnen|Open editor/i}).first();
    await open.click();
    const editor = page.locator('dialog[open] .eva-editor');
    const java = editor.getByRole('tab', {name: 'Java', exact: true});
    const python = editor.getByRole('tab', {name: 'Python', exact: true});
    const snap = editor.getByRole('tab', {name: 'Snap!', exact: true});
    await java.click();
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
    await editor.locator('.cm-content').waitFor();
    const readSource = () => page.evaluate(() => {
      const current = document.querySelector('dialog[open] .cm-editor');
      if (current) return EduSquirrelCodeMirrorLibraries.EditorView.findFromDOM(current).state.doc.toString();
      const fallback = document.querySelector('dialog[open] .code-mirror-editor__fallback:not([hidden])');
      return fallback?.value;
    });
    async function writeSource(code) {
      await page.evaluate(code => {
        const current = document.querySelector('dialog[open] .cm-editor');
        if (current) {
          const view = EduSquirrelCodeMirrorLibraries.EditorView.findFromDOM(current);
          view.dispatch({changes: {from: 0, to: view.state.doc.length, insert: code}});
        } else {
          const fallback = document.querySelector('dialog[open] .code-mirror-editor__fallback:not([hidden])');
          if (!fallback) throw new Error('The current draft must remain editable.');
          fallback.value = code;
          fallback.dispatchEvent(new Event('input', {bubbles: true}));
        }
      }, code);
      assert.equal(await readSource(), code);
    }
    async function checkTabs(convertible) {
      await python.focus();
      await snap.focus();
      await page.waitForFunction(convertible => {
        const tabs = [...document.querySelectorAll('dialog[open] .eva-editor__tabs button')];
        return ['Python', 'Snap!'].every(label => {
          const tab = tabs.find(tab => tab.getAttribute('aria-label') === label);
          return tab?.getAttribute('aria-disabled') === String(!convertible);
        });
      }, convertible);
      assert.equal(await java.isDisabled(), false);
      assert.equal(await python.isDisabled(), !convertible);
      assert.equal(await snap.isDisabled(), !convertible);
      assert.equal(await java.getAttribute('aria-selected'), 'true');
    }
    const valid = 'public class Drawing {\n  public static void main(String[] args) {\n    Turtle.forward(12);\n  }\n}\n';
    await writeSource(valid);
    await checkTabs(true);
    const spaced = valid + '\n\t  ';
    await writeSource(spaced);
    await checkTabs(true);
    const unfinished = spaced.slice(0, spaced.lastIndexOf('}')) + '\n\t  ';
    await writeSource(unfinished);
    await checkTabs(false);
    await page.locator('.fullscreen-close-button').click();
    await open.click();
    assert.equal(await readSource(), unfinished, 'an unfinished class survives closing and reopening');
    await checkTabs(false);
    await writeSource(spaced);
    await checkTabs(true);
    const unsupported = spaced.replace('public static void main', 'public void main');
    await writeSource(unsupported);
    await checkTabs(false);
    await writeSource(spaced);
    await checkTabs(true);
    await page.evaluate(({valid, unfinished}) => {
      const view = EduSquirrelCodeMirrorLibraries.EditorView.findFromDOM(document.querySelector('dialog[open] .cm-editor'));
      for (const source of [unfinished, valid, unfinished, valid])
        view.dispatch({changes: {from: 0, to: view.state.doc.length, insert: source}});
    }, {valid: spaced, unfinished});
    await checkTabs(true);
    assert.equal(await readSource(), spaced, 'rapid invalid and valid drafts retain the latest exact source');
    await java.focus();
    await page.keyboard.press('ArrowLeft');
    assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('aria-label')), 'Python');
    assert.equal(await java.getAttribute('aria-selected'), 'true');
    await python.click();
    await page.waitForFunction(() => document.querySelector('dialog[open] [role="tab"][aria-label="Python"]')?.getAttribute('aria-selected') === 'true');
    assert.equal(await python.getAttribute('role'), 'tab');
    assert.equal(await python.getAttribute('aria-pressed'), null);
    assert.equal(await editor.locator('.eva-editor__tabs').getAttribute('role'), 'tablist');
    assert.equal(await python.getAttribute('aria-selected'), 'true');
    await java.click();
    await page.waitForFunction(() => document.querySelector('dialog[open] button[aria-label="Java"]')?.getAttribute('aria-selected') === 'true');
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
    assert.match(await readSource(), /Turtle\.forward\(12\);/, 'returning to Java uses the converted program in the central answer');
    assert.match(await readSource(), /class Drawing/);
    await checkTabs(true);
    await page.locator('.fullscreen-close-button').click();
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});
