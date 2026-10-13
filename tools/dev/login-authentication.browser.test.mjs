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
    const errors = [];
    let transportFailure = false;
    let validLogin = false;
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
        if (transportFailure) return route.fulfill({status:503,headers,body:'Unavailable'});
        let value;
        if (command.name === 'create-account') value = {accountCreated:false,token:null};
        else if (command.name === 'login-command') {
          const user = {name:'Different Name',id:'test-user',mail:'first.last@example.org'};
          const token = {info:{user,createdAt:'2026-10-13T01:40:00',expiresAt:'2099-01-01T00:00:00'},tokenStringWithSignature:'mock-token'};
          value = {userKnown:true,tokenValid:validLogin,user:validLogin ? user : null,
            signedToken:validLogin ? token : null,
            userConfigJson:validLogin ? JSON.stringify({syncDestinations:[],isOnlineAccount:true}) : null};
        }
        else if (command.name === 'auth-mail-request') value = {user:null,sent:true};
        else throw new Error('Unexpected command ' + command.name);
        return route.fulfill({headers,contentType:'application/json',body:response(command,value)});
      }
      if (url.origin !== origin) return route.abort();
      const file = path.resolve(root, '.' + decodeURIComponent(url.pathname) + (url.pathname.endsWith('/') ? 'index.html' : ''));
      if (!file.startsWith(root + path.sep)) return route.abort();
      try { await route.fulfill({body:await fs.readFile(file),contentType:mime[path.extname(file)] || 'application/octet-stream'}); }
      catch { await route.fulfill({status:404,body:'Not found'}); }
    });
    await page.goto(origin + '/homepage/workbookDesign/index.html');
    const registration = page.locator('.section-register .login-area').first();
    const login = page.locator('.section-continue .login-area').first();
    const status = registration.locator('[role="status"]');
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
    assert.equal(requests[0].json.user.mail, 'first.last@example.org');
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
    validLogin = true;
    await login.locator('button').last().click();
    await page.waitForFunction(() => !document.querySelector('.container-login'));
    const storedUser = await page.evaluate(() => JSON.parse(localStorage.getItem('edusquirrel-alluserinfo')));
    assert.equal(storedUser.user.mail, 'first.last@example.org', 'successful login persists the backend user');
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});
