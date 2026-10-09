import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {createHash} from 'node:crypto';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html', '.js':'text/javascript', '.css':'text/css', '.json':'application/json', '.svg':'image/svg+xml', '.png':'image/png'};
test('Blockchain workbook persists research, grades balances and runs Hashq/SHA-256 experiments on mobile', async () => {
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
    await page.getByText('Cash and online payments', {exact:true}).waitFor();
    const comparison=page.locator('.answer-table').first();
    assert.equal(await comparison.locator('input').count(), 4);
    await comparison.locator('input').first().fill('No intermediary for the cash handover');
    for (let i=1; i<4; i++) await comparison.locator('input').nth(i).fill('Payment comparison ' + i);
    assert.match(await comparison.innerText(), /no automatic grade/);
    await page.locator('.section-block').nth(2).click();
    await page.getByText('Balances after entry 06', {exact:true}).waitFor();
    const balances=page.locator('.answer-table').first();
    const controlMetrics=el=>({height:el.getBoundingClientRect().height,padding:getComputedStyle(el).padding});
    const idleBalanceMetrics=await balances.locator('input').first().evaluate(controlMetrics);
    for (const [i,value] of ['0','20','15','5'].entries()) await balances.locator('input').nth(i).fill(value);
    assert.match(await balances.innerText(), /4 of 4 checked cells/);
    await balances.locator('input').first().fill('4');
    assert.match(await balances.innerText(), /3 of 4 checked cells/);
    await balances.locator('input').first().fill('0');
    assert.match(await balances.innerText(), /4 of 4 checked cells/);
    await balances.locator('input').first().hover();
    assert.deepEqual(await balances.locator('input').first().evaluate(controlMetrics), idleBalanceMetrics,
      'focus and hover must not resize table inputs or move the following radio controls');
    const decision=page.locator('.choice-interaction');
    await decision.getByLabel('Yes', {exact:true}).check();
    assert.match(await decision.locator('.choice-feedback').innerText(), /Reconsider/i);
    await decision.getByLabel('No', {exact:true}).check();
    assert.equal(await decision.locator('.choice-feedback').innerText(), 'Correct.');
    assert.deepEqual(await balances.locator('input').first().evaluate(controlMetrics), idleBalanceMetrics);
    await page.setViewportSize({width:390,height:844});
    const ledgerGeometry=await balances.evaluate(el => {
      const box=el.getBoundingClientRect();
      return {left:box.left,right:box.right,width:innerWidth,inlineStyles:el.querySelectorAll('[style], style').length};
    });
    assert(ledgerGeometry.left>=0 && ledgerGeometry.right<=ledgerGeometry.width+1);
    assert.equal(ledgerGeometry.inlineStyles, 0);
    await page.setViewportSize({width:1440,height:900});
    await page.locator('.section-block').nth(3).click();
    await page.getByText('Pseudonymity in Bitcoin', {exact:true}).waitFor();
    const privacy=page.locator('.answer-table');
    await privacy.locator('input').first().fill('Addresses are pseudonyms, not signatures');
    await privacy.locator('input').nth(1).fill('Exchange registration and payment records');
    assert.match(await privacy.innerText(), /no automatic grade/);
    await page.locator('.section-block').nth(4).click();
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
    await page.locator('.section-block').nth(5).click();
    const sha=page.locator('.sha256-interaction');
    await sha.first().waitFor();
    assert.equal(await sha.count(), 3);
    const comparisonSha=sha.first();
    const digest=text=>createHash('sha256').update(text,'utf8').digest('hex');
    assert.equal(await comparisonSha.locator('td code').first().innerText(), digest('Informatik'));
    assert.equal(await comparisonSha.locator('td code').nth(1).innerText(), digest('informatik'));
    assert.match(await comparisonSha.locator('.sha256-difference').innerText(), /134 of 256/);
    await comparisonSha.locator('textarea').nth(1).fill('Informatik');
    assert.match(await comparisonSha.locator('.sha256-difference').innerText(), /^0 of 256/);
    const unicodeText='Grüße 🌍\n ';
    await comparisonSha.locator('textarea').first().fill(unicodeText);
    assert.equal(await comparisonSha.locator('td code').first().innerText(), digest(unicodeText));
    await comparisonSha.locator('textarea').first().fill('');
    assert.equal(await comparisonSha.locator('td code').first().innerText(), digest(''));
    await comparisonSha.locator('textarea').first().fill('a'.repeat(4096));
    assert.equal(await comparisonSha.locator('td code').first().innerText(), digest('a'.repeat(4096)));
    assert.equal(await comparisonSha.locator('textarea').first().getAttribute('maxlength'), '4096');
    for (const viewport of [{width:390,height:844}, {width:844,height:390}, {width:1440,height:900}]) {
      await page.setViewportSize(viewport);
      const geometry=await comparisonSha.evaluate(el=>{
        const box=el.getBoundingClientRect(), output=el.querySelector('td code');
        return {left:box.left,right:box.right,width:innerWidth,inlineStyles:el.querySelectorAll('[style], style').length,
          overflow:getComputedStyle(output).overflowWrap,outputFits:output.scrollWidth<=output.clientWidth+1,
          border:getComputedStyle(el.querySelector('textarea')).borderTopWidth};
      });
      assert(geometry.left>=0 && geometry.right<=geometry.width+1);
      assert.equal(geometry.inlineStyles,0);
      assert.equal(geometry.overflow,'anywhere');
      assert(geometry.outputFits,'the complete 64-digit hash wraps on mobile');
      assert.equal(geometry.border,'1px');
    }
    await comparisonSha.getByRole('button',{name:'Reset inputs'}).click();
    assert.equal(await comparisonSha.locator('textarea').first().inputValue(),'Informatik');
    await comparisonSha.locator('textarea').first().fill(unicodeText);
    const prefixOne=sha.nth(1), prefixTwo=sha.nth(2);
    await prefixOne.locator('textarea').fill('abc');
    assert.match(await prefixOne.locator('.hash-feedback').innerText(),/does not yet/);
    await prefixOne.locator('textarea').fill('39');
    assert.equal(await prefixOne.locator('.hash-feedback').innerText(),'Correct.');
    await prefixTwo.locator('textarea').fill('39');
    assert.match(await prefixTwo.locator('.hash-feedback').innerText(),/does not yet/);
    await prefixTwo.locator('textarea').fill('286');
    assert.equal(await prefixTwo.locator('.hash-feedback').innerText(),'Correct.');
    assert.match(await prefixTwo.locator('td code').innerText(),/^00/);
    const space=page.locator('.answer-table');
    await space.locator('input').fill('512');
    assert.match(await space.innerText(),/0 of 1 checked cells/);
    await space.locator('input').fill('2^256');
    assert.match(await space.innerText(),/1 of 1 checked cells/);
    await page.reload();
    await page.locator('.section-block').nth(4).click({timeout:60000});
    assert.equal(await collision.locator('.hash-feedback').innerText(), 'Correct.');
    assert.equal(await explore.locator('input').first().inputValue(), '45');
    assert.match(await hashTable.innerText(), /6 of 6 checked cells/);
    await page.locator('.section-block').nth(5).click();
    await sha.first().waitFor();
    assert.equal(await comparisonSha.locator('textarea').first().inputValue(),unicodeText);
    assert.equal(await comparisonSha.locator('td code').first().innerText(),digest(unicodeText));
    assert.equal(await prefixOne.locator('.hash-feedback').innerText(),'Correct.');
    assert.equal(await prefixTwo.locator('.hash-feedback').innerText(),'Correct.');
    assert.match(await space.innerText(),/1 of 1 checked cells/);
    await page.locator('.section-block').nth(2).click();
    await page.getByText('Balances after entry 06', {exact:true}).waitFor();
    assert.match(await balances.innerText(), /4 of 4 checked cells/);
    assert.equal(await decision.getByLabel('No', {exact:true}).isChecked(), true);
    await page.locator('.section-block').nth(3).click();
    await page.getByText('Pseudonymity in Bitcoin', {exact:true}).waitFor();
    assert.equal(await privacy.locator('input').first().inputValue(), 'Addresses are pseudonyms, not signatures');
    await page.locator('.section-block').nth(1).click();
    await page.getByText('Cash and online payments', {exact:true}).waitFor();
    assert.equal(await comparison.locator('input').first().inputValue(), 'No intermediary for the cash handover');
    await page.locator('.section-block').nth(0).click();
    await page.getByText('Claims about Bitcoin', {exact:true}).waitFor();
    assert.equal(await research.locator('input').nth(6).inputValue(), 'Research 6');
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});
