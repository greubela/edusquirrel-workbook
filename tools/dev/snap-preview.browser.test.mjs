import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

test('native Snap edits retain Java method categories and allow a safe return to Java', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}});
    page.setDefaultTimeout(60000);
    const errors = [];
    const prefix = '/edusquirrel-workbook/';
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin || !url.pathname.startsWith(prefix)) return route.abort();
      const relative = decodeURIComponent(url.pathname.slice(prefix.length));
      let file = path.resolve(root, relative + (relative.endsWith('/') ? 'index.html' : ''));
      if (!file.startsWith(root + path.sep)) return route.abort();
      if (relative === 'homepage/js/CodeMirrorLoader.js') file = process.env.CODEMIRROR_TEST_BUNDLE;
      try {
        let body = await fs.readFile(file);
        if (relative.endsWith('/turtlestitchsrc/gui.js')) body = Buffer.from(body.toString() + `
          window.snapCategoryIdes = [];
          (() => {
            const open = IDE_Morph.prototype.rawOpenProjectString;
            IDE_Morph.prototype.rawOpenProjectString = function(...args) {
              const result = open.apply(this,args);
              snapCategoryIdes.push(this);
              return result;
            };
          })();`);
        await route.fulfill({body,contentType:mime[path.extname(file)] || 'application/octet-stream'});
      } catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await page.goto(origin + prefix + 'homepage/workbookDesign/');
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.section-block').first().click();
    const open = page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first();
    await open.click();
    const editor = page.locator('dialog[open] .eva-editor');
    const java = editor.getByRole('button',{name:'Java',exact:true});
    await java.click();
    await editor.getByRole('button',{name:'Full source',exact:true}).click();
    for (const fixture of [
      {source:'public class Drawing {\n public static void main(String[] args) { Turtle.forward(30); }\n}\n',method:'main',selector:'doJavaForward',before:30,after:47},
      {source:'class Branching {\n static void draw(int depth, double length) {\n  if (depth <= 0) { Turtle.forward(length); return; }\n  draw(depth - 1, length / 2.0);\n  Turtle.turnRight(45);\n }\n public static void main(String[] args) { draw(3, 16.0); }\n}\n',method:'draw',selector:'doJavaTurnRight',before:45,after:55}
    ]) {
      await editor.locator('.cm-content').fill(fixture.source);
      await editor.getByRole('button',{name:'Snap!',exact:true}).click();
      await page.waitForFunction(() => {
        const canvas = document.querySelector('dialog[open] .be-program-snap-renderer--editor canvas');
        return window.snapCategoryIdes?.some(ide => ide.world()?.worldCanvas === canvas && ide.stage?.globalBlocks.some(block => block.spec === 'main'));
      });
      const edited = await page.evaluate(fixture => {
        const canvas = document.querySelector('dialog[open] .be-program-snap-renderer--editor canvas');
        const ide = snapCategoryIdes.findLast(ide => ide.world()?.worldCanvas === canvas);
        const methods = ide.stage.globalBlocks;
        const palette = ide.palette.contents.children;
        const visible = methods.map(definition => ({spec:definition.spec,category:definition.category,
          count:palette.filter(block => block.isCustomBlock && block.definition === definition).length}));
        const template = palette.find(block => block.isCustomBlock && block.definition === methods[0]);
        const count = () => ide.palette.contents.children.filter(block => block.isCustomBlock && block.definition === methods[0]).length;
        ide.currentSprite.changeBlockVisibility(template,true);
        const hiddenCount = count();
        ide.currentSprite.changeBlockVisibility(template,false);
        const restoredCount = count();
        const metadata = xml => [...new DOMParser().parseFromString(xml,'application/xml').querySelectorAll('notes')]
          .map(note => note.textContent.replace(/\r\n?/g,'\n')).filter(text => text.startsWith('EduSquirrel Java Turtle 1\n'));
        const beforeNotes = metadata(ide.getProjectXML());
        const method = methods.find(definition => definition.spec.split(' ')[0] === fixture.method);
        let command;
        method.body.expression.forAllChildren(block => {if (block.selector === fixture.selector) command = block;});
        if (!command) throw Error('Missing native Turtle command');
        const before = command.inputs()[0].contents().text;
        command.inputs()[0].setContents(String(fixture.after));
        const xml = ide.getProjectXML();
        document.querySelector('.fullscreen-close-button').click();
        return {visible,before,hiddenCount,restoredCount,metadataUnchanged:JSON.stringify(beforeNotes) === JSON.stringify(metadata(xml))};
      }, fixture);
      assert.equal(edited.before, String(fixture.before));
      assert(edited.visible.length > 0);
      assert(edited.visible.every(method => method.category === 'variables' && method.count === 1), JSON.stringify(edited.visible));
      assert.equal(edited.hiddenCount, 0);
      assert.equal(edited.restoredCount, 1);
      assert.equal(edited.metadataUnchanged, true);
      await open.click();
      assert.equal(await java.isDisabled(), false, 'a native edit must not invalidate the Java bridge');
      await java.click();
      const code = await editor.locator('.cm-content').innerText();
      assert.match(code, new RegExp(`Turtle\\.${fixture.selector === 'doJavaForward' ? 'forward' : 'turnRight'}\\(${fixture.after}\\)`));
      if (fixture.method === 'draw') assert.match(code, /draw\(\(depth - 1\), \(length \/ 2\.0\)\)/);
    }
    await editor.getByRole('button',{name:'Snap!',exact:true}).click();
    await page.waitForFunction(() => {
      const canvas = document.querySelector('dialog[open] .be-program-snap-renderer--editor canvas');
      return snapCategoryIdes.some(ide => ide.world()?.worldCanvas === canvas);
    });
    await page.evaluate(() => {
      const canvas = document.querySelector('dialog[open] .be-program-snap-renderer--editor canvas');
      const ide = snapCategoryIdes.findLast(ide => ide.world()?.worldCanvas === canvas);
      ide.stage.globalBlocks.find(block => block.spec === 'main').category = 'control';
      document.querySelector('.fullscreen-close-button').click();
    });
    await open.click();
    assert.equal(await java.isDisabled(), true, 'authored category changes still prevent a lossy Java conversion');
    await page.locator('.fullscreen-close-button').click();
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});

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
    await open.click();
    await page.locator('dialog[open] .be-program-snap-renderer__canvas').waitFor();
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

