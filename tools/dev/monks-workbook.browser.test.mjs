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
    assert.equal(await page.locator('.section-block').count(), 7);
    const labels = JSON.parse(await readFile(path.join(root, 'resources/languageMaps/eva/monksworkbook/map-de.json'), 'utf8'));
    const section = key => page.locator('.section-block').filter({hasText: labels[key]});
    assert.equal(await section('welcomeTitle').count(), 1);
    assert.equal(await section('finishTitle').count(), 1);
    await section('parta').click();
    const counter = page.locator('.slide-deck-counter');
    await counter.getByText('1/13', {exact:true}).waitFor();
    const next = page.locator('.slide-deck-navigation button').last();
    for (let i = 2; i <= 13; i++) {
      await next.click();
      await counter.getByText(`${i}/13`, {exact:true}).waitFor();
    }
    assert(await next.isDisabled());
    const story = page.locator('.interaction-element').filter({has: counter});
    // Overview results stay collapsed until expanded manually.
    const expand = async exercise => {
      assert(await exercise.locator('.feedback-details').isHidden());
      await exercise.locator('button.feedback-details-toggle').click();
    };
    await expand(story);
    await story.locator('.feedback-score-chip').getByText('1/1', {exact:true}).waitFor();
    const choice = page.locator('.interaction-element:has(.choice-interaction input[type=radio])').first();
    await expand(choice);
    await choice.locator('input[type=radio]').nth(1).check();
    await choice.locator('.feedback-score-chip').getByText('1/1', {exact:true}).waitFor();
    const multiple = page.locator('.interaction-element:has(.choice-interaction input[type=checkbox])').first();
    await expand(multiple);
    for (const index of [1, 3, 5]) await multiple.locator('input[type=checkbox]').nth(index).check();
    await multiple.locator('.feedback-score-chip').getByText('1/1', {exact:true}).waitFor();
    await multiple.locator('input[type=checkbox]').nth(3).uncheck();
    await multiple.locator('.feedback-entry.feedback-status--partial').waitFor();
    const table = page.locator('.interaction-element:has(.answer-table)').first();
    await expand(table);
    await table.locator('input[type=text]').first().fill('3');
    await table.locator('.feedback-score-chip').getByText('1/4', {exact:true}).waitFor();
    const reorder = page.locator('.interaction-element:has(.reorder-list)').first();
    await expand(reorder);
    await reorder.locator('button.feedback-action--primary').click();
    await reorder.locator('.feedback-entry.feedback-status--incorrect').waitFor();
    assert(await page.locator('.choice-interaction code').count() > 0, 'code options render as inline code');
    await section('partc').click();
    await counter.getByText('1/4', {exact:true}).waitFor();
    await page.locator('.answer-table thead th').getByText('Zeitpunkt', {exact:true}).waitFor();
    await section('partzero').click();
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
    await section('kochtitle').click();
    assert.equal(await page.locator('.workbook-body').getByRole('button', {name:/Editor.*öffnen|Open editor/i}).count(), 3);
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});
