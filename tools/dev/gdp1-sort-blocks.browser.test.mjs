import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

test('2026GdP1 sorting offers Snap number-list blocks that run in Snap and read back as NumberList calls', async () => {
  const browser = await chromium.launch({chromiumSandbox:true,
    executablePath:process.env.CHROMIUM_PATH || chromium.executablePath()});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}});
    page.setDefaultTimeout(60000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      const file = path.resolve(root, '.' + decodeURIComponent(url.pathname) + (url.pathname.endsWith('/') ? 'index.html' : ''));
      if (!file.startsWith(root + path.sep)) return route.abort();
      try {
        let body = await readFile(file);
        if (url.pathname.endsWith('/turtlestitchsrc/gui.js')) body = Buffer.from(body.toString() + `
          (() => {
            const open = IDE_Morph.prototype.openIn;
            IDE_Morph.prototype.openIn = function(world,...args) {
              const result = open.call(this,world,...args);
              if (world.worldCanvas.closest('dialog')) window.gdpIde = this;
              return result;
            };
            const make = SpriteMorph.prototype.makeBlockButton;
            SpriteMorph.prototype.makeBlockButton = function(...args) {
              const button = make.apply(this,args);
              if (button) button.gdpMakeBlock = true;
              return button;
            };
          })();`);
        await route.fulfill({body,contentType:mime[path.extname(file)] || 'application/octet-stream'});
      } catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await installCodeMirrorModules(page);
    await page.goto(origin + '/homepage/workbooks/');
    const link = page.locator('a[href="../2026GdP1/"]');
    await link.waitFor();
    await page.waitForFunction(() => document.querySelector('a[href="../2026GdP1/"]')?.textContent.includes('2026GdP1'));
    await link.click();
    assert.equal(new URL(page.url()).pathname, '/homepage/2026GdP1/');
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.select-language-line > div:has(.flag-fill-german-red)').click();
    await page.locator('.section-block').nth(3).click();
    await page.getByText('Aufgabe 11', {exact:false}).first().waitFor();
    await page.locator('.workbook-body').getByRole('button', {name:/Editor.*öffnen|Open editor/i}).nth(3).click();
    const listBlocks = ['item %\'index\' of number list', 'set item %\'index\' of number list to %\'value\'',
      'swap items %\'first\' and %\'second\' of number list', 'length of number list'];
    await page.locator('dialog[open]').getByRole('tab', {name:'Snap!',exact:true}).click();
    await page.waitForFunction(count => (window.gdpIde?.currentSprite?.blockTemplates('Blocks') ?? [])
      .filter(item => item?.isCustomBlock).length >= count, listBlocks.length);
    assert.deepEqual(await page.evaluate(() => gdpIde.currentSprite.blockTemplates('Blocks')
      .filter(item => item?.isCustomBlock).map(item => item.definition.spec)), [...listBlocks, 'sortiere'],
      'the sort task offers the number-list blocks next to the own sortiere block');
    // The blocks really work in Snap: swap, then store the length and a read item, on the default list 7,3,9,1,5.
    const list = await page.evaluate(async () => {
      const definition = start => gdpIde.stage.globalBlocks.find(block => block.spec.startsWith(start));
      const run = async block => {
        const threads = gdpIde.stage.threads;
        const process = threads.startProcess(block, gdpIde.currentSprite);
        for (let step = 0; step < 200 && threads.processes.includes(process); step++) {
          threads.step();
          await new Promise(resolve => setTimeout(resolve, 0));
        }
        if (process.errorFlag || threads.processes.includes(process)) throw new Error('Snap did not finish ' + block.blockSpec);
      };
      const swap = definition('swap items').blockInstance();
      swap.inputs()[0].setContents(0); swap.inputs()[1].setContents(2);
      await run(swap);
      const setLength = definition('set item').blockInstance();
      setLength.inputs()[0].setContents(4);
      setLength.replaceInput(setLength.inputs()[1], definition('length of').blockInstance());
      await run(setLength);
      const setItem = definition('set item').blockInstance();
      const item = definition('item %').blockInstance();
      item.inputs()[0].setContents(1);
      setItem.inputs()[0].setContents(3);
      setItem.replaceInput(setItem.inputs()[1], item);
      await run(setItem);
      // Attached under the green flag, the block is part of the program the other tabs show.
      const hat = gdpIde.currentSprite.scripts.children.find(script => script.selector === 'receiveGo');
      hat.bottomBlock().nextBlock(swap);
      return gdpIde.globalVariables.vars['number list'].value.itemsArray().map(Number);
    });
    assert.deepEqual(list, [9, 3, 7, 3, 5]);
    // The editor publishes the project to the answer state, whose source the overview shows in Python.
    await page.waitForFunction(() => [...document.querySelectorAll('.java-full-editor-preview')]
      .some(preview => preview.innerText.includes('NumberList.swap(0, 2)')));
    await page.locator('dialog[open]').getByRole('tab', {name:'Python',exact:true}).click();
    await page.waitForFunction(() => document.querySelector('dialog[open] .cm-content')?.innerText.includes('NumberList.swap(0, 2)'));
    assert(!/number list|item_of/.test(await page.locator('dialog[open] .cm-content').first().innerText()),
      'the blocks become NumberList calls, without their definitions');
    await page.locator('.fullscreen-close-button').click();
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});
