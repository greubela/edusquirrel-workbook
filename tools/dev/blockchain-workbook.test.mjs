import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html', '.js':'text/javascript', '.css':'text/css', '.json':'application/json', '.svg':'image/svg+xml', '.png':'image/png'};
test('Blockchain workbook saves research, computes exact Hashq and grades collisions/preimages on mobile', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium', args:['--no-sandbox']});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:900}});
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      const file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
      if (!file.startsWith(root + path.sep)) return route.abort();
      try { await route.fulfill({body:await fs.readFile(file), contentType:mime[path.extname(file)] || 'application/octet-stream'}); }
      catch { await route.fulfill({status:404,body:'Not found'}); }
    });
    await page.goto(origin + '/homepage/blockchainWorkbook/index.html');
    await page.locator('.section-register .login-area').nth(1).locator('button').click({timeout:60000});
    const research = page.locator('.answer-table');
    await research.locator('input').first().waitFor();
    assert.equal(await research.locator('input').count(), 12);
    for (let i=0; i<12; i++) await research.locator('input').nth(i).fill('Research ' + i);
    assert.match(await research.innerText(), /no automatic grade/);
    await page.locator('.section-block').nth(1).click();
    const calculators = page.locator('.square-middle-hash');
    await calculators.first().waitFor();
    assert.equal(await calculators.count(), 4);
    const explore = calculators.nth(0);
    assert.equal(await explore.locator('mark').first().innerText(), '24');
    await explore.locator('input').first().fill('45');
    assert.equal(await explore.locator('mark').first().innerText(), '02');
    await explore.locator('input').first().fill('3');
    assert.match(await explore.locator('.hash-feedback').innerText(), /greater than 3/);
    await explore.locator('input').first().fill('9'.repeat(100));
    assert.equal((await explore.locator('tbody td').nth(1).innerText()).length, 200, '100-digit input is squared exactly');
    for (const viewport of [{width:390,height:844}, {width:844,height:390}, {width:1440,height:900}]) {
      await page.setViewportSize(viewport);
      const geometry = await explore.evaluate(el => {
        const box=el.getBoundingClientRect();
        return {left:box.left,right:box.right,width:innerWidth,inlineStyles:el.querySelectorAll('[style], style').length,
          overflow:getComputedStyle(el.querySelector('.hash-results')).overflowX,
          border:getComputedStyle(el.querySelector('input')).borderTopWidth};
      });
      assert(geometry.left>=0 && geometry.right<=geometry.width+1);
      assert.equal(geometry.inlineStyles, 0);
      assert.equal(geometry.overflow, 'auto');
      assert.equal(geometry.border, '1px');
    }
    await explore.getByRole('button', {name:'Reset inputs'}).click();
    assert.equal(await explore.locator('input').first().inputValue(), '57');
    await explore.locator('input').first().fill('45');
    const hashTable=page.locator('.answer-table');
    for (const [i,value] of ['1764','76','9801','80','169','16'].entries()) await hashTable.locator('input').nth(i).fill(value);
    assert.match(await hashTable.innerText(), /6 of 6 checked cells/);
    const collision=calculators.nth(1);
    await collision.locator('input').nth(0).fill('35');
    await collision.locator('input').nth(1).fill('65');
    assert.equal(await collision.locator('.hash-feedback').innerText(), 'Correct.');
    await collision.locator('input').nth(1).fill('035');
    assert.match(await collision.locator('.hash-feedback').innerText(), /do not yet meet/);
    await collision.locator('input').nth(1).fill('65');
    const target22=calculators.nth(2);
    await target22.locator('input').fill('45');
    assert.match(await target22.locator('.hash-feedback').innerText(), /do not yet meet/);
    await target22.locator('input').fill('35');
    assert.equal(await target22.locator('.hash-feedback').innerText(), 'Correct.');
    await calculators.nth(3).locator('input').fill('76');
    assert.equal(await calculators.nth(3).locator('.hash-feedback').innerText(), 'Correct.');
    await page.reload();
    await page.locator('.section-block').nth(1).click({timeout:60000});
    assert.equal(await collision.locator('.hash-feedback').innerText(), 'Correct.');
    assert.equal(await explore.locator('input').first().inputValue(), '45');
    assert.match(await hashTable.innerText(), /6 of 6 checked cells/);
    await page.locator('.section-block').nth(0).click();
    assert.equal(await research.locator('input').nth(6).inputValue(), 'Research 6');
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});
