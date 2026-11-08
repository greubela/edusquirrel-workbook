import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const prefix = '/edusquirrel-workbook/';
const mime = {'.html':'text/html', '.js':'text/javascript', '.css':'text/css',
  '.json':'application/json', '.svg':'image/svg+xml', '.png':'image/png'};

test('text feedback preserves cached and in-flight grading across display languages', async () => {
  const browser = await chromium.launch({chromiumSandbox:true,
    executablePath:process.env.CHROMIUM_PATH || chromium.executablePath()});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}});
    page.setDefaultTimeout(60000);
    const errors = [], requests = [], held = [];
    const policy = {mode:'normal'};
    const headers = {'access-control-allow-origin':origin, 'access-control-allow-credentials':'true',
      'access-control-allow-methods':'POST, OPTIONS', 'access-control-allow-headers':'Content-Type, Authorization'};
    page.on('pageerror', error => errors.push(error.message));
    async function reply({route, command}, value) {
      const now = '2026-10-24T12:00:00';
      await route.fulfill({headers,contentType:'application/json',body:JSON.stringify({
        timestamps:{timestampReceived:now,timestampStarted:now,timestampFinished:now},
        result:[1,{json:JSON.stringify(value),classSimpleName:'String'}],
        receivedExecutionCommand:command,additionalOutput:{loggerOut:'',loggerError:''}
      })});
    }
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/executeCommand') {
        if (route.request().method() === 'OPTIONS') return route.fulfill({status:204,headers});
        const command = route.request().postDataJSON();
        if (command.name !== 'feedback-llm-request') return route.abort();
        const prompt = JSON.parse(command.json.prompt);
        requests.push(prompt);
        const pending = {route,command};
        if (policy.mode === 'hold') {held.push(pending);return;}
        if (policy.mode === 'invalid') return reply(pending,'A response without a valid marker.');
        if (prompt.originalRequest) return reply(pending,'PARTIALL_CORRECT');
        if (prompt.hints) return reply(pending,'Wann werden keine Karten mehr weitergegeben?');
        if (prompt.feedbackLanguage === 'English') return reply(pending,'Clear reasoning.\nCORRECT');
        return reply(pending,'Deine Begründung passt.');
      }
      if (url.origin !== origin || !url.pathname.startsWith(prefix)) return route.abort();
      const relative = decodeURIComponent(url.pathname.slice(prefix.length));
      if (relative.split('/').includes('..')) return route.abort();
      const file = path.resolve(root,relative + (relative.endsWith('/') ? 'index.html' : ''));
      try {await route.fulfill({body:await readFile(file),contentType:mime[path.extname(file)] || 'application/octet-stream'});}
      catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await installCodeMirrorModules(page);
    await page.goto(origin + prefix + 'homepage/workbookDesign/');
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.section-block').last().click();
    const exercise = page.locator('.interaction-element').filter({has:page.locator('.text-input-feedback')});
    const content = exercise.locator('.text-input-feedback');
    const input = content.locator('textarea');
    const grade = exercise.locator('.grading-info');
    const hint = exercise.locator('.scaffolding-info');
    const results = content.locator('.text-input-feedback__result');
    const selectLanguage = flag => page.locator(`.select-language-line > div:has(.flag-fill-${flag})`).click();
    async function waitForHeld() {
      for (let attempt = 0; attempt < 200 && held.length === 0; attempt++) await page.waitForTimeout(25);
      assert.equal(held.length, 1, 'exactly one backend operation is pending');
    }
    await input.waitFor();
    await selectLanguage('german-red');
    const answer = 'Die Rekursion endet, wenn das Tablett leer ist.';
    await input.fill(answer);
    assert.equal(requests.length, 0, 'editing and selecting a language do not send the answer to the LLM');
    await hint.click();
    await page.waitForFunction(() => [...document.querySelectorAll('.scaffolding-info')].some(
      icon => icon.dataset.tooltip === 'Wann werden keine Karten mehr weitergegeben?'));
    assert.equal(requests[0].answer, answer);
    assert(requests[0].hints.length === 1 && !requests[0].criterion);
    policy.mode = 'hold';
    await grade.click();
    await waitForHeld();
    await selectLanguage('british-blue');
    await grade.click();
    assert.equal(requests.length, 2, 'language changes and duplicate clicks join the pending grading');
    policy.mode = 'normal';
    await reply(held.shift(), 'Deine Begründung passt.');
    await content.getByText('Deine Begründung passt.', {exact: true}).waitFor();
    await results.getByText('Partially correct', {exact: true}).waitFor();
    assert.equal(requests.length, 3, 'only the missing classification marker requires one automatic correction');
    assert(!/CORRECT|INCORRECT|PARTIALL_CORRECT/.test(await content.innerText()));
    for (const flag of ['german-red', 'british-blue', 'german-red', 'british-blue']) {
      await selectLanguage(flag);
      await grade.click();
      await hint.click();
      assert.equal(await input.inputValue(), answer);
      await content.getByText('Deine Begründung passt.', {exact: true}).waitFor();
      assert.equal(requests.length, 3, 'completed hints and grades remain valid in every display language');
    }
    assert.equal(await hint.getAttribute('data-tooltip'), 'Wann werden keine Karten mehr weitergegeben?');
    assert(!await content.evaluate(element => element.querySelector('[style]')));
    for (const viewport of [{width:390,height:844}, {width:844,height:390}]) {
      await page.setViewportSize(viewport);
      await input.scrollIntoViewIfNeeded();
      const bounds = await input.boundingBox();
      assert(bounds && bounds.x >= 0 && bounds.x + bounds.width <= viewport.width);
    }
    await page.setViewportSize({width:1440,height:1000});
    await page.waitForFunction(() => new Promise(resolve => {
      const open = indexedDB.open('EvaDidInteractionDB');
      open.onerror = () => resolve(false);
      open.onsuccess = () => {
        const db = open.result;
        const transaction = db.transaction('variableHistoryStore', 'readonly');
        const rows = transaction.objectStore('variableHistoryStore').getAll();
        transaction.oncomplete = () => db.close();
        rows.onerror = () => resolve(false);
        rows.onsuccess = () => resolve(rows.result.some(row => row.id.includes('demo-feedback-1_history') &&
          row.value.includes('Deine Begründung passt.')));
      };
    }));
    await page.reload();
    await page.locator('.section-block').last().click();
    await input.waitFor();
    assert.equal(await input.inputValue(), answer);
    await content.getByText('Deine Begründung passt.', {exact: true}).waitFor();
    assert.equal(requests.length, 3, 'restoring saved feedback does not call the LLM');
    await selectLanguage('british-blue');
    await input.fill('The recursion ends when the tray is empty.');
    await grade.click();
    await content.getByText('Clear reasoning.', {exact: true}).waitFor();
    await results.getByText('Correct', {exact: true}).waitFor();
    assert.equal(requests.length, 4, 'a different answer requires a new assessment');
    policy.mode = 'hold';
    await input.fill('A draft about sorted cards.');
    await grade.click();
    await waitForHeld();
    await input.fill('A newly edited answer.');
    policy.mode = 'normal';
    await reply(held.shift(), 'Feedback for the old answer.\nCORRECT');
    await page.waitForTimeout(100);
    assert.equal(await results.count(), 1, 'the current answer retains its not-yet-requested status');
    assert(!await content.innerText().then(text => text.includes('Feedback for the old answer.')));
    policy.mode = 'invalid';
    const beforeInvalid = requests.length;
    await grade.click();
    await content.getByText('Check unavailable', {exact: true}).waitFor();
    assert.equal(requests.length - beforeInvalid, 2, 'an invalid classification gets only one correction attempt');
    policy.mode = 'normal';
    await grade.click();
    await content.getByText('Clear reasoning.', {exact: true}).waitFor();
    assert.equal(requests.length - beforeInvalid, 3, 'an explicit retry repeats an execution error');
    await grade.click();
    await page.waitForTimeout(100);
    assert.equal(requests.length - beforeInvalid, 3, 'successful assessments are reused');
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});
