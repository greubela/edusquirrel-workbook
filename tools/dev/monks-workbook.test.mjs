import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg'};

test('monk story images exist and each speaking turn has one localized caption', async () => {
  const source = await fs.readFile(path.join(root, 'modules/client/src/main/scala/it/evadid/homepage/workbook/content/CreateMonksWorkbook.scala'), 'utf8');
  const folder = path.join(root, 'resources/languageMaps/eva/monksworkbook');
  const universal = JSON.parse(await fs.readFile(path.join(folder, 'map-universal.json')));
  const german = JSON.parse(await fs.readFile(path.join(folder, 'map-de.json')));
  const scenes = [...source.matchAll(/Scene\("([^"]+)", "([^"]+)"([^\n]*)/g)];
  assert.equal(scenes.length, 86);
  assert.equal(german.silence, '...');
  for (const [, id, imageKey, options] of scenes) {
    const image = universal[imageKey];
    assert(image, id + ' has an image mapping');
    await fs.access(path.join(root, 'resources/workbookresources', image));
    const caption = options.match(/dialogueKey = "([^"]+)"/)?.[1] || 'silence';
    assert(german[caption], id + ' has a caption');
    if (caption !== 'silence') {
      assert.equal((german[caption].match(/\*\*[RMK]:\*\*/g) || []).length, 1, id + ' has one speaker');
      assert(!german[caption].includes('\n'), id + ' has one speaking turn');
    }
  }
  assert(!Object.keys(german).some(key => /narration|narrator/i.test(key)));
});

test('monk stories and Koch practice render and can be navigated', {skip:!process.env.MONKS_BROWSER_TEST}, async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',args:['--no-sandbox']});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}});
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      let file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
      if (!file.startsWith(root + path.sep)) return route.abort();
      if (url.pathname === '/homepage/js/CodeMirrorLoader.js' && process.env.CODEMIRROR_TEST_BUNDLE) file = process.env.CODEMIRROR_TEST_BUNDLE;
      try { await route.fulfill({body:await fs.readFile(file),contentType:mime[path.extname(file)] || 'application/octet-stream'}); }
      catch { await route.fulfill({status:404,body:'Not found'}); }
    });
    await page.goto(origin + '/homepage/monksWorkbook/index.html');
    await page.locator('.section-register .login-area').nth(1).locator('button').click({timeout:60000});
    for (const [section, count] of [[0,69],[2,17]]) {
      await page.locator('.section-block').nth(section).click();
      const deck = page.locator('.slide-deck-container');
      await page.waitForFunction(expected => document.querySelector('.slide-deck-counter')?.textContent === expected, `1/${count}`);
      for (let i=0;i<count;i++) {
        assert.equal(await deck.locator('.slide-deck-counter').innerText(), `${i+1}/${count}`);
        assert.equal(await deck.locator('.slide-deck-column').count(), 1);
        assert.equal(await deck.locator('.slide-deck-column-title').innerText(), 'Dialog');
        await deck.locator('.slide-deck-image img').waitFor({timeout:10000});
        await page.waitForFunction(() => {const img=document.querySelector('.slide-deck-image img'); return img?.complete && img.naturalWidth>0;});
        if (i<count-1) await deck.locator('button').last().click();
      }
    }
    await page.locator('.section-block').nth(3).click();
    await page.waitForFunction(() => document.querySelectorAll('.turtle-gradig-panel').length === 3);
    const openButtons = page.getByRole('button', {name:/Editor.*öffnen|Open editor/i});
    assert.equal(await openButtons.count(), 3);
    const functionCode = ['def koch(length, depth):','    if depth == 0:','        forward(length)','    else:','        koch(length / 3, depth - 1)','        turn(-60)','        koch(length / 3, depth - 1)','        turn(120)','        koch(length / 3, depth - 1)','        turn(-60)','        koch(length / 3, depth - 1)'].join('\n');
    for (let depth=0;depth<3;depth++) {
      await openButtons.nth(depth).click();
      const dialog = page.locator('dialog[open]');
      await dialog.locator('.eva-editor').waitFor();
      await dialog.getByRole('button', {name:'Python',exact:true}).click();
      const code = dialog.locator('.cm-content');
      await code.waitFor();
      assert.equal((await code.innerText()).trim(), '', 'each exercise starts unsolved');
      await code.fill(functionCode + `\nkoch(270, ${depth})\n`);
      await page.waitForFunction(count => document.querySelectorAll('dialog[open] .turtle-line--correct').length === count, 4**depth);
      assert.equal(await dialog.locator('.turtle-line--missing, .turtle-line--unexpected').count(), 0);
      await page.locator('.fullscreen-close-button').click();
      await openButtons.nth(depth).click();
      await dialog.locator('.eva-editor').waitFor();
      await dialog.getByRole('button', {name:'Python',exact:true}).click();
      assert.match(await dialog.locator('.cm-content').innerText(), new RegExp(`koch\\(270, ${depth}\\)`), 'answer survives reopening');
      await page.locator('.fullscreen-close-button').click();
    }
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});
