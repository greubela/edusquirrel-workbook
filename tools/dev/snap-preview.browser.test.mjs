import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

// A fast cached client can start while Snap's sequential script loader is still fetching.
// Exercise the actual Scala.js previews and retained fullscreen editor in that order.
test('Snap previews and editor wait for the runtime and ignore closed mounts', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(), chromiumSandbox:true});
  try {
    const page = await browser.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    let release;
    const gate = new Promise(resolve => {release = resolve;});
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      if (url.pathname.endsWith('/adjusted/adjustedMorphic.js')) await gate;
      let file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
      if (!file.startsWith(root + path.sep)) return route.abort();
      if (url.pathname === '/homepage/js/CodeMirrorLoader.js') file = process.env.CODEMIRROR_TEST_BUNDLE;
      try {await route.fulfill({body:await fs.readFile(file),contentType:mime[path.extname(file)] || 'application/octet-stream'});}
      catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await page.goto(origin + '/homepage/workbookDesign/index.html', {waitUntil:'domcontentloaded'});
    await page.locator('.section-register .login-area').nth(1).locator('button').click({timeout:60000});
    await page.locator('.section-block').first().click();
    const previews = page.locator('.be-program-snap-renderer__canvas:not(dialog *)');
    await previews.first().waitFor();
    assert.equal(await page.evaluate(() => typeof window.WorldMorph), 'undefined', 'runtime is deliberately incomplete');
    const open = page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first();
    await open.click();
    await page.locator('dialog[open] .be-program-snap-renderer__canvas').waitFor();
    await page.locator('.fullscreen-close-button').click();
    assert.deepEqual(errors, [], 'pending runtime must not throw from preview/editor mount');
    release();
    // Pixel coverage, rather than DOM presence, proves that scripts were actually drawn.
    const hasBlocks = canvas => {
      if (canvas.width <= 1 || canvas.height <= 1) return false;
      const data = canvas.getContext('2d').getImageData(0,0,canvas.width,canvas.height).data;
      const colors = new Set();
      for (let i=0;i<data.length;i+=4) {
        if (data[i+3]) colors.add(`${data[i]},${data[i+1]},${data[i+2]}`);
        if (colors.size > 8) return true;
      }
      return false;
    };
    await page.waitForFunction(hasBlocks, await previews.first().elementHandle());
    assert.equal(await page.locator('dialog[open]').count(), 0, 'late runtime does not reopen the editor');
    assert.equal(await previews.evaluateAll(canvases => canvases.filter(c => c.hasAttribute('style')).length), 0, 'preview presentation uses CSS');
    await open.click();
    const editor = page.locator('dialog[open] .be-program-snap-renderer__canvas');
    await page.waitForFunction(hasBlocks, await editor.elementHandle());
    await page.locator('.fullscreen-close-button').click();
    await open.click();
    await page.waitForFunction(hasBlocks, await editor.elementHandle());
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});
