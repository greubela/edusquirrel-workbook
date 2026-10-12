import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html', '.js':'text/javascript', '.mjs':'text/javascript', '.css':'text/css', '.json':'application/json', '.svg':'image/svg+xml', '.png':'image/png'};

test('workbook EVA2 construction saves, restores, resizes, resets and uses CSS on mobile', async () => {
  await fs.access(path.join(root, 'artifacts/newest/client.js'));
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium', args:['--no-sandbox']});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:900}});
    page.setDefaultTimeout(60000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      const file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
      if (!file.startsWith(root + path.sep)) return route.abort();
      try { await route.fulfill({body:await fs.readFile(file), contentType:mime[path.extname(file)] || 'application/octet-stream'}); }
      catch { await route.fulfill({status:404,body:'Not found'}); }
    });
    await page.goto(origin + '/homepage/workbookDesign/index.html');
    const login = page.locator('.section-register .login-area').nth(1).locator('button');
    await login.click();
    const chapter = page.locator('.section-block').filter({hasText:'Evacuation – construct a floor'});
    await chapter.click();
    const preview = page.locator('.evacuation-preview');
    await preview.waitFor();
    assert.match(await preview.innerText(), /8 × 6 cells · 0 people · 0 exits/);
    const open = page.getByRole('button', {name:/Open editor/i});
    await open.click();
    const editor = page.locator('dialog[open] .evacuation-editor');
    await editor.waitFor();
    const tool = name => editor.getByRole('button', {name,exact:true});
    const cells = editor.locator('.evacuation-cell');
    assert.equal(await cells.count(), 48);
    await tool('Person').click();
    await cells.nth(9).focus();
    await page.keyboard.press('Enter');
    assert.equal(await editor.locator('.evacuation-cell--person').count(),1);
    assert(await cells.nth(9).evaluate(el => el === document.activeElement), 'painting retains keyboard focus');
    await cells.nth(9).click();
    assert.equal(await editor.locator('.evacuation-cell--person').count(),0);
    await cells.nth(9).click();
    await tool('Wall').click();
    await cells.nth(10).click();
    await tool('Exit').click();
    await cells.first().click();
    await tool('Add bottom row').click();
    await tool('Add right column').click();
    assert.equal(await cells.count(),63);
    assert.equal(await editor.locator('.evacuation-cell--person').count(),1);
    await tool('Remove bottom row').click();
    await tool('Remove right column').click();
    assert.equal(await cells.count(),48);
    assert.match(await cells.nth(9).getAttribute('aria-label'), /Person/);
    assert.match(await cells.nth(10).getAttribute('class'), /--wall/);
    const colors = await editor.evaluate(el => ['floor','wall','exit'].map(kind =>
      getComputedStyle(el.querySelector(`.evacuation-cell--${kind}`)).backgroundColor));
    assert.equal(new Set(colors).size,3,'CSS distinguishes walkable floor, walls and exits');
    assert.equal(await editor.locator('[style], style').count(),0,'no inline editor styling');
    await page.locator('.fullscreen-close-button').click();
    assert.match(await preview.innerText(), /1 people · 1 exits/);
    assert.match(await preview.innerText(), /Construction criteria met/);
    await open.click();
    assert.equal(await editor.locator('.evacuation-cell--person').count(),1);
    await page.locator('.fullscreen-close-button').click();
    await page.waitForFunction(async () => {
      const db = await new Promise((resolve,reject) => {
        const request = indexedDB.open('EvaDidInteractionDB');
        request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error);
      });
      const rows = await new Promise((resolve,reject) => {
        const request = db.transaction('variableHistoryStore').objectStore('variableHistoryStore').getAll();
        request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error);
      });
      db.close();
      return rows.some(row => JSON.stringify(row).includes('evacuation-construct-floor'));
    });
    await page.reload();
    if (await login.isVisible()) await login.click();
    await chapter.click();
    await preview.waitFor();
    assert.match(await preview.innerText(), /1 people · 1 exits/);
    await open.click();
    assert.equal(await editor.locator('.evacuation-cell--person').count(),1);
    await page.setViewportSize({width:390,height:844});
    const geometry = await editor.evaluate(el => {
      const box = el.getBoundingClientRect();
      const viewport = el.querySelector('.evacuation-viewport');
      return {left:box.left,right:box.right,width:innerWidth,scrollWidth:viewport.scrollWidth,
        clientWidth:viewport.clientWidth,cellWidth:el.querySelector('.evacuation-cell').getBoundingClientRect().width};
    });
    assert(geometry.left >= 0 && geometry.right <= geometry.width + 1, JSON.stringify(geometry));
    assert(geometry.cellWidth >= 44,'grid cells retain accessible target size on mobile');
    assert(geometry.scrollWidth >= geometry.clientWidth,'wide floors scroll inside the editor');
    await tool('Reset floor').click();
    assert.equal(await editor.locator('.evacuation-cell--person').count(),0);
    assert.equal(await editor.locator('.evacuation-cell--exit').count(),0);
    await page.locator('.fullscreen-close-button').click();
    assert.match(await preview.innerText(), /0 people · 0 exits/);
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});
