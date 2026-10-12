import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html', '.js':'text/javascript', '.mjs':'text/javascript', '.css':'text/css', '.json':'application/json', '.svg':'image/svg+xml', '.png':'image/png'};

test('source activities keep simulations independent and persist locker edits, budget choices and reflections', async () => {
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
    await page.goto(origin + '/homepage/evacuationWorkbook/index.html');
    const login = page.locator('.section-register .login-area').nth(1).locator('button');
    await login.click();
    const chapter = title => page.locator('.section-block').filter({hasText:title});
    await chapter('Remove lockers or close the gaps?').click();
    const preview = page.locator('.evacuation-simulation-preview');
    await preview.waitFor();
    assert.match(await preview.innerText(), /6 people · 2 exits · 0 recorded runs/);
    await page.locator('textarea').first().fill('Remove the two lockers nearest the door.');
    await page.getByRole('button', {name:/Open editor/i}).click();
    const editor = page.locator('dialog[open] .evacuation-experiment');
    await editor.waitFor();
    await editor.getByRole('button',{name:'Floor',exact:true}).click();
    await editor.locator('.evacuation-cell').nth(3 * 11 + 6).click();
    await editor.locator('.evacuation-cell').nth(5 * 11 + 6).click();
    assert.equal(await editor.locator('.evacuation-cell--wall').count(),36);
    await page.locator('.fullscreen-close-button').click();
    await chapter('How does door width affect evacuation time?').click();
    await preview.waitFor();
    await page.waitForFunction(() => document.querySelector('.evacuation-simulation-preview')?.textContent.includes('6 people · 1 exits · 0 recorded runs'));
    assert.match(await preview.innerText(), /6 people · 1 exits · 0 recorded runs/,'door experiment has independent state');
    await page.getByRole('button', {name:/Open editor/i}).click();
    await editor.waitFor();
    await page.waitForFunction(() => document.querySelectorAll('dialog[open] .evacuation-cell--wall').length === 39);
    assert.equal(await editor.locator('.evacuation-cell--wall').count(),39,'door example retains all four lockers');
    await editor.getByRole('button',{name:'Exit',exact:true}).click();
    await editor.locator('.evacuation-cell').nth(5 * 11 + 10).click();
    assert.equal(await editor.locator('.evacuation-cell--exit').count(),2);
    await page.locator('.fullscreen-close-button').click();
    await chapter('Your €20,000 master plan').click();
    const training = page.getByRole('checkbox',{name:/Walking\/running training/});
    const obstacles = page.getByRole('checkbox',{name:/Remove obstacles/});
    await training.check();
    await obstacles.check();
    await page.locator('textarea').fill('Total €12,000; €8,000 left. Compare assumptions with measured evidence.');
    await chapter('Question the simulation').click();
    await preview.waitFor();
    assert.match(await preview.innerText(), /1 people · 1 exits · 0 recorded runs/);
    await chapter('Your €20,000 master plan').click();
    await training.waitFor();
    assert(await training.isChecked());
    assert(await obstacles.isChecked());
    assert.match(await page.locator('textarea').inputValue(), /Total €12,000/);
    await page.waitForFunction(async () => {
      const db = await new Promise((resolve,reject) => {const r=indexedDB.open('EvaDidInteractionDB');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
      const rows = await new Promise((resolve,reject) => {const r=db.transaction('variableHistoryStore').objectStore('variableHistoryStore').getAll();r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
      db.close(); return rows.some(row => JSON.stringify(row).includes('Total €12,000'));
    });
    await page.reload();
    if (await login.isVisible()) await login.click();
    await chapter('Your €20,000 master plan').click();
    await training.waitFor();
    assert(await training.isChecked());
    assert(await obstacles.isChecked());
    assert.match(await page.locator('textarea').inputValue(), /Total €12,000/);
    await chapter('Remove lockers or close the gaps?').click();
    await page.waitForFunction(() => document.querySelector('textarea')?.value.includes('Remove the two lockers'));
    await page.getByRole('button', {name:/Open editor/i}).click();
    await editor.waitFor();
    assert.equal(await editor.locator('.evacuation-cell--wall').count(),36,'edited lockers survive reload');
    assert.deepEqual(errors,[]);
  } finally { await browser.close(); }
});
