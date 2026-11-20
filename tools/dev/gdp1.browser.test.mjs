import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import assert from 'node:assert/strict';
import {mkdir, readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

test('2026GdP1 opens from the catalogue, localizes its tasks and exposes the progressive palettes', async () => {
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
    const language = flag => page.locator(`.select-language-line > div:has(.flag-fill-${flag})`);
    await language('german-red').click();
    await page.getByText('alle Aufgaben grün zu bekommen', {exact:false}).waitFor();
    assert.equal(await page.locator('.section-block').count(), 4);
    await page.locator('.section-block').nth(1).click();
    await page.getByText('Aufgabe 1 · Ein Quadrat von Hand', {exact:true}).waitFor();
    assert.equal(await page.locator('.workbook-body .interaction-element').count(), 5);
    await page.waitForFunction(() => document.querySelectorAll('.workbook-body .turtle-gradig-panel svg').length >= 4);
    const openButtons = () => page.locator('.workbook-body').getByRole('button', {name:/Editor.*öffnen|Open editor/i});
    await openButtons().first().click();
    await page.waitForFunction(() => window.gdpIde?.currentSprite?.blockTemplates('Blocks')?.some(item => item?.selector === 'forward'));
    const palette = () => page.evaluate(() => {
      const items = gdpIde.currentSprite.blockTemplates('Blocks');
      return {selectors:items.filter(item => item?.selector).map(item => item.selector),
        makeBlock:items.some(item => item?.gdpMakeBlock)};
    });
    const initial = await palette();
    assert.deepEqual(initial.selectors, ['receiveGo','forward','turn']);
    assert.equal(initial.makeBlock, false);
    await page.locator('dialog[open]').getByRole('tab', {name:'Java',exact:true}).click();
    const method = page.locator('dialog[open] .java-function-editor__method .cm-content');
    await method.waitFor();
    assert.match(await method.innerText(), /static void main/);
    assert(!/class Main/.test(await method.innerText()), 'Java opens the function editor rather than a raw source editor');
    await method.fill('static void main(String[] args) { ' + 'Turtle.forward(100); Turtle.right(90); '.repeat(4) + '}');
    await page.locator('dialog[open] .grading-info-line').getByRole('button', {name:/Prüfen|Check/,exact:true}).click();
    await page.locator('dialog[open] .feedback-score-chip').getByText('4/4', {exact:true}).waitFor();
    await page.locator('dialog[open] .feedback-entry.feedback-status--correct').nth(3).waitFor();
    await page.locator('dialog[open]').getByText('Programmiere die Zielfigur nach:', {exact:false}).waitFor();
    await page.locator('.fullscreen-close-button').click();
    await page.evaluate(() => {window.gdpIde = null;});
    await openButtons().nth(3).click();
    await page.waitForFunction(() => window.gdpIde?.world()?.worldCanvas.closest('dialog'));
    await page.waitForFunction(() => window.gdpIde?.currentSprite?.blockTemplates('Blocks')?.some(item => item?.gdpMakeBlock));
    assert.equal((await palette()).makeBlock, true, 'function tasks expose the native Make a block button');
    await language('british-blue').evaluate(flag => flag.click());
    await page.locator('dialog[open]').getByText('Call this function to draw the squares.', {exact:false}).waitFor();
    await page.locator('.fullscreen-close-button').click();
    await page.locator('.section-block').nth(2).click();
    await page.getByText('Task 7 · Size and orientation', {exact:true}).waitFor();
    assert.equal(await page.locator('.workbook-body .interaction-element').count(), 3);
    await page.waitForFunction(() => document.querySelectorAll('.workbook-body .turtle-gradig-panel svg').length >= 3);
    await mkdir(path.join(root,'tmp/work/gdp1'), {recursive:true});
    await page.screenshot({path:path.join(root,'tmp/work/gdp1/parameters.png'),fullPage:true});
    await page.locator('.workbook-body .interaction-element').last().screenshot({path:path.join(root,'tmp/work/gdp1/orientation-task.png')});
    await page.setViewportSize({width:390,height:844});
    await page.screenshot({path:path.join(root,'tmp/work/gdp1/parameters-mobile.png'),fullPage:true});
    await page.setViewportSize({width:1440,height:1000});
    await page.locator('.section-block').nth(3).click();
    await page.getByText('Task 10 · Sudoku solver', {exact:true}).waitFor();
    await page.locator('.workbook-body').getByRole('button', {name:/Editor.*öffnen|Open editor/i}).nth(2).click();
    await page.locator('dialog[open] .program-data-sudoku').waitFor();
    assert.equal(await page.locator('dialog[open] .program-data-sudoku td').count(), 81, 'the sudoku after main is shown beside the editor');
    await page.locator('dialog[open] .feedback-panel button.feedback-action--primary').click();
    await page.locator('dialog[open] .feedback-entry.feedback-status--incorrect').first().waitFor();
    assert.equal(await page.locator('dialog[open] .feedback-entry').count(), 8, 'all sudoku unit tests report a result');
    await page.locator('.fullscreen-close-button').click();
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});
