import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

test('local preferences restore and credential-bearing saved logins are discarded without clearing answers', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true});
  try {
    const legacy = JSON.stringify({user:{name:'Saved student',id:'saved-student',mail:'saved@example.org'},token:null,
      config:JSON.stringify({syncDestinations:['localIndexedDb'],isOnlineAccount:false})});
    const current = JSON.stringify({user:{name:'Current student',id:'current-student',mail:'current@example.org'},
      config:{syncDestinations:[],isOnlineAccount:false}});
    for (const saved of [legacy, '{broken JSON', current]) {
      const page = await browser.newPage();
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
      await page.addInitScript(saved => {
        localStorage.setItem('edusquirrel-alluserinfo', saved);
        localStorage.setItem('saved-answer-fixture', 'keep this answer');
      }, saved);
      await page.goto(origin + '/homepage/embroideryWorkbook/');
      await page.locator('.workbook-app-shell').waitFor();
      if (saved === current) {
        await page.waitForFunction(() => {
          const restored = JSON.parse(localStorage.getItem('edusquirrel-alluserinfo'));
          return restored.user.id === 'current-student' && typeof restored.config === 'object';
        });
        assert.equal(await page.locator('.container-login').count(), 0, 'the existing local identity is restored');
      } else {
        await page.locator('.container-login').waitFor();
        assert.equal(await page.evaluate(() => localStorage.getItem('edusquirrel-alluserinfo')), null,
          'unsupported or corrupt saved logins are discarded');
        if (saved === legacy) {
          await page.locator('.select-language-line svg[viewBox="0 0 37 28"]').click();
          await page.waitForFunction(() => document.querySelector('.section-register .login-name-input')?.placeholder === 'Dit navn');
          assert.equal(await page.locator('.section-register .login-email-input').first().getAttribute('placeholder'), 'E-mailadresse',
            'Danish login entries are discovered and rendered by the current client');
        }
      }
      assert.equal(await page.evaluate(() => localStorage.getItem('saved-answer-fixture')), 'keep this answer');
      assert.deepEqual(errors, []);
      await page.close();
    }
  } finally { await browser.close(); }
});
