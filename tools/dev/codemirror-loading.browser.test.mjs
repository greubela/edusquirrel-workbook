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
    if (url.pathname === '/homepage/js/CodeMirrorLoader.js') {
      requests++;
      await gate;
      if (fail) return route.fulfill({status: 503, body: 'Unavailable'});
      file = process.env.CODEMIRROR_TEST_BUNDLE;
      assert(file, 'run via npm run test:workbooks to use pinned offline modules');
    }
    try {
      let body = await fs.readFile(file);
      if (url.pathname === '/homepage/js/CodeMirrorLoader.js') body = Buffer.from(body.toString() + `
        window.editorCreations = 0;
        const create = window.EduSquirrelCodeMirror.createEditor;
        window.EduSquirrelCodeMirror.createEditor = config => {
          window.editorCreations++;
          return create(config);
        };`);
      await route.fulfill({body, contentType: mime[path.extname(file)] || 'application/octet-stream'});
    } catch { await route.fulfill({status:404,body:'Not found'}); }
  });
  await page.goto(origin + '/homepage/workbookDesign/index.html', {waitUntil:'domcontentloaded'});
  await page.locator('.section-register .login-area').nth(1).locator('button').click();
  await page.locator('.section-block').first().click();
  await page.waitForFunction(() => document.querySelector('script[src$="CodeMirrorLoader.js"]'));
  assert.equal(requests, 1, 'startup preheats without opening an editor');
  assert.equal(await page.locator('script[src$="CodeMirrorLoader.js"]').getAttribute('fetchpriority'), 'low');
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
    await page.waitForFunction(() => document.querySelector('script[src$="CodeMirrorLoader.js"]'));
    await dialog.getByRole('button', {name:'Snap!',exact:true}).click();
    release();
    await page.waitForFunction(() => !!window.EduSquirrelCodeMirror);
    await page.waitForTimeout(300);
    assert.equal(await page.evaluate(() => window.editorCreations), 0, 'resolved load cannot mount a stale Python tab');
    await dialog.getByRole('button', {name:'Python',exact:true}).click();
    await dialog.locator('.cm-editor').waitFor();
    assert.match(await dialog.locator('.cm-content').innerText(), /forward\(77\)/, 'text entered while loading survives enhancement');
    assert.equal(await dialog.locator('.cm-flowchart-gutter').count(), 1, 'Eva enables the configured Python gutter');
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
    assert.equal(await page.evaluate(() => window.editorCreations), 0);
    assert.equal(await page.locator('.cm-editor').count(), 0);
    await page.getByRole('button', {name:/Editor.*öffnen|Open editor/i}).first().click();
    await page.locator('dialog[open]').getByRole('button', {name:'Python',exact:true}).click();
    await page.locator('dialog[open] .cm-editor').waitFor();
    assert.equal(requests(), 1, 'opening the first text editor does not request another script');
    assert.equal(await page.evaluate(() => window.editorCreations), 1);
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});
