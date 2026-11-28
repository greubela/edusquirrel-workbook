import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const germanEditorLabels = JSON.parse(await fs.readFile(path.join(root, 'resources/languageMaps/eva/blockeditor/map-de.json'), 'utf8'));
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
        const request = put.call(this,value,...args);
        if (this.name === 'variableHistoryStore' &&
            JSON.parse(value.id).keyForSerialisation === 'turtle-recreate-1_history') {
          const saved = value.value;
          this.transaction.addEventListener('complete', () => snapHistoryWrites.push(saved));
        }
        return request;
      };
      window.snapAnswerEvents = () => {
        const saved = snapHistoryWrites.at(-1);
        return saved ? JSON.parse(saved).historyJson.events.values
          .filter(event => event.$type === 'AnswerChangedEvent') : [];
      };
      window.snapSavedForward = value => {
        function containsForward(node) {
          if (!node || typeof node !== 'object') return false;
          if (node.tag === 'block' && node.attributes?.some(a => a.name === 's' && a.rawValue === 'forward') &&
              node.children?.some(child => child.element?.tag === 'l' &&
                child.element.children?.some(text => text.raw === String(value)))) return true;
          return Object.values(node).some(containsForward);
        }
        return snapAnswerEvents().some(event => event.newState?.$type === 'SnapModel' && containsForward(event.newState));
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
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').click();
    // Start local usage explicitly: there is no default user.
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.section-block').first().click();
    await page.waitForFunction(() => window.snapLanguagePreviews?.some(p => p.language === 'en' && p.specs.includes('move %n steps')));
    const preview = page.locator('.be-program-snap-renderer__canvas:not(dialog *)').first();
    await preview.evaluate(canvas => { window.englishPreview = canvas; });
    await page.locator('.select-language-line > div:has(.flag-fill-german-red)').click();
    await page.waitForFunction(() => snapLanguagePreviews.some(p => p.language === 'de' && p.specs.includes('gehe %n Schritte')));
    assert(await preview.evaluate(canvas => canvas !== englishPreview), 'language changes replace a static preview');
    await page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first().click();
    await page.locator('dialog[open]').getByRole('tab',{name:'Snap!',exact:true}).click();
    await page.waitForFunction(() => window.snapLanguageEditor?.currentSprite?.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'gehe %n Schritte'), null, {timeout:60000}).catch(async error => {
        console.error({errors, state:await page.evaluate(() => ({language:SnapTranslator.language,
          alerts:[...document.querySelectorAll('[role=alert]')].map(e => e.textContent),
          blocks:window.snapLanguageEditor?.currentSprite?.scripts.allChildren().filter(m => m.selector).map(m => [m.selector,m.blockSpec])}))});
        throw error;
      });
    const originalViewport = page.viewportSize();
    for (const width of [1280, 760]) {
      await page.setViewportSize({width, height: 900});
      const panes = await page.locator('dialog[open] .editor-with-panel').evaluate(container => {
        const editor = container.querySelector('.fullscreen-editor').getBoundingClientRect();
        const sidebar = container.querySelector('.fullscreen-sidepanel').getBoundingClientRect();
        return {ratio: editor.width / sidebar.width, editorRight: editor.right, sidebarLeft: sidebar.left,
          overlap: Math.min(editor.bottom, sidebar.bottom) - Math.max(editor.top, sidebar.top)};
      });
      assert(panes.editorRight <= panes.sidebarLeft + 1 && panes.overlap > 0,
        'editor and sidebar stay beside each other at desktop and narrow widths');
      assert(panes.ratio > 2.8 && panes.ratio < 3.2, 'editor receives three quarters of the pane width');
    }
    await page.setViewportSize(originalViewport);
    // Request grading through the shared feedback line. It must produce public
    // results without replacing the editor or rewriting its current draft.
    await page.locator('dialog[open] .grading-info-line').getByRole('button', {name: 'Prüfen', exact: true}).click();
    await page.waitForFunction(() => [...document.querySelectorAll('dialog[open] .feedback-date')]
      .some(time => /\d{4}-\d{2}-\d{2}/.test(time.getAttribute('title') ?? '')));
    await page.waitForFunction(() => snapAnswerEvents().some(event => event.newState?.$type === 'SnapModel'));
    const historyBeforeLanguage = await page.evaluate(() => snapHistoryWrites.length);
    const answersBeforeLanguage = await page.evaluate(() => snapAnswerEvents().map(event => event.eventId).sort());
    const source = await page.evaluate(() => {
      window.germanIde = snapLanguageEditor;
      window.germanWorld = snapLanguageWorld;
      return [...new DOMParser().parseFromString(snapLanguageEditor.getProjectXML(),'application/xml').querySelectorAll('scripts')]
        .map(scripts => scripts.outerHTML);
    });
    // The modal makes header controls inert to pointer input. Dispatch the real
    // language control's click to exercise its signal while the editor stays open.
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').evaluate(flag => flag.click());
    await page.waitForFunction(() => snapLanguageEditor === germanIde && snapLanguageEditor.currentSprite.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'move %n steps'));
    assert.deepEqual(await page.evaluate(() => [...new DOMParser().parseFromString(snapLanguageEditor.getProjectXML(),'application/xml')
      .querySelectorAll('scripts')].map(scripts => scripts.outerHTML)), source);
    assert.equal(await page.locator('dialog[open]').count(), 1);
    assert(await page.evaluate(() => snapLanguageWorld === germanWorld), 'language changes retain the Morphic world');
    await page.locator('.select-language-line > div:has(.flag-fill-german-red)').evaluate(flag => flag.click());
    await page.waitForFunction(() => snapLanguageEditor.currentSprite.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'gehe %n Schritte'));
    assert.equal(localeRequests.filter(url => url.endsWith('/lang-de.js')).length, 1, 'dictionary loads are shared across previews and reopenings');
    assert(await page.evaluate(() => snapLanguageEditor === germanIde && snapLanguageWorld === germanWorld), 'both native handles survive switching back');
    assert(await page.evaluate(() => snapExportLanguages.length > 0 && snapExportLanguages.every(language => language === 'en')), 'all project exports use English, including native language refresh');
    assert.equal(await page.evaluate(() => SnapTranslator.language), 'de', 'exports restore the display language');
    await page.waitForTimeout(750);
    assert.deepEqual(await page.evaluate(() => snapAnswerEvents().map(event => event.eventId).sort()),
      answersBeforeLanguage, 'language-only refresh creates no answer history');
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
    await editForward(42, false);
    // CI can save another exercise while the Snap input is still pending.
    await page.evaluate(() => new Promise((resolve, reject) => {
      const open = indexedDB.open('EvaDidInteractionDB');
      open.onerror = () => reject(open.error);
      open.onsuccess = () => {
        const db = open.result;
        const transaction = db.transaction('variableHistoryStore', 'readwrite');
        transaction.objectStore('variableHistoryStore').put({
          id: JSON.stringify({programId:'edusquirrel', scenarioId:'workbookTest', userId:'probe',
            keyForSerialisation:'unrelated-exercise_history'}),
          value: JSON.stringify({format:'TextInputWithLlmGradingAndScaffoldingFormat',
            historyJson:{events:{values:[]}}})
        });
        transaction.oncomplete = () => { db.close(); resolve(); };
        transaction.onabort = () => { db.close(); reject(transaction.error); };
      };
    }));
    assert.equal(await page.evaluate(() => snapSavedForward(42)), false,
      'an unrelated committed write does not satisfy the pending Snap save');
    await page.keyboard.press('Enter');
    await page.waitForFunction(() => snapSavedForward(42)).catch(async error => {
      const written = await page.evaluate(count => snapHistoryWrites.slice(count)
        .map(value => String(value).replace(/data:image[^"]*/g, 'data:image…').slice(0, 400)), historyBeforeLanguage);
      throw new Error(`the Snap edit was not persisted; target writes: ${JSON.stringify(written)}`, {cause: error});
    });
    const persisted = await page.evaluate(() => snapHistoryWrites.at(-1));
    assert(persisted.includes('SnapModel'), 'native edits persist a typed model');
    assert(!persisted.includes('data:image/png;base64,'), 'generated previews are absent from database history');
    // A language switch must also flush a numeric field still being edited.
    await editForward(91, false);
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').evaluate(flag => flag.click());
    await page.waitForFunction(() => snapLanguageEditor === germanIde && snapLanguageEditor.currentSprite.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'move %n steps' && Number(m.inputs()[0].evaluate()) === 91));
    await page.locator('.fullscreen-close-button').click();
    await page.locator('.select-language-line > div:has(.flag-fill-german-red)').click();
    await page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first().click();
    await page.waitForFunction(() => snapLanguageEditor.currentSprite.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'gehe %n Schritte' && Number(m.inputs()[0].evaluate()) === 91));

    const editor = page.locator('dialog[open] .eva-editor');
    await editForward(92, false);
    await editor.getByRole('tab',{name:'Python',exact:true}).click();
    const code = editor.locator('.cm-content');
    await code.waitFor();
    assert.match(await code.innerText(), /forward\(92\)/, 'tab conversion includes the pending Snap input');
    // The whole program is replaced, so the Python tab shows it in full rather than its main function.
    await editor.getByRole('tab', {name:/^(Full Mode|Gesamtmodus)$/}).click();
    await code.fill('forward(123)\nforward(17)\n');
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').evaluate(flag => flag.click());
    assert.match(await code.innerText(), /forward\(123\)\s+(?:turtle\.)?forward\(17\)/);
    await editor.getByRole('tab',{name:'Snap!',exact:true}).click();
    await page.waitForFunction(() => {
      const blocks = snapLanguageEditor.currentSprite.scripts.allChildren().filter(m => m.selector === 'forward');
      return [123,17].every(value => blocks.some(m => m.blockSpec === 'move %n steps' && Number(m.inputs()[0].evaluate()) === value));
    });
    await page.locator('.select-language-line > div:has(.flag-fill-german-red)').evaluate(flag => flag.click());
    await page.waitForFunction(() => snapLanguageEditor.currentSprite.scripts.allChildren()
      .some(m => m.selector === 'forward' && m.blockSpec === 'gehe %n Schritte' && Number(m.inputs()[0].evaluate()) === 123));
    await editor.getByRole('tab',{name:'Python',exact:true}).click();
    assert.match(await code.innerText(), /forward\(123\)\s+(?:turtle\.)?forward\(17\)/);
    await page.locator('.fullscreen-close-button').click();
    await page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first().click();
    assert.match(await code.innerText(), /forward\(123\)\s+(?:turtle\.)?forward\(17\)/);
    await editor.getByRole('tab',{name:'Java',exact:true}).click();
    await editor.getByRole('tab', {name: germanEditorLabels.javaModeFull,exact:true}).click();
    await code.fill('public class Drawing {\n  static void line(int size) { Turtle.forward(size); }\n  public static void main(String[] args) { line(64); }\n}\n');
    await editor.getByRole('tab',{name:'Snap!',exact:true}).click();
    await page.waitForFunction(() => {
      const xml = window.snapLanguageEditor?.getProjectXML();
      return xml?.includes('line') && xml.includes('64') && xml.includes('forward');
    });
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').evaluate(flag => flag.click());
    await page.waitForFunction(() => SnapTranslator.language === 'en');
    await editor.getByRole('tab',{name:'Java',exact:true}).click();
    await editor.getByRole('tab', {name: 'Full Mode',exact:true}).click();
    assert.match(await code.innerText(), /void line\((?:int|double) size\)/);
    assert.match(await code.innerText(), /line\(64\)/);
    await page.locator('.fullscreen-close-button').click();
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});
