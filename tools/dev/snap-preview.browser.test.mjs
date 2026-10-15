import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

// A fast cached client can start while Snap's sequential script loader is still fetching.
// Exercise the actual Scala.js previews and retained fullscreen editor in that order.
test('Snap previews and editor wait for the runtime and ignore closed mounts', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(), chromiumSandbox:true});
  try {
    const page = await browser.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    let release;
    const gate = new Promise(resolve => {release = resolve;});
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      if (url.pathname.endsWith('/adjusted/adjustedMorphic.js')) await gate;
      let file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
      if (!file.startsWith(root + path.sep)) return route.abort();
      if (url.pathname === '/homepage/js/CodeMirrorLoader.js') file = process.env.CODEMIRROR_TEST_BUNDLE;
      try {await route.fulfill({body:await fs.readFile(file),contentType:mime[path.extname(file)] || 'application/octet-stream'});}
      catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await page.goto(origin + '/homepage/workbookDesign/index.html', {waitUntil:'domcontentloaded'});
    await page.waitForFunction(() => document.querySelector('script[src$="adjusted/adjustedMorphic.js"]'));
    assert.equal(await page.locator('dialog[open]').count(), 0, 'Snap loading starts before an editor opens');
    assert.equal(await page.locator('script[src$="adjusted/adjustedMorphic.js"]').getAttribute('fetchpriority'), 'low');
    await page.locator('.section-register .login-area').nth(1).locator('button').click({timeout:60000});
    await page.locator('.section-block').first().click();
    const previews = page.locator('.be-program-snap-renderer__canvas:not(dialog *)');
    await previews.first().waitFor();
    assert.equal(await page.evaluate(() => typeof window.WorldMorph), 'undefined', 'runtime is deliberately incomplete');
    const open = page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first();
    const ownPreview = page.locator('.interaction-element:not(dialog *)').first();
    const otherPreview = page.locator('.interaction-element:not(dialog *)').nth(1);
    await open.click();
    await page.locator('dialog[open] .be-program-snap-renderer__canvas').waitFor();
    assert.equal(await ownPreview.locator('.be-program-snap-renderer__canvas').count(), 0,
      'opening a task unmounts its pending background preview');
    assert.equal(await otherPreview.locator('.be-program-snap-renderer__canvas').count(), 1,
      'another task keeps its pending preview mounted');
    await page.locator('.fullscreen-close-button').click();
    assert.deepEqual(errors, [], 'pending runtime must not throw from preview/editor mount');
    release();
    // Pixel coverage, rather than DOM presence, proves that scripts were actually drawn.
    const hasBlocks = canvas => {
      if (canvas.width <= 1 || canvas.height <= 1) return false;
      const data = canvas.getContext('2d').getImageData(0,0,canvas.width,canvas.height).data;
      const colors = new Set();
      for (let i=0;i<data.length;i+=4) {
        if (data[i+3]) colors.add(`${data[i]},${data[i+1]},${data[i+2]}`);
        if (colors.size > 8) return true;
      }
      return false;
    };
    await page.waitForFunction(hasBlocks, await previews.first().elementHandle());
    assert.equal(await page.locator('dialog[open]').count(), 0, 'late runtime does not reopen the editor');
    assert.equal(await previews.evaluateAll(canvases => canvases.filter(c => c.hasAttribute('style')).length), 0, 'preview presentation uses CSS');
    await open.click();
    const editor = page.locator('dialog[open] .be-program-snap-renderer__canvas');
    await page.waitForFunction(hasBlocks, await editor.elementHandle());
    await page.locator('.fullscreen-close-button').click();
    await open.click();
    await page.waitForFunction(hasBlocks, await editor.elementHandle());
    const sources = await page.locator('script[src*="20260212TurtleStitch/"]').evaluateAll(scripts =>
      scripts.filter(script => !script.src.endsWith('/fullturtle.js')).map(script => ({src:script.src,priority:script.getAttribute('fetchpriority')})));
    assert.equal(sources.length, 22);
    assert.equal(new Set(sources.map(script => script.src)).size, 22, 'editor reopening does not load the runtime again');
    assert(sources.every(script => script.priority === 'low'));
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});


test('Snap finishes startup preheating without creating a Morphic world or opening an editor', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true});
  try {
    const page = await browser.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      let file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
      if (!file.startsWith(root + path.sep)) return route.abort();
      if (url.pathname === '/homepage/js/CodeMirrorLoader.js') file = process.env.CODEMIRROR_TEST_BUNDLE;
      try {
        let body = await fs.readFile(file);
        if (url.pathname.endsWith('/adjusted/adjustedMorphic.js')) body = Buffer.from(body.toString() + `
          (() => {
            window.snapWorldCreations = 0;
            const init = WorldMorph.prototype.init;
            WorldMorph.prototype.init = function(...args) {
              window.snapWorldCreations++;
              return init.apply(this,args);
            };
          })();`);
        await route.fulfill({body,contentType:mime[path.extname(file)] || 'application/octet-stream'});
      } catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await page.goto(origin + '/homepage/workbookDesign/index.html', {waitUntil:'domcontentloaded'});
    await page.waitForFunction(() => !!(window.TurtleStitchPoC?._impl));
    assert.equal(await page.evaluate(() => window.snapWorldCreations), 0, 'warmup loads code without allocating GUI sessions');
    assert.equal(await page.locator('dialog[open], .be-program-snap-renderer__canvas').count(), 0);
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});

