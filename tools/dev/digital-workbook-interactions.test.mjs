import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html', '.js':'text/javascript', '.css':'text/css', '.json':'application/json', '.svg':'image/svg+xml', '.png':'image/png'};

test('digital image chapter persists answer tables, choices and neuron edits, rejects drafts and fits mobile', async () => {
  await fs.access(path.join(root, 'artifacts/newest/client.js'));
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
    await page.goto(origin + '/homepage/imageRecognitionWorkbook/index.html');
    await page.locator('.section-register .login-area').nth(1).locator('button').click({timeout:60000});
    const opinion = page.locator('.choice-interaction').first();
    await opinion.locator('input').nth(1).check();
    assert.match(await opinion.innerText(), /Opinion saved/);
    await opinion.locator('input').nth(2).check();
    assert.equal(await opinion.locator('input:checked').count(), 1, 'single choices are exclusive');
    await page.locator('.section-block').nth(1).click();
    const answerTable = page.locator('.answer-table');
    const answers = ['0','1','0','1', '1','1','1','1', '0','0','1','1', '0','1','0','1'];
    assert.equal(await answerTable.locator('select').count(), 16);
    assert.equal(await answerTable.locator('tbody td:not(:has(select))').count(), 4, 'Monday is fixed');
    assert.match(await answerTable.innerText(), /0 of 16 checked cells/);
    for (const [i, value] of answers.entries()) await answerTable.locator('select').nth(i).selectOption(value);
    assert.match(await answerTable.innerText(), /16 of 16 checked cells/);
    await answerTable.locator('select').first().selectOption('1');
    assert.match(await answerTable.innerText(), /15 of 16 checked cells/);
    await answerTable.locator('select').first().selectOption('');
    assert.match(await answerTable.innerText(), /15 of 16 checked cells/);
    await answerTable.locator('select').first().selectOption('0');
    for (const viewport of [{width:390,height:844}, {width:1440,height:900}]) {
      await page.setViewportSize(viewport);
      const geometry = await answerTable.evaluate(el => {
        const box = el.getBoundingClientRect();
        return {left:box.left,right:box.right,width:innerWidth,
          overflow:getComputedStyle(el.querySelector('.answer-table__scroll')).overflowX,
          border:getComputedStyle(el.querySelector('select')).borderTopWidth,
          inlineStyles:el.querySelectorAll('[style], style').length};
      });
      assert(geometry.left>=0 && geometry.right<=geometry.width+1, 'answer table fits viewport');
      assert.equal(geometry.overflow, 'auto');
      assert.equal(geometry.border, '1px');
      assert.equal(geometry.inlineStyles, 0);
    }
    const choices = page.locator('.choice-interaction');
    await choices.nth(0).locator('input').nth(1).check();
    assert.match(await choices.nth(0).innerText(), /Reconsider/);
    await choices.nth(0).locator('input').nth(0).check();
    assert.match(await choices.nth(0).innerText(), /Correct\./);
    await choices.nth(1).locator('input').nth(0).check();
    assert.match(await choices.nth(1).innerText(), /Reconsider/);
    await choices.nth(1).locator('input').nth(1).check();
    assert.match(await choices.nth(1).innerText(), /Correct\./);
    await choices.nth(1).locator('input').nth(2).check();
    assert.match(await choices.nth(1).innerText(), /Reconsider/);
    await choices.nth(1).locator('input').nth(2).uncheck();
    const open = () => page.getByRole('button', {name:/Open editor/i}).click();
    await open();
    const editor = page.locator('dialog[open] .neuron-editor');
    await editor.waitFor();
    assert.equal(await editor.locator('tbody tr').count(), 5);
    assert.match(await editor.innerText(), /4 of 5 days match/);
    for (const [i, value] of ['1','1','0','2','4'].entries()) await editor.locator('input').nth(i).fill(value);
    assert.match(await editor.innerText(), /5 of 5 days match/);
    const validTable = await editor.locator('tbody').innerText();
    await editor.locator('input').nth(0).fill('');
    assert.match(await editor.locator('.neuron-error').innerText(), /last valid parameters/);
    assert.equal(await editor.locator('tbody').innerText(), validTable, 'invalid draft cannot replace the valid result');
    await page.locator('.fullscreen-close-button').click();
    assert.match(await page.locator('.neuron-preview').innerText(), /5 of 5/);
    await open();
    assert.equal(await editor.locator('input').nth(0).inputValue(), '1', 'reopening discards invalid local draft');
    for (const viewport of [{width:1440,height:900}, {width:390,height:844}, {width:844,height:390}]) {
      await page.setViewportSize(viewport);
      const geometry = await editor.evaluate(el => {
        const box = el.getBoundingClientRect();
        const controls = el.querySelector('.neuron-controls').getBoundingClientRect();
        const table = el.querySelector('.neuron-results');
        return {left:box.left,right:box.right,controlsLeft:controls.left,controlsRight:controls.right,
          width:innerWidth, tableOverflow:getComputedStyle(table).overflowX,
          inlineStyles:el.querySelectorAll('[style], style').length,
          cellBorder:getComputedStyle(el.querySelector('td')).borderTopWidth,
          inputBorder:getComputedStyle(el.querySelector('input')).borderTopWidth};
      });
      assert(geometry.left>=0 && geometry.right<=geometry.width+1, 'editor fits viewport');
      assert(geometry.controlsLeft>=geometry.left && geometry.controlsRight<=geometry.right+1, 'controls fit editor');
      assert.equal(geometry.tableOverflow,'auto');
      assert.equal(geometry.cellBorder,'1px', 'table borders resolve through shared color tokens');
      assert.equal(geometry.inputBorder,'1px', 'input borders resolve through shared color tokens');
      assert.equal(geometry.inlineStyles,0, 'presentation comes from dedicated CSS');
    }
    await page.locator('.fullscreen-close-button').click();
    await page.setViewportSize({width:1440,height:900});
    await page.reload();
    await page.locator('.section-block').nth(1).click({timeout:60000});
    assert.match(await page.locator('.neuron-preview').innerText(), /5 of 5/);
    assert.match(await answerTable.innerText(), /16 of 16 checked cells/);
    assert.deepEqual(await answerTable.locator('select').evaluateAll(els => els.map(el => el.value)), answers,
      'all table answers restore after reload');
    assert.equal(await choices.nth(1).locator('input:checked').count(), 2, 'multiple choices restore after reload');
    await open();
    await editor.getByRole('button', {name:'Reset weights'}).click();
    assert.match(await editor.innerText(), /4 of 5 days match/);
    assert.deepEqual(errors, [], 'actual renderer/editor flow has no uncaught errors');
  } finally { await browser.close(); }
});
