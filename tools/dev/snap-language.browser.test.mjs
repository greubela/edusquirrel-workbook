import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

test('the workbook language translates native block previews and a mounted editor without losing the project', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true});
  try {
    const page = await browser.newPage();
    page.setDefaultTimeout(60000);
    const errors = [];
    const localeRequests = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      const file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
      if (!file.startsWith(root + path.sep)) return route.abort();
      if (/\/adjusted\/lang-.*\.js$/.test(url.pathname)) localeRequests.push(url.pathname);
      try {
        let body = await fs.readFile(file);
        if (url.pathname.endsWith('/turtlestitchsrc/blocks.js')) body = Buffer.from(body.toString() + `
          (() => {
            window.snapLanguagePreviews = [];
            const picture = ScriptsMorph.prototype.scriptsPicture;
            ScriptsMorph.prototype.scriptsPicture = function(...args) {
              snapLanguagePreviews.push({language:SnapTranslator.language,
                specs:this.allChildren().filter(m => m instanceof BlockMorph && m.selector === 'forward').map(m => m.blockSpec)});
              return picture.apply(this,args);
            };
          })();`);
        if (url.pathname.endsWith('/turtlestitchsrc/gui.js')) body = Buffer.from(body.toString() + `
          (() => {
            const open = IDE_Morph.prototype.openIn;
            IDE_Morph.prototype.openIn = function(world,...args) {
              const result = open.call(this,world,...args);
              if (world.worldCanvas.closest('dialog')) window.snapLanguageEditor = this;
              return result;
            };
          })();`);
        await route.fulfill({body,contentType:mime[path.extname(file)] || 'application/octet-stream'});
      } catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await installCodeMirrorModules(page);
    await page.goto(origin + '/homepage/workbookDesign/index.html');
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.section-block').first().click();
    await page.waitForFunction(() => window.snapLanguagePreviews?.some(p => p.language === 'en' && p.specs.includes('move %n steps')));
    const preview = page.locator('.be-program-snap-renderer__canvas:not(dialog *)').first();
    await preview.evaluate(canvas => { window.englishPreview = canvas; });
    await page.locator('.select-language-line > div').nth(1).click();
    await page.waitForFunction(() => snapLanguagePreviews.some(p => p.language === 'de' && p.specs.includes('gehe %n Schritte')));
    assert(await preview.evaluate(canvas => canvas !== englishPreview), 'language changes replace a static preview');
    await page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first().click();
    await page.waitForFunction(() => window.snapLanguageEditor?.currentSprite?.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'gehe %n Schritte'));
    const source = await page.evaluate(() => {
      window.germanIde = snapLanguageEditor;
      return [...new DOMParser().parseFromString(snapLanguageEditor.getProjectXML(),'application/xml').querySelectorAll('scripts')]
        .map(scripts => scripts.outerHTML);
    });
    // The modal makes header controls inert to pointer input. Dispatch the real
    // language control's click to exercise its signal while the editor stays open.
    await page.locator('.select-language-line > div').first().evaluate(flag => flag.click());
    await page.waitForFunction(() => snapLanguageEditor !== germanIde && snapLanguageEditor.currentSprite.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'move %n steps'));
    assert.deepEqual(await page.evaluate(() => [...new DOMParser().parseFromString(snapLanguageEditor.getProjectXML(),'application/xml')
      .querySelectorAll('scripts')].map(scripts => scripts.outerHTML)), source);
    assert.equal(await page.locator('dialog[open]').count(), 1);
    await page.locator('.select-language-line > div').nth(1).evaluate(flag => flag.click());
    await page.waitForFunction(() => snapLanguageEditor.currentSprite.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'gehe %n Schritte'));
    assert.equal(localeRequests.filter(url => url.endsWith('/lang-de.js')).length, 1, 'dictionary loads are shared across previews and reopenings');
    await page.locator('.fullscreen-close-button').click();
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});
