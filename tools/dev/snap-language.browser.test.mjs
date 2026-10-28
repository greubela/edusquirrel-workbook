import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

test('test workbook preserves edits across human languages, programming languages and editor reopenings', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true});
  try {
    const page = await browser.newPage();
    page.setDefaultTimeout(60000);
    const errors = [];
    await page.addInitScript(() => {
      window.snapHistoryWrites = [];
      const put = IDBObjectStore.prototype.put;
      IDBObjectStore.prototype.put = function(value,...args) {
        if (this.name === 'variableHistoryStore') snapHistoryWrites.push(value.value);
        return put.call(this,value,...args);
      };
    });
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
              if (world.worldCanvas.closest('dialog')) { window.snapLanguageEditor = this; window.snapLanguageWorld = world; }
              return result;
            };
          })();`);
        if (url.pathname.endsWith('/turtlestitchsrc/store.js')) body = Buffer.from(body.toString() + `
          (() => {
            window.snapExportLanguages = [];
            const serialize = SnapSerializer.prototype.serialize;
            SnapSerializer.prototype.serialize = function(object,...args) {
              if (object instanceof Project) {
                snapExportLanguages.push(SnapTranslator.language);
                if (SnapTranslator.language !== 'en') throw new Error('Project export requires English');
              }
              return serialize.call(this,object,...args);
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
      .some(m => m.selector === 'forward' && m.blockSpec === 'gehe %n Schritte'), null, {timeout:60000}).catch(async error => {
        console.error({errors, state:await page.evaluate(() => ({language:SnapTranslator.language,
          alerts:[...document.querySelectorAll('[role=alert]')].map(e => e.textContent),
          blocks:window.snapLanguageEditor?.currentSprite?.scripts.allChildren().filter(m => m.selector).map(m => [m.selector,m.blockSpec])}))});
        throw error;
      });
    await page.waitForTimeout(750); // include the periodic native snapshot in the baseline
    const historyBeforeLanguage = await page.evaluate(() => snapHistoryWrites.length);
    const source = await page.evaluate(() => {
      window.germanIde = snapLanguageEditor;
      window.germanWorld = snapLanguageWorld;
      return [...new DOMParser().parseFromString(snapLanguageEditor.getProjectXML(),'application/xml').querySelectorAll('scripts')]
        .map(scripts => scripts.outerHTML);
    });
    // The modal makes header controls inert to pointer input. Dispatch the real
    // language control's click to exercise its signal while the editor stays open.
    await page.locator('.select-language-line > div').first().evaluate(flag => flag.click());
    await page.waitForFunction(() => snapLanguageEditor === germanIde && snapLanguageEditor.currentSprite.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'move %n steps'));
    assert.deepEqual(await page.evaluate(() => [...new DOMParser().parseFromString(snapLanguageEditor.getProjectXML(),'application/xml')
      .querySelectorAll('scripts')].map(scripts => scripts.outerHTML)), source);
    assert.equal(await page.locator('dialog[open]').count(), 1);
    assert(await page.evaluate(() => snapLanguageWorld === germanWorld), 'language changes retain the Morphic world');
    await page.locator('.select-language-line > div').nth(1).evaluate(flag => flag.click());
    await page.waitForFunction(() => snapLanguageEditor.currentSprite.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'gehe %n Schritte'));
    assert.equal(localeRequests.filter(url => url.endsWith('/lang-de.js')).length, 1, 'dictionary loads are shared across previews and reopenings');
    assert(await page.evaluate(() => snapLanguageEditor === germanIde && snapLanguageWorld === germanWorld), 'both native handles survive switching back');
    assert(await page.evaluate(() => snapExportLanguages.length > 0 && snapExportLanguages.every(language => language === 'en')), 'all project exports use English, including native language refresh');
    assert.equal(await page.evaluate(() => SnapTranslator.language), 'de', 'exports restore the display language');
    await page.waitForTimeout(750);
    assert.equal(await page.evaluate(() => snapHistoryWrites.length), historyBeforeLanguage, 'language-only refresh creates no answer history');
    async function editForward(value, commit = true) {
      const point = await page.evaluate(() => {
        const slot = snapLanguageEditor.currentSprite.scripts.allChildren().find(m => m.selector === 'forward').inputs()[0];
        const center = slot.contents().center();
        const canvas = snapLanguageWorld.worldCanvas;
        const rect = canvas.getBoundingClientRect();
        return {x:rect.left + center.x * rect.width / canvas.width, y:rect.top + center.y * rect.height / canvas.height};
      });
      await page.mouse.click(point.x, point.y);
      await page.keyboard.press('ControlOrMeta+A');
      await page.keyboard.insertText(String(value));
      if (commit) await page.keyboard.press('Enter');
      await page.waitForFunction(value => snapLanguageEditor.currentSprite.scripts.allChildren()
        .some(m => m.selector === 'forward' && Number(m.inputs()[0].evaluate()) === value), value);
    }
    await editForward(42);
    await page.waitForFunction(count => snapHistoryWrites.length > count, historyBeforeLanguage);
    const persisted = await page.evaluate(() => snapHistoryWrites.at(-1));
    assert(persisted.includes('SnapModel'), 'native edits persist a typed model');
    assert(!persisted.includes('data:image/png;base64,'), 'generated previews are absent from database history');
    // A language switch must also flush a numeric field still being edited.
    await editForward(91, false);
    await page.locator('.select-language-line > div').first().evaluate(flag => flag.click());
    await page.waitForFunction(() => snapLanguageEditor === germanIde && snapLanguageEditor.currentSprite.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'move %n steps' && Number(m.inputs()[0].evaluate()) === 91));
    await page.locator('.fullscreen-close-button').click();
    await page.locator('.select-language-line > div').nth(1).click();
    await page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first().click();
    await page.waitForFunction(() => snapLanguageEditor.currentSprite.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'gehe %n Schritte' && Number(m.inputs()[0].evaluate()) === 91));

    const editor = page.locator('dialog[open] .eva-editor');
    await editForward(92, false);
    await editor.getByRole('tab',{name:'Python',exact:true}).click();
    const code = editor.locator('.cm-content');
    await code.waitFor();
    assert.match(await code.innerText(), /forward\(92\)/, 'tab conversion includes the pending Snap input');
    await code.fill('forward(123)\nforward(17)\n');
    await page.locator('.select-language-line > div').first().evaluate(flag => flag.click());
    assert.match(await code.innerText(), /forward\(123\)\s+forward\(17\)/);
    await editor.getByRole('tab',{name:'Snap!',exact:true}).click();
    await page.waitForFunction(() => {
      const blocks = snapLanguageEditor.currentSprite.scripts.allChildren().filter(m => m.selector === 'forward');
      return [123,17].every(value => blocks.some(m => m.blockSpec === 'move %n steps' && Number(m.inputs()[0].evaluate()) === value));
    });
    await page.locator('.select-language-line > div').nth(1).evaluate(flag => flag.click());
    await page.waitForFunction(() => snapLanguageEditor.currentSprite.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'gehe %n Schritte' && Number(m.inputs()[0].evaluate()) === 123));
    await editor.getByRole('tab',{name:'Python',exact:true}).click();
    assert.match(await code.innerText(), /forward\(123\)\s+forward\(17\)/);
    await page.locator('.fullscreen-close-button').click();
    await page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first().click();
    assert.match(await code.innerText(), /forward\(123\)\s+forward\(17\)/);
    await editor.getByRole('tab',{name:'Java',exact:true}).click();
    await editor.getByRole('button',{name:'Full source',exact:true}).click();
    await code.fill('public class Drawing {\n  static void line(int size) { Turtle.forward(size); }\n  public static void main(String[] args) { line(64); }\n}\n');
    await editor.getByRole('tab',{name:'Snap!',exact:true}).click();
    await page.waitForFunction(() => {
      const xml = window.snapLanguageEditor?.getProjectXML();
      return xml?.includes('line') && xml.includes('64') && xml.includes('doJavaForward');
    });
    await page.locator('.select-language-line > div').first().evaluate(flag => flag.click());
    await page.waitForFunction(() => SnapTranslator.language === 'en');
    await editor.getByRole('tab',{name:'Java',exact:true}).click();
    await editor.getByRole('button',{name:'Full source',exact:true}).click();
    assert.match(await code.innerText(), /void line\(int size\)/);
    assert.match(await code.innerText(), /line\(64\)/);
    await page.locator('.fullscreen-close-button').click();
    await page.reload();
    await page.locator('.section-block').first().click();
    await page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first().click();
    await editor.getByRole('tab',{name:'Java',exact:true}).click();
    await editor.getByRole('button',{name:'Full source',exact:true}).click();
    assert.match(await code.innerText(), /void line\(int size\)/, 'saved methods survive a page reload');
    assert.match(await code.innerText(), /line\(64\)/);
    await page.locator('.fullscreen-close-button').click();
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});
