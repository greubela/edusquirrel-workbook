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

test('Java appearance changes preserve the live editor, undo history and drawing', async () => {
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
    const open = page.getByRole('button', {name: /Editor.*öffnen|Open editor/i});
    await open.last().click();
    const editor = page.locator('dialog[open] .eva-editor');
    await editor.locator('.cm-content').waitFor();
    assert.equal(await editor.locator('.editor-appearance__toggle').count(), 1);
    assert.equal(await editor.locator('.eva-editor__tabs > button').count(), 1, 'configured language list stays Java-only');
    const javaTab = editor.getByRole('button', {name: 'Java', exact: true});
    assert.equal(await javaTab.locator('.eva-editor__language-icon').count(), 1);
    assert.equal((await javaTab.textContent()).trim(), '', 'Java uses its icon rather than visible tab text');
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
    await page.waitForFunction(() => !!globalThis.EduSquirrelCodeMirrorLibraries?.EditorView);
    await editor.locator('.cm-content').waitFor();
    const original = await page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
      .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString());
    await page.evaluate(() => {
      const node = document.querySelector('dialog[open] .cm-editor');
      const view = EduSquirrelCodeMirrorLibraries.EditorView.findFromDOM(node);
      globalThis.appearanceProbe = {node, view, source: view.state.doc.toString()};
      view.dispatch({changes: {from: view.state.doc.length, insert: '\n// appearance check'},
        selection: {anchor: 5}});
    });
    const drawings = () => page.locator('.turtle-gradig-panel').evaluateAll(panels => panels.map(panel => ({
      id: panel.id, background: getComputedStyle(panel).backgroundColor,
      lines: [...panel.querySelectorAll('.turtle-line')].map(line => ({
        class: line.getAttribute('class'), stroke: line.getAttribute('stroke'),
        path: line.getAttribute('d'), x1: line.getAttribute('x1'), y1: line.getAttribute('y1'),
        x2: line.getAttribute('x2'), y2: line.getAttribute('y2')
      }))
    })));
    const before = await drawings();
    for (const theme of ['dark', 'light', 'dark', 'light']) {
      await editor.locator('.editor-appearance__toggle').click();
      await page.waitForFunction(theme => document.querySelector('dialog[open] .eva-editor')?.getAttribute('data-editor-theme') === theme, theme);
      await page.waitForFunction(theme => {
        const {view} = appearanceProbe;
        return view.state.facet(EduSquirrelCodeMirrorLibraries.EditorView.darkTheme) === (theme === 'dark');
      }, theme);
      const preserved = await page.evaluate(() => ({
        sameNode: appearanceProbe.node === document.querySelector('dialog[open] .cm-editor'),
        sameView: appearanceProbe.view === EduSquirrelCodeMirrorLibraries.EditorView.findFromDOM(appearanceProbe.node),
        styled: appearanceProbe.node.classList.contains('edusquirrel-code-mirror'),
        source: appearanceProbe.view.state.doc.toString(), selection: appearanceProbe.view.state.selection.main.anchor
      }));
      assert(preserved.sameNode && preserved.sameView, 'theme does not remount CodeMirror');
      assert(preserved.styled, 'CodeMirror retains its syntax and diagnostic styling hook');
      assert.equal(preserved.source, original + '\n// appearance check');
      assert.equal(preserved.selection, 5);
      const comment = editor.locator('.cm-comment').filter({hasText: '// appearance check'});
      await comment.waitFor();
      assert.equal(await comment.evaluate(node => getComputedStyle(node).color),
        await editor.evaluate(node => {
          const probe = document.createElement('span');
          probe.style.color = 'var(--editor-code-comment)'; node.append(probe);
          const color = getComputedStyle(probe).color; probe.remove(); return color;
        }), 'real syntax comments use the readable editor palette');
      assert.deepEqual(await drawings(), before, 'theme does not alter existing drawing boards or line geometry');
    }
    await editor.locator('.cm-content').click();
    await page.keyboard.press(process.platform === 'darwin' ? 'Meta+z' : 'Control+z');
    assert.equal(await page.evaluate(() => appearanceProbe.view.state.doc.toString()), original, 'undo survives theme changes');
    await editor.getByRole('button', {name: 'Hide classes', exact: true}).click();
    assert.equal(await editor.locator('.java-function-editor__diagram-section').isVisible(), false);
    assert.equal(await editor.getByRole('combobox', {name: 'Java method', exact: true})
      .locator('option:checked').innerText(), 'Drawing.main(String[] args)');
    await editor.getByRole('combobox', {name: 'Java method', exact: true})
      .selectOption({label: 'Drawing.square(int side)'});
    assert.equal(await editor.locator('.java-function-editor__workspace-heading h3').innerText(), 'Drawing.square');
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
    await editor.locator('.cm-content').waitFor();
    assert.equal(await page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
      .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString()), original,
    'compact method navigation preserves the full program');
    await editor.getByRole('button', {name: 'Show classes', exact: true}).click();
    assert.equal(await editor.locator('.java-function-editor__diagram-section').isVisible(), true);
    for (const viewport of [{width: 1366, height: 768}, {width: 1440, height: 480},
      {width: 1024, height: 768}, {width: 760, height: 900}, {width: 390, height: 844},
      {width: 320, height: 740}, {width: 844, height: 390}]) {
      await page.setViewportSize(viewport);
      const {width} = viewport;
      assert(await editor.evaluate(node => node.scrollWidth <= node.clientWidth + 1), `editor fits ${width}px`);
      const geometry = await editor.evaluate(node => {
        const shell = node.parentElement;
        const elements = [shell, node, node.querySelector('.eva-editor__content'), node.querySelector('.java-function-editor__workspace')];
        const radius = element => getComputedStyle(element).borderTopLeftRadius;
        return {radii: [radius(shell), radius(node)], owners: elements.map(element => ({
          name: element.className, height: element.clientHeight, scroll: element.scrollHeight
        })), codeHeight: node.querySelector('.cm-scroller').clientHeight};
      });
      assert.equal(geometry.radii[0], geometry.radii[1], 'editor and fullscreen shell have the same contour');
      for (const owner of geometry.owners) assert(owner.scroll <= owner.height + 1,
        `no outer scrolling at ${width}x${viewport.height}: ${JSON.stringify(owner)}`);
      assert(geometry.codeHeight >= (viewport.height >= 700 ? 80 : 40), `code remains usable at ${width}x${viewport.height}`);
      await editor.locator('.editor-appearance__toggle').click();
    }
    await page.setViewportSize({width: 1440, height: 1000});
    await page.locator('.fullscreen-close-button').click();
    await open.last().click();
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
    await editor.locator('.cm-content').waitFor();
    assert.equal(await page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
      .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString()), original, 'source survives reopening');
    async function addMethod(name, parameters, isStatic) {
      const showClasses = editor.getByRole('button', {name: 'Show classes', exact: true});
      if (await showClasses.count()) await showClasses.click();
      await editor.locator('.java-function-editor__class-header').getByRole('button', {name: 'Add function', exact: true}).click();
      const dialog = page.getByRole('dialog', {name: 'Add function', exact: true});
      const staticOption = dialog.getByRole('checkbox', {name: 'Static method', exact: true});
      assert(await staticOption.isChecked(), 'each new method starts static');
      await staticOption.setChecked(isStatic);
      await dialog.getByLabel('Function name', {exact: true}).fill(name);
      await dialog.getByLabel('Parameters (for example: int count, String label)', {exact: true}).fill(parameters);
      await dialog.getByRole('button', {name: 'Add function', exact: true}).click();
      await editor.locator('.cm-content').waitFor();
      return page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
        .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString());
    }
    assert.match(await addMethod('drawLine', 'int distance', true), /^public static void drawLine\(int distance\)/);
    assert.match(await addMethod('instanceMethod', '', false), /^public void instanceMethod\(\)/);
    assert.match(await addMethod('staticAgain', '', true), /^public static void staticAgain\(\)/);
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
    await editor.locator('.cm-content').waitFor();
    const withMethods = await page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
      .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString());
    assert(withMethods.startsWith(original.slice(0, original.lastIndexOf('}'))), 'adding methods preserves the original class contents');
    assert.match(withMethods, /public static void drawLine\(int distance\)/);
    assert.match(withMethods, /public void instanceMethod\(\)/);
    await editor.locator('.java-function-editor__function').filter({hasText: 'instanceMethod()'}).click();
    await editor.locator('.cm-content').waitFor();
    await page.evaluate(() => {
      const view = EduSquirrelCodeMirrorLibraries.EditorView.findFromDOM(document.querySelector('dialog[open] .cm-editor'));
      view.dispatch({changes: {from: view.state.doc.toString().indexOf('{') + 1, insert: '\n    Turtle.forward(7);'}});
    });
    await editor.getByRole('button', {name: 'Full source', exact: true}).click();
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
    const java = editor.getByRole('button', {name: 'Java', exact: true});
    const python = editor.getByRole('button', {name: 'Python', exact: true});
    const snap = editor.getByRole('button', {name: 'Snap!', exact: true});
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
      await page.waitForFunction(convertible => {
        const tabs = [...document.querySelectorAll('dialog[open] .eva-editor__tabs button')];
        return ['Python', 'Snap!'].every(label => tabs.find(tab => tab.getAttribute('aria-label') === label)?.disabled === !convertible);
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
    await python.click();
    await page.waitForFunction(() => document.querySelector('dialog[open] button[aria-label="Python"]')?.getAttribute('aria-selected') === 'true');
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
