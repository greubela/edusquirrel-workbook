import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

function response(command, value) {
  const now = '2026-10-13T01:40:00';
  return JSON.stringify({
    timestamps: {timestampReceived:now, timestampStarted:now, timestampFinished:now},
    result: [1, {json:JSON.stringify(value), classSimpleName:'TestResponse'}],
    receivedExecutionCommand:command, additionalOutput:{loggerOut:'',loggerError:''}
  });
}

test('login preserves email, explains duplicate registration and observes failed token login', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true});
  try {
    const page = await browser.newPage();
    const requests = [];
    const requestHeaders = [];
    const errors = [];
    const missingTranslations = [];
    const destinations = ['ClassicJsonWithKey', 'ClassicKeylessJson', 'RelationalEvents'].map(format => ({
      syncDestination: {$type:'SyncDestinationBasedOnHistoryDatabase', format,
        databaseConfig: {$type:'DatabaseConfig', dbFormat:'mysql', host:'herrgreubel.lima-db.de', port:'3306', database:'db_332371_12'}},
      syncStrategy: {$type:'DesiredRelevance', desired:['MAJOR']}
    }));
    let transportFailure = false;
    let validLogin = false;
    let unknownAccount = false;
    page.on('pageerror', error => errors.push(error.message));
    page.on('console', msg => { if (process.env.LOGIN_TEST_DEBUG) console.error(msg.text()); });
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/executeCommand') {
        const headers = {'access-control-allow-origin':origin,'access-control-allow-credentials':'true',
          'access-control-allow-methods':'POST, OPTIONS','access-control-allow-headers':'Content-Type, Authorization'};
        if (route.request().method() === 'OPTIONS') return route.fulfill({status:204,headers});
        const command = route.request().postDataJSON();
        requests.push(command);
        requestHeaders.push(route.request().headers());
        if (transportFailure) return route.fulfill({status:503,headers,body:'Unavailable'});
        let value;
        if (command.name === 'create-account') value = {accountCreated:false,token:null};
        else if (command.name === 'login-command') {
          const user = {name:'Different Name',id:'test-user',mail:'first.last@example.org'};
          const token = {info:{user,createdAt:'2026-10-13T01:40:00',expiresAt:'2099-01-01T00:00:00'},tokenStringWithSignature:'mock-token'};
          value = {userKnown:true,tokenValid:validLogin,allUserInfo:validLogin ? {
            user,credentials:{signedBackendToken:token,singleAccessToken:null,llmApiConfig:null,databaseConfig:null},config:{syncDestinations:destinations,isOnlineAccount:true}} : null};
        }
        else if (command.name === 'fetch-from-db-request') value = {fetchedElements:[]};
        else if (command.name === 'sync-to-db-request' || command.name === 'clear-values-in-db-request') value = {elementsAdded:1,elementsChanged:0,elementsRemoved:0,timestampCommitted:'2026-11-09T12:00:00'};
        else if (command.name === 'auth-mail-request') value = {associatedUser:null,sent:!unknownAccount};
        else throw new Error('Unexpected command ' + command.name);
        return route.fulfill({headers,contentType:'application/json',body:response(command,value)});
      }
      if (url.origin !== origin) return route.abort();
      const file = path.resolve(root, '.' + decodeURIComponent(url.pathname) + (url.pathname.endsWith('/') ? 'index.html' : ''));
      if (!file.startsWith(root + path.sep)) return route.abort();
      try { await route.fulfill({body:await fs.readFile(file),contentType:mime[path.extname(file)] || 'application/octet-stream'}); }
      catch {
        if (url.pathname.includes('/languageMaps/eva/')) missingTranslations.push(url.pathname);
        await route.fulfill({status:404,body:'Not found'});
      }
    });
    await page.goto(origin + '/homepage/workbookDesign/index.html');
    const registration = page.locator('.section-register .login-area').first();
    const login = page.locator('.section-continue .login-area').first();
    const status = registration.locator('[role="status"]');
    await page.waitForFunction(() => [...document.querySelectorAll('.container-login input[placeholder]')].every(input => input.placeholder && !input.placeholder.startsWith('[')));
    assert.equal(await registration.locator('.login-name-input').inputValue(), '');
    assert.equal(await registration.locator('.login-email-input').inputValue(), '');
    await registration.locator('button').click();
    await page.waitForFunction(() => document.querySelector('.section-register .login-status-error'));
    assert.equal(requests.length, 0, 'blank email is rejected before a backend request');
    await registration.locator('.login-name-input').fill('First Last');
    assert.equal(await registration.locator('.login-email-input').inputValue(), 'first.last@student.hu-berlin.de');
    await registration.locator('.login-name-input').fill('Second Name');
    assert.equal(await registration.locator('.login-email-input').inputValue(), 'second.name@student.hu-berlin.de');
    await registration.locator('.login-email-input').fill('First.Last@example.org');
    assert.equal(await registration.locator('.login-email-input').inputValue(), 'First.Last@example.org');
    await registration.locator('.login-name-input').fill('Different Name');
    assert.equal(await registration.locator('.login-email-input').inputValue(), 'First.Last@example.org', 'editing the name never rewrites a supplied email');
    await registration.locator('button').click();
    await page.waitForFunction(() => /exist|already/i.test(document.querySelector('.section-register [role="status"]').textContent));
    assert.match(await status.innerText(), /exist|already/i);
    assert.equal(requests[0].json.userInfo.user.mail, 'first.last@example.org');
    assert.equal(await login.locator('.login-email-input').inputValue(), 'First.Last@example.org');
    await login.locator('.login-name-input').fill('  emailed-token  ');
    await login.locator('button').last().click();
    await page.waitForFunction(() => /ungültig|invalid/i.test(document.querySelector('.section-continue [role="status"]').textContent));
    assert.equal(requests.at(-1).json.userMail, 'first.last@example.org');
    assert.equal(requests.at(-1).json.accessToken[1].token, 'emailed-token');
    assert.equal(requests.filter(command => command.name === 'auth-mail-request').length, 0, 'invalid login never sends unsolicited mail');
    transportFailure = true;
    await login.locator('button').last().click();
    await page.waitForFunction(() => /fehlgeschlagen|failed/i.test(document.querySelector('.section-continue [role="status"]').textContent));
    assert.equal(await login.locator('button').last().isDisabled(), false, 'failed requests allow retry');
    transportFailure = false;
    await login.locator('button').first().click();
    await page.waitForFunction(() => /gesendet|sent/i.test(document.querySelector('.section-continue [role="status"]').textContent));
    assert.equal(requests.filter(command => command.name === 'auth-mail-request').length, 1, 'mail requests only follow the explicit button');
    transportFailure = true;
    await login.locator('button').first().click();
    await page.waitForFunction(() => /server.*(could not send|konnte keinen)/i.test(document.querySelector('.section-continue [role="status"]').textContent));
    assert.equal(await login.locator('button').first().isDisabled(), false, 'mail-service failures allow retry');
    transportFailure = false;
    unknownAccount = true;
    await login.locator('button').first().click();
    await page.waitForFunction(() => /No account|kein Konto/i.test(document.querySelector('.section-continue [role="status"]').textContent));
    unknownAccount = false;
    const defaultFetches = ['ClassicJsonWithKey', 'ClassicKeylessJson', 'RelationalEvents'].map(format => page.waitForRequest(request => {
      if (request.method() !== 'POST' || !request.url().endsWith('/executeCommand')) return false;
      const payload = request.postDataJSON();
      return payload?.name === 'fetch-from-db-request' && payload.json.databaseFormat === format;
    }));
    validLogin = true;
    await login.locator('button').last().click();
    await page.waitForFunction(() => !document.querySelector('.container-login'));
    for (const request of await Promise.all(defaultFetches)) {
      const config = request.postDataJSON().json.databaseConfig;
      assert.deepEqual(Object.keys(config).sort(), ['$type','database','dbFormat','host','port'].sort());
      assert.equal(config.$type, 'DatabaseConfig');
      assert.equal(config.database, 'db_332371_12');
    }
    const storedUser = await page.evaluate(() => JSON.parse(localStorage.getItem('edusquirrel-alluserinfo')));
    assert.equal(storedUser.user.mail, 'first.last@example.org', 'successful login persists the backend user');
    assert.equal(storedUser.credentials, undefined, 'credentials stay out of browser storage');
    assert(!JSON.stringify(storedUser).includes('mock-token'));
    await page.locator('.workbook-user-menu-button').click();
    await page.getByRole('menuitem', {name:'Session connections', exact:true}).click();
    const connections = page.getByRole('dialog', {name:'Session connections'});
    await connections.getByLabel('Use my MySQL database').check();
    await connections.getByLabel('Host', {exact:true}).fill('db.fixture.invalid');
    await connections.getByLabel('Database', {exact:true}).fill('user_history');
    await connections.getByLabel('User', {exact:true}).fill('session-owner');
    await connections.getByLabel('Password', {exact:true}).fill('ephemeral-database-password');
    await connections.getByLabel('Use my LLM configuration').check();
    await connections.getByLabel('Chat completion URL').fill('https://fixture.invalid/chat');
    await connections.getByLabel('API key').fill('ephemeral-browser-key');
    await connections.getByRole('button', {name:'Apply', exact:true}).click();
    await connections.waitFor({state:'detached'});
    assert(requests.some(command => command.name === 'fetch-from-db-request' && command.json.databaseConfig.$type === 'DatabaseConfigWithCredentials'), 'custom database sync uses the explicit credential-bearing configuration');
    assert(!await page.evaluate(() => /ephemeral-browser-key|ephemeral-database-password/.test(JSON.stringify(localStorage))));
    await page.locator('.workbook-user-menu-button').click();
    await page.getByRole('menuitem', {name:'Session connections', exact:true}).click();
    assert.equal(await connections.getByLabel('API key').inputValue(), 'ephemeral-browser-key', 'session credentials remain available in memory');
    assert.equal(await connections.getByLabel('Password', {exact:true}).inputValue(), 'ephemeral-database-password');
    await connections.getByRole('button', {name:'Cancel', exact:true}).click();
    // Storage restrictions must not keep a signed-in session alive after logout.
    await page.evaluate(() => {
      const removeItem = Storage.prototype.removeItem;
      Storage.prototype.removeItem = function(key) {
        if (key === 'edusquirrel-alluserinfo') throw new DOMException('Fixture storage denied', 'SecurityError');
        return removeItem.call(this, key);
      };
    });
    await page.locator('.workbook-user-menu-button').click();
    const exportedSession = page.waitForEvent('download');
    await page.getByRole('menuitem', {name:/^(Abmelden|Log out)$/}).click();
    const exported = await fs.readFile(await (await exportedSession).path(), 'utf8');
    assert(!/ephemeral-browser-key|ephemeral-database-password|mock-token|emailed-token/.test(exported), 'session exports exclude credentials');
    await page.locator('.container-login').waitFor();
    await login.locator('.login-email-input').fill('first.last@example.org');
    await login.locator('button').first().click();
    await page.waitForFunction(() => /gesendet|sent/i.test(document.querySelector('.section-continue [role="status"]').textContent));
    assert(!requestHeaders.at(-1).authorization, 'logout clears the backend token even when browser storage is unavailable');

    await page.reload();
    await page.locator('.container-login').waitFor();
    assert(!await page.evaluate(() => /ephemeral-browser-key|ephemeral-database-password/.test(JSON.stringify(localStorage))));

    assert.deepEqual(errors, []);
    assert.deepEqual(missingTranslations, [], 'startup requests only bundled translation files');
  } finally { await browser.close(); }
});
