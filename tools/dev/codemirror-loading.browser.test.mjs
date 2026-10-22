import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

async function fixture(browser, fail = false, openEditor = true, forceTimer = false) {
  const page = await browser.newPage();
  if (forceTimer) await page.addInitScript(() => {
    Object.defineProperty(window, 'requestIdleCallback', {value:undefined, configurable:true});
  });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  let requests = 0;
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.origin !== origin) return route.abort();
    let file = path.resolve(root, '.' + decodeURIComponent(url.pathname) + (url.pathname.endsWith('/') ? 'index.html' : ''));
    if (!file.startsWith(root + path.sep)) return route.abort();
    try {
      let body = await fs.readFile(file);
      await route.fulfill({body, contentType: mime[path.extname(file)] || 'application/octet-stream'});
    } catch { await route.fulfill({status:404,body:'Not found'}); }
  });
  await installCodeMirrorModules(page, {gate, fail, onRequest: () => requests++});
  await page.goto(origin + '/homepage/workbookDesign/index.html', {waitUntil:'domcontentloaded'});
  await page.locator('.section-register .login-area').nth(1).locator('button').click();
  await page.locator('.section-block').first().click();
  await page.waitForFunction(() => performance.getEntriesByType('resource').some(entry => entry.name.includes('esm.sh/')));
  assert.equal(requests, 1, 'startup preheats without opening an editor');
  assert.equal(await page.locator('dialog[open]').count(), 0);
  if (openEditor) await page.getByRole('button', {name:/Editor.*öffnen|Open editor/i}).first().click();
  return {page, release, requests: () => requests, errors};
}

test('slow CodeMirror loading leaves the app usable, shares one load and ignores unmounted editors', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true});
  try {
    const {page, release, requests, errors} = await fixture(browser);
    assert.equal(requests(), 1, 'the pending background load is shared with editors');
    const dialog = page.locator('dialog[open]');
    await dialog.getByRole('button', {name:'Python',exact:true}).click();
    const fallback = dialog.locator('.code-mirror-editor__fallback');
    await fallback.fill('forward(77)\n');
    await page.waitForFunction(() => performance.getEntriesByType('resource').some(entry => entry.name.includes('esm.sh/')));
    await dialog.getByRole('button', {name:'Snap!',exact:true}).click();
    release();
    await page.waitForFunction(() => !!window.EduSquirrelCodeMirror);
    await page.waitForTimeout(300);
    assert.equal(await page.locator('.cm-editor').count(), 0, 'resolved load cannot mount a stale Python tab');
    await dialog.getByRole('button', {name:'Python',exact:true}).click();
    await dialog.locator('.cm-editor').waitFor();
    assert.match(await dialog.locator('.cm-content').innerText(), /forward\(77\)/, 'text entered while loading survives enhancement');
    assert.equal(await dialog.locator('.cm-flowchart-gutter').count(), 1, 'Eva enables the configured Python gutter');
    await dialog.locator('.cm-flowchart-row image').first().waitFor();
    const overlay = await dialog.locator('.cm-flowchart-row image').first().evaluate(image => {
      const uri = image.getAttribute('href');
      const svg = new DOMParser().parseFromString(decodeURIComponent(uri.slice(uri.indexOf(',') + 1)), 'image/svg+xml');
      return {paths:svg.querySelectorAll('path').length, text:svg.querySelectorAll('text').length,
        errors:svg.querySelectorAll('parsererror').length};
    });
    assert(overlay.paths > 0, 'the nested block renderer supplies real flow paths');
    assert.equal(overlay.text, 0, 'only CodeMirror supplies source content');
    assert.equal(overlay.errors, 0, 'the flow image is valid SVG');
    assert.match(await dialog.locator('.cm-content').innerText(), /forward\(77\)/);
    const source = 'while True:\n    if True:\n        print(1)\n    else:\n        print(2)\nprint(3)';
    await dialog.locator('.cm-content').click();
    await page.keyboard.press('Control+Home');
    await page.keyboard.press('Control+a');
    await page.keyboard.insertText(source);
    await dialog.locator('.cm-flowchart-row[title^="decision:"]').waitFor();
    assert.equal(await dialog.locator('.cm-line').count(), source.split('\n').length,
      'synthetic branch and closing rows do not add content lines');
    assert.equal(await dialog.locator('.cm-content').innerText(), source);
    await dialog.getByRole('button', {name:'Use dark appearance'}).click();
    await dialog.getByRole('button', {name:'Use light appearance'}).waitFor();
    assert.notEqual(await dialog.locator('.cm-flowchart-row > svg').first().evaluate(svg => getComputedStyle(svg).filter),
      'none', 'the nested renderer palette adapts to the dark editor');
    await dialog.getByRole('button', {name:'Java',exact:true}).click();
    await dialog.locator('.cm-editor').waitFor();
    assert.equal(await dialog.locator('.cm-flowchart-gutter').count(), 0, 'Python gutter does not appear for Java');
    assert.equal(requests(), 1, 'all tabs reuse the same module load');
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});

test('a failed CodeMirror request leaves the fallback editable and other tabs usable', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true});
  try {
    const {page, release, errors} = await fixture(browser, true);
    const dialog = page.locator('dialog[open]');
    await dialog.getByRole('button', {name:'Python',exact:true}).click();
    release();
    const fallback = dialog.locator('.code-mirror-editor__fallback');
    await fallback.fill('forward(88)\n');
    await dialog.getByRole('button', {name:'Snap!',exact:true}).click();
    await dialog.getByRole('button', {name:'Python',exact:true}).click();
    assert.match(await fallback.inputValue(), /forward\(88\)/);
    assert.equal(await dialog.locator('.cm-editor').count(), 0);
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});


test('startup warms CodeMirror without idle callbacks or editor creation, and the first editor reuses it', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true});
  try {
    const {page, release, requests, errors} = await fixture(browser, false, false, true);
    release();
    await page.waitForFunction(() => !!window.EduSquirrelCodeMirror);
    assert.equal(await page.locator('.cm-editor').count(), 0);
    await page.getByRole('button', {name:/Editor.*öffnen|Open editor/i}).first().click();
    await page.locator('dialog[open]').getByRole('button', {name:'Python',exact:true}).click();
    await page.locator('dialog[open] .cm-editor').waitFor();
    assert.equal(requests(), 1, 'opening the first text editor does not request another script');
    assert.equal(await page.locator('.cm-editor').count(), 1);
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});