test('unchanged Java block previews retain their canvas and changed or unfinished drafts replace it', async () => {
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
            const open = IDE_Morph.prototype.rawOpenProjectString;
            IDE_Morph.prototype.rawOpenProjectString = function(xml,...args) {
              window.snapPreviewLoadedXml = xml;
              return open.call(this,xml,...args);
            };
          })();`);
        await route.fulfill({body,contentType:mime[path.extname(file)] || 'application/octet-stream'});
      } catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await page.goto(origin + prefix + 'homepage/workbookDesign/');
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.section-block').first().click();
    await page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first().click();
    const editor = page.locator('dialog[open] .eva-editor');
    await editor.getByRole('button',{name:'Java',exact:true}).click();
    await editor.getByRole('button',{name:'Full source',exact:true}).click();
    await editor.locator('.cm-content').waitFor();
    const preview = page.locator('.be-program-snap-renderer:not(dialog *)').first();
    const source = 'public class Drawing {\n  public static void main(String[] args) {\n    Turtle.forward(20);\n  }\n}\n';
    async function writeSource(code) {
      await page.evaluate(code => {
        const view = EduSquirrelCodeMirrorLibraries.EditorView.findFromDOM(document.querySelector('dialog[open] .cm-editor'));
        view.dispatch({changes:{from:0,to:view.state.doc.length,insert:code}});
      }, code);
      await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
      assert.equal(await page.evaluate(() => EduSquirrelCodeMirrorLibraries.EditorView
        .findFromDOM(document.querySelector('dialog[open] .cm-editor')).state.doc.toString()), code);
    }
    async function rememberPreview() {
      await page.waitForFunction(() => {
        const canvas = document.querySelector('.be-program-snap-renderer__canvas:not(dialog *)');
        return canvas && canvas.width > 1 && canvas.height > 1;
      });
      await page.evaluate(() => {
        globalThis.previewReuseProbe = {
          canvas:document.querySelector('.be-program-snap-renderer__canvas:not(dialog *)'),
          worlds:snapPreviewWorlds,pictures:snapPreviewPictures
        };
        previewReuseProbe.xml = snapPreviewLoadedXml;
      });
    }
    await writeSource(source);
    await rememberPreview();
    for (const suffix of [' ', '\n\t', '\n\n']) {
      await writeSource(source + suffix);
      assert.deepEqual(await page.evaluate(() => ({
        retained:previewReuseProbe.canvas === document.querySelector('.be-program-snap-renderer__canvas:not(dialog *)'),
        worlds:snapPreviewWorlds - previewReuseProbe.worlds,pictures:snapPreviewPictures - previewReuseProbe.pictures
      })), {retained:true,worlds:0,pictures:0});
    }
    const changed = source.replace('forward(20)', 'forward(30)');
    await writeSource(changed);
    assert(await page.evaluate(() => previewReuseProbe.canvas !== document.querySelector('.be-program-snap-renderer__canvas:not(dialog *)') &&
      snapPreviewWorlds > previewReuseProbe.worlds && snapPreviewPictures > previewReuseProbe.pictures));
    assert(await page.evaluate(() => previewReuseProbe.xml !== snapPreviewLoadedXml &&
      new DOMParser().parseFromString(snapPreviewLoadedXml,'application/xml')
        .querySelector('block-definition[s="main"] block[s="doJavaForward"] > l')?.textContent === '30'),
      'the regenerated preview loads the changed method body');
    await rememberPreview();
    await writeSource(changed.slice(0, changed.lastIndexOf('}')));
    assert.equal(await preview.locator('canvas').count(), 0);
    assert.match(await preview.textContent(), /Preview unavailable for this draft/);
    await writeSource(changed);
    assert(await page.evaluate(() => previewReuseProbe.canvas !== document.querySelector('.be-program-snap-renderer__canvas:not(dialog *)') &&
      snapPreviewWorlds > previewReuseProbe.worlds && snapPreviewPictures > previewReuseProbe.pictures));
    assert(await page.evaluate(() => previewReuseProbe.xml === snapPreviewLoadedXml),
      'restoring the same valid source reloads its complete block program');
    await rememberPreview();
    assert.equal(await preview.locator('[role=alert]').count(), 0);
    await page.locator('.fullscreen-close-button').click();
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});
