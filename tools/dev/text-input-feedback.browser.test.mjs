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

test('text feedback demo owns its answer, renders localized hints and grades, retries once and restores saved results', async () => {
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
        if (prompt.originalRequest) return reply(pending,'CORRECT');
        if (prompt.hints) return reply(pending,'Welche unabhängige Kontaktmöglichkeit kennst du?');
        if (prompt.feedbackLanguage === 'English') return reply(pending,'Clear reasoning.\nCORRECT');
        if (prompt.criterion.includes('Echtheit')) return reply(pending,'Deine Begründung passt.');
        return reply(pending,'Prüfe den Kontaktweg.\nPARTIALL_CORRECT');
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
    const grade = content.locator('.text-input-feedback__grade');
    const results = content.locator('.text-input-feedback__result');
    await input.waitFor();
    await page.locator('.select-language-line > div:has(.flag-fill-german-red)').click();
    await content.getByText(/Erkläre, warum ein vertrauter Absendername/).waitFor();
    const answer = 'Ein Name kann gefälscht sein. Ich überprüfe die Anfrage über einen bekannten Kontaktweg.';
    await input.fill(answer);
    assert.equal(requests.length,0,'typing and changing language never automatically send the answer');
    await exercise.locator('.interaction-info__hint-toggle').click();
    const hints = exercise.locator('.interaction-info__hint-body');
    await hints.locator('.interaction-scaffolding__request').click();
    await hints.getByText('Welche unabhängige Kontaktmöglichkeit kennst du?',{exact:true}).waitFor();
    assert.equal(requests[0].answer,answer);
    assert(requests[0].hints.length === 1 && !requests[0].criterion,'hint requests do not include grading criteria');
    await hints.locator('.interaction-info__hint-close').click();
    await grade.click();
    await content.getByText('Deine Begründung passt.',{exact:true}).waitFor();
    await content.getByText('Prüfe den Kontaktweg.',{exact:true}).waitFor();
    assert.equal(await results.count(),2);
    await results.getByText('Richtig',{exact:true}).waitFor();
    await results.getByText('Teilweise richtig',{exact:true}).waitFor();
    assert(!await content.innerText().then(text => /CORRECT|INCORRECT|PARTIALL_CORRECT/.test(text)),'protocol markers are not rendered');
    assert.equal(requests.length,4,'one hint, two criterion requests and one format correction');
    assert.equal(await input.inputValue(),answer);
    assert(!await content.evaluate(element => element.querySelector('[style]')),'rendering uses dedicated CSS');
    if (process.env.WORKBOOK_SCREENSHOT) await content.screenshot({path:process.env.WORKBOOK_SCREENSHOT});
    for (const viewport of [{width:390,height:844},{width:844,height:390}]) {
      await page.setViewportSize(viewport);
      await input.scrollIntoViewIfNeeded();
      const bounds = await input.boundingBox();
      assert(bounds && bounds.x >= 0 && bounds.x + bounds.width <= viewport.width,'answer input fits narrow and landscape screens');
    }
    await page.setViewportSize({width:1440,height:1000});
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').click();
    assert.equal(await input.inputValue(),answer);
    assert.equal(requests.length,4);
    assert.equal(await results.count(),0,'feedback from another language is not shown as current-language feedback');
    await grade.click();
    await content.getByText('Clear reasoning.',{exact:true}).first().waitFor();
    await page.waitForFunction(() => document.querySelectorAll('.text-input-feedback__result strong').length === 2 &&
      [...document.querySelectorAll('.text-input-feedback__result strong')].every(element => element.textContent === 'Correct'));
    assert(requests.slice(-2).every(request => request.feedbackLanguage === 'English' && request.answer === answer));
    await page.waitForFunction(() => !document.querySelector('.text-input-feedback__grade')?.disabled);
    const persisted = await page.evaluate(() => new Promise(resolve => {
      const open = indexedDB.open('EvaDidInteractionDB');
      open.onerror = () => resolve(false);
      open.onsuccess = () => {
        const db = open.result;
        const transaction = db.transaction('variableHistoryStore','readonly');
        const rows = transaction.objectStore('variableHistoryStore').getAll();
        transaction.oncomplete = () => db.close();
        rows.onerror = () => resolve(false);
        rows.onsuccess = () => resolve(rows.result.some(row => row.id.includes('text-feedback-demo-answer_history') &&
          row.value.includes('Clear reasoning.')));
      };
    }));
    assert(persisted, 'completed grading is actually saved in IndexedDB before reloading');
    await page.reload();
    await page.locator('.section-block').last().click();
    await input.waitFor();
    assert.equal(await input.inputValue(),answer,'the same interaction restores the saved answer');
    await content.getByText('Clear reasoning.',{exact:true}).first().waitFor();
    assert.equal(await results.count(),2,'the same history restores the saved grades');
    assert.equal(requests.length,6,'reloading never calls the LLM');

    policy.mode = 'hold';
    await grade.click();
    await page.waitForFunction(() => document.querySelector('.text-input-feedback__grade')?.disabled);
    for (let attempt = 0; attempt < 200 && held.length < 2; attempt++) await page.waitForTimeout(25);
    assert.equal(held.length,2,'both independent graders reach the backend');
    await input.fill('A newly edited answer.');
    await Promise.all(held.splice(0).map(pending => reply(pending,'Feedback for the old answer.\nCORRECT')));
    await grade.waitFor({state:'visible'});
    await page.waitForFunction(() => !document.querySelector('.text-input-feedback__grade')?.disabled);
    assert.equal(await results.count(),0,'late grades remain associated with their original answer');
    assert.equal(await input.inputValue(),'A newly edited answer.');
    policy.mode = 'invalid';
    const beforeInvalid = requests.length;
    await grade.click();
    await content.getByText('The answer could not be checked. Please try again.',{exact:true}).first().waitFor();
    await page.waitForFunction(() => !document.querySelector('.text-input-feedback__grade')?.disabled);
    assert.equal(requests.length - beforeInvalid,4,'two invalid criteria each get only one correction attempt');
    assert.equal(await input.inputValue(),'A newly edited answer.');
    assert.deepEqual(errors,[]);
  } finally {await browser.close();}
});
