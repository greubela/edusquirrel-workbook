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
    assert.equal(await page.locator('.section-block').count(), 6);
    const labels = JSON.parse(await readFile(path.join(root, 'resources/languageMaps/eva/monksworkbook/map-de.json'), 'utf8'));
    const basic = JSON.parse(await readFile(path.join(root, 'resources/languageMaps/eva/basic/map-de.json'), 'utf8'));
    const section = key => page.locator('.section-block').filter({hasText: labels[key]});
    assert.equal(await section('welcomeTitle').count(), 1);
    assert.equal(await section('finishTitle').count(), 1);
    await section('sec1title').click();
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
    const check = exercise => exercise.locator('button.feedback-action--primary').click();
    // Answers are graded only on request. A failed check marks the wrong option until the next change;
    // the green strip of the solved task counts the checked answers.
    const choice = page.locator('.interaction-element:has(.choice-interaction input[type=radio])').first();
    await expand(choice);
    await choice.locator('input[type=radio]').nth(0).check();
    await choice.locator('.feedback-score-chip').getByText(basic.gradingNotRun, {exact:true}).waitFor();
    await check(choice);
    await choice.locator('label.answer-part--wrong').waitFor();
    await choice.locator('input[type=radio]').nth(1).check();
    assert.equal(await choice.locator('.answer-part--wrong').count(), 0);
    await check(choice);
    await choice.locator('.feedback-score-chip').getByText('1/1', {exact:true}).waitFor();
    await choice.locator('.interaction-info__attempts-count').getByText('2', {exact:true}).waitFor();
    const multiple = page.locator('.interaction-element:has(.choice-interaction input[type=checkbox])').first();
    await expand(multiple);
    // What the head monk needs to know: options 2, 4 and 6 are correct.
    for (const index of [1, 3, 5]) await multiple.locator('input[type=checkbox]').nth(index).check();
    await check(multiple);
    await multiple.locator('.feedback-score-chip').getByText('1/1', {exact:true}).waitFor();
    await multiple.locator('input[type=checkbox]').nth(5).uncheck();
    await check(multiple);
    await multiple.locator('.feedback-entry.feedback-status--partial').waitFor();
    const table = page.locator('.interaction-element:has(.answer-table)').first();
    await expand(table);
    await table.locator('input[type=text]').first().fill('3');
    await table.locator('input[type=text]').nth(1).fill('4');
    await check(table);
    await table.locator('.feedback-score-chip').getByText('1/4', {exact:true}).waitFor();
    assert.equal(await table.locator('td.answer-part--wrong').count(), 1, 'only the filled wrong cell is marked');
    const reorder = page.locator('.interaction-element:has(.reorder-list)').first();
    await expand(reorder);
    await reorder.locator('button.feedback-action--primary').click();
    await reorder.locator('.feedback-entry.feedback-status--incorrect').waitFor();
    assert(await page.locator('.choice-interaction code').count() > 0, 'code options render as inline code');
    // The monks' actions as a matching: connections by clicking both items in either order, one by dragging from the right.
    const matching = page.locator('.interaction-element:has(.matching-interaction)').first();
    const left = matching.locator('.matching-item--left');
    const right = matching.locator('.matching-item--right');
    for (const [row, category] of [1, 2, 0, 3].entries()) {
      const [first, second] = row === 3 ? [right.nth(category), left.nth(row)] : [left.nth(row), right.nth(category)];
      await first.click();
      await second.click();
    }
    await right.nth(4).dragTo(left.nth(4));
    assert.equal(await matching.locator('.matching-line').count(), 5);
    await expand(matching);
    await matching.locator('button.feedback-action--primary').click();
    await matching.locator('.feedback-score-chip').getByText('1/1', {exact:true}).waitFor();
    await matching.locator('.matching-remove').first().click();
    assert.equal(await matching.locator('.matching-line').count(), 4);
    // The call stack figure is an SVG data URL that the browser can decode; it spans the text width of its exercise.
    const figure = page.locator('[data-element-id="monks-image-callstack"] img');
    await figure.waitFor();
    assert(await figure.evaluate(img => img.complete && img.naturalWidth > 0), 'call stack figure renders');
    const [figureWidth, columnWidth] = await figure.evaluate(img =>
      [img.getBoundingClientRect().width, img.closest('.workbook-element').getBoundingClientRect().width]);
    console.log(`call stack figure: ${figureWidth}px of ${columnWidth}px`);
    assert(figureWidth > 720, `figure grows beyond its natural width: ${figureWidth}`);
    await section('partc').click();
    await counter.getByText('1/4', {exact:true}).waitFor();
    await page.locator('.answer-table thead th').getByText('Zeitpunkt', {exact:true}).waitFor();
    await section('sec3Title').click();
    const open = () => page.locator('.workbook-body').getByRole('button', {name:/Editor.*öffnen|Open editor/i}).first();
    await open().click();
    const dialog = page.locator('dialog[open]');
    await dialog.getByRole('tab', {name:'Java',exact:true}).click();
    assert.equal(await dialog.getByRole('tab', {name:'Python',exact:true}).count(), 1);
    // The method editor opens main; the full source is a read-only view of the whole class.
    // Until CodeMirror has loaded, the editor's plain text fallback edits the same answer.
    const code = dialog.locator('.cm-content, textarea.code-mirror-editor__fallback:visible').first();
    const codeText = () => code.evaluate(node => node.tagName === 'TEXTAREA' ? node.value : node.innerText);
    await code.waitFor();
    assert.match(await codeText(), /Turtle\.turnLeft\(90\)/);
    await code.fill('public static void main(String[] args) {\nTurtle.forward(60);\nTurtle.turnLeft(90);\nTurtle.forward(20);\n' +
      'Turtle.turnLeft(90);\nTurtle.forward(60);\nTurtle.turnLeft(90);\nTurtle.forward(20);\n}');
    await dialog.getByRole('tab', {name:'Snap!',exact:true}).click();
    await dialog.getByRole('tab', {name:'Java',exact:true}).click();
    await code.waitFor();
    assert.match(await codeText(), /Turtle\.(left|turnLeft)\(90/);
    await page.locator('.fullscreen-close-button').click();
    await open().click();
    await dialog.getByRole('tab', {name:'Java',exact:true}).click();
    await code.waitFor();
    assert.match(await codeText(), /Turtle\.forward\(60/);
    // Python: a block being typed gets no automatic pass and stays as typed.
    // (Switching from Java generates the Python tab from the Java answer, which shows valid Python with pass.)
    await dialog.getByRole('tab', {name:'Python',exact:true}).click();
    const editorLabels = JSON.parse(await readFile(path.join(root, 'resources/languageMaps/eva/blockeditor/map-de.json'), 'utf8'));
    await dialog.getByRole('tab', {name:editorLabels.pythonModeFull,exact:true}).click();
    await code.waitFor();
    const typed = (await codeText()).replace(/\s*$/, '') + '\n    if 1 > 0:\n        ';
    await code.fill(typed);
    await page.waitForTimeout(3000);
    assert.equal(await codeText(), typed, 'the typed text stays, without pass');
    await dialog.getByRole('tab', {name:'Java',exact:true}).click();
    await code.waitFor();
    assert.match(await codeText(), /if \(1 > 0\)/, 'the Python block reached the Java answer');
    await page.locator('.fullscreen-close-button').click();
    await section('sec4Title').click();
    assert.equal(await page.locator('.workbook-body').getByRole('button', {name:/Editor.*öffnen|Open editor/i}).count(), 1);
    // Survey: eight statements on a five point scale; consent is opt-in.
    await section('finishTitle').click();
    const rows = page.locator('.likert-interaction .likert-row');
    assert.equal(await rows.count(), 8);
    const agree = rows.first().locator('input[type=radio]').nth(4);
    await agree.check();
    assert(await agree.isChecked());
    const consent = page.locator('.interaction-element').filter({hasText: labels['survey-consent']}).locator('input[type=checkbox]');
    assert.equal(await consent.isChecked(), false);
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});