test('a task pauses only its own block preview and close restores the latest text or Snap edits', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true});
  try {
    const page = await browser.newPage();
    page.setDefaultTimeout(60000);
    const errors = [];
    const prefix = '/edusquirrel-workbook/';
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin || !url.pathname.startsWith(prefix)) return route.abort();
      const relative = decodeURIComponent(url.pathname.slice(prefix.length));
      if (relative.split('/').includes('..')) return route.abort();
      let file = path.resolve(root, relative + (relative.endsWith('/') ? 'index.html' : ''));
      if (relative === 'homepage/js/CodeMirrorLoader.js') file = process.env.CODEMIRROR_TEST_BUNDLE;
      try {
        let body = await fs.readFile(file);
        const probe = relative.endsWith('/adjusted/adjustedMorphic.js') ? ['WorldMorph', 'init', 'snapPreviewWorlds'] :
          relative.endsWith('/turtlestitchsrc/blocks.js') ? ['ScriptsMorph', 'scriptsPicture', 'snapPreviewPictures'] : null;
        if (probe) {
          const [type, method, counter] = probe;
          body = Buffer.from(body.toString() + `
            (() => {
              window.${counter} = 0;
              const original = ${type}.prototype.${method};
              ${type}.prototype.${method} = function(...args) {
                window.${counter}++;
                return original.apply(this,args);
              };
            })();`);
        }
        if (relative.endsWith('/turtlestitchsrc/gui.js')) body = Buffer.from(body.toString() + `
          (() => {
            window.snapPreviewOpened = [];
            const open = IDE_Morph.prototype.rawOpenProjectString;
            IDE_Morph.prototype.rawOpenProjectString = function(xml,...args) {
              window.snapPreviewLoadedXml = xml;
              const result = open.call(this,xml,...args);
              snapPreviewOpened.push({ide:this,xml});
              return result;
            };
          })();`);
        await route.fulfill({body,contentType:mime[path.extname(file)] || 'application/octet-stream'});
      } catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await page.goto(origin + prefix + 'homepage/workbookDesign/');
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.section-block').first().click();
    const tasks = page.locator('.interaction-element:not(dialog *)');
    const own = tasks.nth(0);
    const other = tasks.nth(1);
    const regular = tasks.nth(2);
    const open = page.getByRole('button',{name:/Editor.*öffnen|Open editor/i});
    await page.waitForFunction(() => document.querySelectorAll('.be-program-snap-renderer__canvas:not(dialog *)').length === 3 &&
      [...document.querySelectorAll('.be-program-snap-renderer__canvas:not(dialog *)')].every(canvas => canvas.width > 1));
    await other.locator('.be-program-snap-renderer__canvas').evaluate(canvas => {globalThis.otherTaskCanvas = canvas;});
    await open.first().click();
    const editor = page.locator('dialog[open] .eva-editor');
    await editor.getByRole('button',{name:'Java',exact:true}).click();
    await editor.getByRole('button',{name:'Full source',exact:true}).click();
    const code = editor.locator('.cm-content');
    await code.waitFor();
    const source = 'public class Drawing {\n  public static void main(String[] args) {\n    Turtle.forward(20);\n  }\n}\n';
    async function writeSource(code) {
      await editor.locator('.cm-content').fill(code);
      await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
      assert.equal(await page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
        .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString()), code);
    }
    async function rememberCounts() {
      await page.evaluate(() => {
        globalThis.previewPauseProbe = {worlds:snapPreviewWorlds,pictures:snapPreviewPictures};
      });
    }
    async function assertPaused() {
      assert.equal(await own.locator('.be-program-snap-renderer__canvas').count(), 0);
      assert(await other.locator('.be-program-snap-renderer__canvas').evaluate(canvas => canvas === otherTaskCanvas && canvas.isConnected),
        'another task retains its mounted preview');
      assert.deepEqual(await page.evaluate(() => ({worlds:snapPreviewWorlds - previewPauseProbe.worlds,
        pictures:snapPreviewPictures - previewPauseProbe.pictures})), {worlds:0,pictures:0},
      'typing in the fullscreen editor performs no background Morphic rendering');
    }
    const waitForForward = value => page.waitForFunction(value =>
      new DOMParser().parseFromString(snapPreviewLoadedXml,'application/xml')
        .querySelector('block-definition[s="main"] block[s="doJavaForward"] > l')?.textContent === String(value), value);

    await writeSource(source);
    await rememberCounts();
    await code.click();
    await page.keyboard.press(process.platform === 'darwin' ? 'Meta+End' : 'Control+End');
    await page.keyboard.type(' \t ');
    await assertPaused();
    const changed = source.replace('forward(20)', 'forward(30)');
    await writeSource(changed);
    await assertPaused();
    const unfinished = changed.slice(0, changed.lastIndexOf('}'));
    await writeSource(unfinished);
    await assertPaused();
    await page.locator('.fullscreen-close-button').click();
    assert.equal(await own.locator('.be-program-snap-renderer__canvas').count(), 0);
    assert.match(await own.locator('.be-program-snap-renderer').textContent(), /Preview unavailable for this draft/);
    await open.first().click();
    await code.waitFor();
    assert.equal(await page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
      .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString()), unfinished);
    await writeSource(changed);
    assert.equal(await own.locator('.be-program-snap-renderer__canvas').count(), 0);
    await page.locator('.fullscreen-close-button').click();
    await waitForForward(30);
    await own.locator('.be-program-snap-renderer__canvas').waitFor();
    await own.locator('.be-program-snap-renderer__canvas').evaluate(canvas => {globalThis.closedTaskCanvas = canvas;});

    await open.nth(2).click();
    assert.equal(await regular.locator('.be-program-snap-renderer__canvas').count(), 0,
      'regular programming exercises also pause their own preview');
    assert(await own.locator('.be-program-snap-renderer__canvas').evaluate(canvas => canvas === closedTaskCanvas && canvas.isConnected),
      'opening a different editor leaves this unchanged block preview intact');
    await page.locator('.fullscreen-close-button').click();
    await regular.locator('.be-program-snap-renderer__canvas').waitFor();

    await open.first().click();
    await editor.getByRole('button',{name:'Snap!',exact:true}).click();
    await page.waitForFunction(() => {
      const canvas = document.querySelector('dialog[open] .be-program-snap-renderer--editor canvas.be-program-snap-renderer__canvas');
      return snapPreviewOpened.some(({ide}) => ide.world?.()?.worldCanvas === canvas && ide.stage?.globalBlocks.some(definition => definition.spec === 'main'));
    });
    const snapMetadata = await page.evaluate(() => {
      const canvas = document.querySelector('dialog[open] .be-program-snap-renderer--editor canvas.be-program-snap-renderer__canvas');
      const {ide} = snapPreviewOpened.findLast(({ide}) => ide.world?.()?.worldCanvas === canvas);
      const metadata = [...new Set([...new DOMParser().parseFromString(ide.getProjectXML(),'application/xml')
        .querySelectorAll('notes')].map(notes => notes.textContent.replace(/\r\n?/g,'\n')).filter(text => text.startsWith('EduSquirrel Java Turtle 1\n')))];
      const main = ide.stage.globalBlocks.find(definition => definition.spec === 'main');
      let forward;
      main.body.expression.forAllChildren(block => {if (block.selector === 'doJavaForward') forward = block;});
      if (!forward) throw new Error('The native Snap main block must contain the Java forward command.');
      forward.inputs()[0].setContents('47');
      document.querySelector('.fullscreen-close-button').click();
      return metadata;
    });
    assert.equal(snapMetadata.length, 1, 'the native Snap project retains its Java Turtle metadata');
    await waitForForward(47);
    assert.deepEqual(await page.evaluate(() => [...new Set([...new DOMParser().parseFromString(snapPreviewLoadedXml,'application/xml')
      .querySelectorAll('notes')].map(notes => notes.textContent.replace(/\r\n?/g,'\n')).filter(text => text.startsWith('EduSquirrel Java Turtle 1\n')))]), snapMetadata,
    'close flushes the native Snap edit without losing its project metadata');
    await own.locator('.be-program-snap-renderer__canvas').waitFor();
    await open.first().click();
    const reopenedSnap = await page.waitForFunction(() => {
      const canvas = document.querySelector('dialog[open] .be-program-snap-renderer--editor canvas.be-program-snap-renderer__canvas');
      const opened = snapPreviewOpened.findLast(({ide}) => ide.world?.()?.worldCanvas === canvas);
      const main = opened?.ide.stage?.globalBlocks.find(definition => definition.spec === 'main');
      if (!main) return null;
      let forward;
      main.body.expression.forAllChildren(block => {if (block.selector === 'doJavaForward') forward = block;});
      const input = String(forward?.inputs()[0].contents().text);
      if (input !== '47') return null;
      const metadata = [...new Set([...new DOMParser().parseFromString(opened.ide.getProjectXML(),'application/xml')
        .querySelectorAll('notes')].map(notes => notes.textContent.replace(/\r\n?/g,'\n')).filter(text => text.startsWith('EduSquirrel Java Turtle 1\n')))];
      return {input,metadata};
    });
    assert.deepEqual(await reopenedSnap.jsonValue(), {input:'47',metadata:snapMetadata},
    'the next native Snap editor reads the flushed input and unchanged metadata from the bound source');
    await page.locator('.fullscreen-close-button').click();
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});
