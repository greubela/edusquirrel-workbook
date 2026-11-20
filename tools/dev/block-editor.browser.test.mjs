import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import assert from 'node:assert/strict';
import {mkdir, readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

/** Drags with real pointer events from the centre of one element to the centre of another. */
async function drag(page, from, to) {
  const start = await from.boundingBox();
  const end = await to.boundingBox();
  await page.mouse.move(start.x + start.width / 2, start.y + start.height / 2);
  await page.mouse.down();
  await page.mouse.move(start.x + start.width / 2 + 10, start.y + start.height / 2 + 10, {steps: 3});
  await page.mouse.move(end.x + end.width / 2, end.y + end.height / 2, {steps: 8});
  await page.mouse.up();
}

test('the Eva block tab inserts, moves, removes and edits blocks of the central answer', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(), chromiumSandbox:true});
  try {
    const page = await browser.newPage({viewport:{width:1440, height:1000}});
    page.setDefaultTimeout(60000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      const file = path.resolve(root, '.' + decodeURIComponent(url.pathname) + (url.pathname.endsWith('/') ? 'index.html' : ''));
      if (!file.startsWith(root + path.sep)) return route.abort();
      try { await route.fulfill({body:await readFile(file), contentType:mime[path.extname(file)] || 'application/octet-stream'}); }
      catch { await route.fulfill({status:404, body:'Not found'}); }
    });
    await installCodeMirrorModules(page);
    await page.goto(origin + '/homepage/workbookDesign/index.html');
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.section-block').first().click();
    await page.getByRole('button', {name:/Editor.*öffnen|Open editor/i}).first().click();

    const dialog = page.locator('dialog[open]');
    const blocksTab = dialog.getByRole('tab', {name:'Blocks'});
    await blocksTab.waitFor();
    if (await blocksTab.getAttribute('aria-selected') !== 'true') await blocksTab.click();
    const editor = dialog.locator('.block-editor');
    const statements = editor.locator('.block-editor__drawing [data-block-role=statement]');
    await editor.locator('.block-editor__drawing').waitFor();
    const initial = await statements.count();

    // Insert a template into the first slot.
    const forward = editor.locator('.block-editor__template').first();
    await drag(page, forward, editor.locator('.block-editor__drawing [data-block-role=slot]').first());
    await page.waitForFunction(count => document.querySelectorAll('dialog[open] .block-editor__drawing [data-block-role=statement]').length === count + 1, initial);

    // Parameters are expressions of the parameter's type: text that is not a number is rejected,
    // operators on literals are computed.
    await editor.locator('.block-editor__drawing [data-block-role=value]').first().click();
    const popup = editor.locator('.block-editor__popup');
    const input = popup.locator('input');
    await input.fill('abc');
    assert.equal(await input.getAttribute('aria-invalid'), 'true');
    assert(await popup.locator('.block-editor__popup-apply').isDisabled(), 'invalid values cannot be applied');
    assert((await popup.locator('[role=alert]').textContent()).length > 0, 'the rejection is explained');
    await input.fill('40+2');
    assert(!(await popup.locator('.block-editor__popup-apply').isDisabled()));
    await input.press('Enter');
    await popup.waitFor({state:'detached'});
    await editor.locator('.block-editor__drawing text', {hasText:/^42$/}).waitFor();

    await mkdir(path.join(root, 'tmp/work/browser-tests'), {recursive:true});
    await editor.screenshot({path:path.join(root, 'tmp/work/browser-tests/block-editor.png')});

    // A program block dropped on the library is removed.
    await drag(page, statements.first(), editor.locator('.block-editor__library'));
    await page.waitForFunction(count => document.querySelectorAll('dialog[open] .block-editor__drawing [data-block-role=statement]').length === count, initial);

    // A loop dragged to the end accepts a command in its body; its count must be a whole number.
    const slots = editor.locator('.block-editor__drawing [data-block-role=slot]');
    await drag(page, editor.locator('.block-editor__template', {hasText:'REPEAT-TIMES'}), slots.last());
    await page.waitForFunction(count => document.querySelectorAll('dialog[open] .block-editor__drawing [data-block-role=statement]').length === count + 1, initial);
    const loopBody = slots.nth(await slots.count() - 2);
    const start = await forward.boundingBox();
    const end = await loopBody.boundingBox();
    await page.mouse.move(start.x + start.width / 2, start.y + start.height / 2);
    await page.mouse.down();
    await page.mouse.move(end.x + end.width / 2, end.y + end.height / 2, {steps:10});
    await editor.screenshot({path:path.join(root, 'tmp/work/browser-tests/block-editor-dragging.png')});
    await page.mouse.up();
    // The body's command is the last one drawn with the template's default distance.
    await editor.locator('.block-editor__drawing [data-block-role=value]', {hasText:/^100$/}).last().click();
    await input.fill('37');
    await popup.locator('.block-editor__popup-apply').click();
    await popup.waitFor({state:'detached'});
    const count = editor.locator('.block-editor__drawing [data-block-role=value]', {hasText:/^4$/});
    await count.click();
    await input.fill('2.5');
    assert(await popup.locator('.block-editor__popup-apply').isDisabled(), 'repeat counts are whole numbers');
    await input.press('Escape');
    // A call keeps its function and folded arguments; its own values stay editable.
    await editor.locator('.block-editor__drawing [data-block-role=value]', {hasText:/^100$/}).first().click();
    await input.fill('add(3+5, 3)');
    await input.press('Enter');
    await editor.locator('.block-editor__drawing [data-block-role=value]', {hasText:/^8$/}).waitFor();
    await editor.screenshot({path:path.join(root, 'tmp/work/browser-tests/block-editor-loop.png')});

    // The other editors see the block edits.
    await dialog.getByRole('tab', {name:'Python'}).click();
    await page.waitForFunction(() => {
      const lines = (document.querySelector('dialog[open] .cm-content')?.innerText ?? '').split('\n').map(line => line.trim());
      const loop = lines.indexOf('for _ in range(4):');
      return loop >= 0 && lines[loop + 1]?.endsWith('forward(37)') && lines.some(line => line.endsWith('forward(add(8, 3))'));
    });
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
  }
});
