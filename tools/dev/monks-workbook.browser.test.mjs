import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg'};

test('monk stories and revised tasks work with the current editor and interaction models', async () => {
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
      try { await route.fulfill({body:await readFile(file),contentType:mime[path.extname(file)] || 'application/octet-stream'}); }
      catch { await route.fulfill({status:404,body:'Not found'}); }
    });
    await installCodeMirrorModules(page);
    await page.goto(origin + '/homepage/monksWorkbook/');
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.select-language-line > div:has(.flag-fill-german-red)').click();
    assert.equal(await page.locator('.section-block').count(), 5);
    await page.locator('.section-block').first().click();
    const counter = page.locator('.slide-deck-counter');
    await counter.getByText('1/13', {exact:true}).waitFor();
    const next = page.locator('.slide-deck-navigation button').last();
    for (let i = 2; i <= 13; i++) {
      await next.click();
      await counter.getByText(`${i}/13`, {exact:true}).waitFor();
    }
    assert(await next.isDisabled());
    assert(await page.locator('.choice-interaction code').count() > 0, 'code options render as inline code');
    await page.locator('.section-block').nth(1).click();
    await counter.getByText('1/4', {exact:true}).waitFor();
    await page.locator('.answer-table thead th').getByText('Zeitpunkt', {exact:true}).waitFor();
    await page.locator('.section-block').nth(2).click();
    const open = () => page.locator('.workbook-body').getByRole('button', {name:/Editor.*öffnen|Open editor/i}).first();
    await open().click();
    const dialog = page.locator('dialog[open]');
    await dialog.getByRole('tab', {name:'Java',exact:true}).click();
    assert.equal(await dialog.getByRole('tab', {name:'Python',exact:true}).count(), 0);
    await dialog.getByRole('button', {name:'Full source',exact:true}).click();
    const code = dialog.locator('.cm-content');
    await code.waitFor();
    assert.match(await code.innerText(), /Turtle\.turnLeft\(90\)/);
    const solution = 'public class Zeichnung { public static void main(String[] args) {' +
      'Turtle.forward(60); Turtle.turnLeft(90); Turtle.forward(20); Turtle.turnLeft(90);' +
      'Turtle.forward(60); Turtle.turnLeft(90); Turtle.forward(20); } }';
    await code.fill(solution);
    await dialog.getByRole('button', {name:'Run',exact:true}).click();
    await dialog.locator('.turtle-execution__status').getByText("Here's your drawing.", {exact:true}).waitFor();
    await dialog.getByRole('tab', {name:'Snap!',exact:true}).click();
    await dialog.getByRole('tab', {name:'Java',exact:true}).click();
    await dialog.getByRole('button', {name:'Full source',exact:true}).click();
    await code.waitFor();
    assert.match(await code.innerText(), /Turtle\.(left|turnLeft)\(90/);
    await page.locator('.fullscreen-close-button').click();
    await open().click();
    await dialog.getByRole('tab', {name:'Java',exact:true}).click();
    await dialog.getByRole('button', {name:'Full source',exact:true}).click();
    assert.match(await code.innerText(), /Turtle\.forward\(60/);
    await page.locator('.fullscreen-close-button').click();
    await page.locator('.section-block').nth(4).click();
    assert.equal(await page.locator('.workbook-body').getByRole('button', {name:/Editor.*öffnen|Open editor/i}).count(), 3);
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});
