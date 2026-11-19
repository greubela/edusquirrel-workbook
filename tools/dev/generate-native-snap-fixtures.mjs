// Regenerates the native Snap fixtures of NativeSnapFormatSpec: each input project is loaded into the bundled
// TurtleStitch editor, with the extra primitives our editor injects, and saved again by its own serializer.
// Inputs come from `NativeSnapFixtures` (see its scaladoc). Usage:
//   node tools/dev/generate-native-snap-fixtures.mjs <input directory> <output directory>
// CHROMIUM_PATH selects the browser; CHROMIUM_NO_SANDBOX=1 runs it unsandboxed (needed only when running as root).
import {chromium} from 'playwright';
import fs from 'node:fs/promises';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '../..');
const [inputDir, outputDir] = process.argv.slice(2);
const origin = 'http://localhost:9000';
const base = '/resources/programs/20260212TurtleStitch/';
const scripts = ["adjusted/adjustedMorphic.js", "turtlestitchsrc/symbols.js", "turtlestitchsrc/widgets.js",
  "turtlestitchsrc/blocks.js", "turtlestitchsrc/threads.js", "adjusted/adjustedObjects.js",
  "turtlestitchsrc/scenes.js", "turtlestitchsrc/gui.js", "turtlestitchsrc/paint.js",
  "turtlestitchsrc/lists.js", "turtlestitchsrc/byob.js", "turtlestitchsrc/tables.js",
  "turtlestitchsrc/sketch.js", "turtlestitchsrc/video.js", "turtlestitchsrc/maps.js",
  "turtlestitchsrc/extensions.js", "turtlestitchsrc/xml.js", "turtlestitchsrc/store.js",
  "turtlestitchsrc/locale.js", "turtlestitchsrc/cloud.js", "turtlestitchsrc/api.js",
  "turtlestitchsrc/embroider.js"];
const html = `<!doctype html><html><head><meta charset="utf-8"></head><body><canvas id="world" width="1200" height="800"></canvas>
<script>window.base_prog_dir = '${base}';</script>
${scripts.map(s => `<script src="${base}${s}"></script>`).join('\n')}</body></html>`;

const browser = await chromium.launch({executablePath: process.env.CHROMIUM_PATH || chromium.executablePath(),
  chromiumSandbox: process.env.CHROMIUM_NO_SANDBOX !== '1'});
const extras = JSON.parse(await fs.readFile(path.join(inputDir, 'extra-primitives.json'), 'utf8'));
const page = await browser.newPage();
const errors = [];
page.on('pageerror', e => errors.push(e.message));
await page.route('**/*', async route => {
  const url = new URL(route.request().url());
  if (url.origin !== origin) return route.abort();
  if (url.pathname === '/generate.html') return route.fulfill({contentType: 'text/html; charset=utf-8', body: html});
  const file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
  if (!file.startsWith(root + path.sep)) return route.abort();
  try { return route.fulfill({body: await fs.readFile(file), contentType: file.endsWith('.js') ? 'text/javascript; charset=utf-8' : undefined}); }
  catch { return route.fulfill({status: 404, body: ''}); }
});
/** A fresh page and editor for every project: one editor reused across loads alters later custom definitions. */
async function freshEditor() {
  await page.goto(origin + '/generate.html');
  await page.waitForFunction(() => typeof IDE_Morph === 'function' && typeof SnapSerializer === 'function');
  await page.evaluate(extras => {
    // As SnapExtraPrimitives.install: inject now and after every primitive-table rebuild (each project load).
    SpriteMorph.prototype.blockColor.embroidery ??= new Color(0, 120, 0);
    const inject = () => extras.forEach(extra => {
      SpriteMorph.prototype.blocks[extra.selector] = {type: 'command', category: extra.category, spec: extra.spec,
        only: SpriteMorph, ...(extra.defaults.length ? {defaults: extra.defaults} : {})};
    });
    const initBlocks = SpriteMorph.prototype.initBlocks;
    SpriteMorph.prototype.initBlocks = function (...args) { const value = initBlocks.apply(this, args); inject(); return value; };
    SpriteMorph.prototype.initBlocks();
    window.world = new WorldMorph(document.getElementById('world'), false);
    window.ide = new IDE_Morph({noAutoFill: true, noUserSettings: true, noSprites: false});
    ide.openIn(world);
    for (let i = 0; i < 5; i++) world.doOneCycle();
    window.exportProject = () => {
      ide.scene.captureGlobalSettings();
      return ide.serializer.serialize(new Project(ide.scenes, ide.scene));
    };
    window.reload = xml => { ide.rawOpenProjectString(xml); for (let i = 0; i < 3; i++) world.doOneCycle(); return exportProject(); };
  }, extras);
}

await fs.mkdir(outputDir, {recursive: true});
for (const name of (await fs.readdir(inputDir)).filter(n => n.endsWith('.xml')).sort()) {
  const xml = await fs.readFile(path.join(inputDir, name), 'utf8');
  try {
    await freshEditor();
    const exported = await page.evaluate(source => reload(source), xml);
    await fs.writeFile(path.join(outputDir, name), exported);
    console.log('exported', name, exported.length);
  } catch (e) { console.log('FAILED', name, e.message.slice(0, 300)); }
}

// One project assembled through the editor's own objects, covering vocabulary that converted programs do not use.
await freshEditor();
const featureResult = await page.evaluate(() => {
  const steps = [];
  const step = (label, run) => { try { run(); steps.push('ok ' + label); } catch (e) { steps.push('FAILED ' + label + ': ' + e.message); } };
  ide.rawOpenProjectString(`<project name="features" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes>Feature notes</notes><scenes select="1"><scene name="features"><stage name="Stage" width="480" height="360"><sprites select="1"><sprite name="Turtle" idx="1" x="0" y="0" heading="90" scale="1"><scripts><script x="20" y="20"><block s="receiveGo"/><block s="forward"><l>10</l></block></script></scripts></sprite></sprites></stage></scene></scenes></project>`);
  const sprite = ide.currentSprite, stage = ide.stage, scene = ide.scene;
  step('rotation style and pen point', () => { sprite.rotationStyle = 2; sprite.penPoint = 'center'; sprite.isDraggable = false; });
  step('global and sprite variables with lists', () => {
    ide.globalVariables.addVar('score'); ide.globalVariables.setVar('score', 7);
    sprite.variables.addVar('path'); sprite.variables.setVar('path', new List([1, 'two', new List(['nested'])]));
    const linked = new List(); linked.isLinked = true; linked.first = 'head'; linked.rest = new List(['tail']);
    sprite.variables.addVar('linked'); sprite.variables.setVar('linked', linked);
  });
  step('variable watchers', () => {
    sprite.toggleVariableWatcher('score', true);
    const watcher = sprite.findVariableWatcher('score'); if (watcher) { watcher.style = 'large'; watcher.fixLayout(); }
  });
  step('global reporter, predicate and hat definitions with inputs', () => {
    const reporter = new CustomBlockDefinition('double %n'); reporter.type = 'reporter'; reporter.category = 'operators'; reporter.isGlobal = true;
    reporter.declarations.set('n', ['%n', '2', undefined, false, false]);
    const predicate = new CustomBlockDefinition('is big %x'); predicate.type = 'predicate'; predicate.category = 'other'; predicate.isGlobal = true;
    predicate.declarations.set('x', ['%s', '', undefined, true, false]);
    const hat = new CustomBlockDefinition('when ready'); hat.type = 'hat'; hat.category = 'control'; hat.isGlobal = true; hat.semantics = 'rule';
    stage.globalBlocks.push(reporter, predicate, hat);
  });
  step('local command definition in the embroidery category', () => {
    const local = new CustomBlockDefinition('stitch twice'); local.type = 'command'; local.category = 'embroidery'; local.isGlobal = false;
    sprite.customBlocks.push(local);
  });
  step('script comment', () => { const comment = new CommentMorph('A note'); comment.setPosition(sprite.scripts.topLeft().add(200)); sprite.scripts.add(comment); });
  step('scene settings', () => { SpriteMorph.prototype.useFlatLineEnds = true; scene.penColorModel = 'hsl'; scene.unifiedPalette = true; scene.disableClickToRun = true; });
  step('second sprite', () => {
    const helper = new SpriteMorph(ide.globalVariables); helper.name = 'Helper'; helper.setPosition(stage.center());
    stage.add(helper); ide.sprites.add(helper); helper.rotationStyle = 0;
  });
  for (let i = 0; i < 3; i++) world.doOneCycle();
  return {steps, xml: exportProject()};
});
featureResult.steps.forEach(s => console.log(s));
await fs.writeFile(path.join(outputDir, 'features.xml'), featureResult.xml);
console.log('exported features.xml', featureResult.xml.length);
if (errors.length) console.log('page errors:', errors.slice(0, 5));
await browser.close();
