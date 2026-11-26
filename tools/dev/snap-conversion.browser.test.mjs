import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const block = (selector, inputs = '') => `<block s="${selector}">${inputs}</block>`;
const literal = value => `<l>${value}</l>`;
const variable = name => `<block var="${name}"/>`;
const script = body => `<script>${body}</script>`;
const forward = input => block('forward', input);
const project = (body, definitions = '', variables = '', detached = '') =>
  `<project name="native-corpus" app="TurtleStitch 2.11" version="2"><notes/><scenes select="1"><scene name="native-corpus"><notes/>${definitions ? '<palette><category name="Variables" color="243,118,29,1"/></palette>' : ''}<hidden/><headers/><code/><blocks>${definitions}</blocks><primitives/><stage name="Stage" width="480" height="360" costume="0" tempo="60" color="255,255,255,1"><costumes><list/></costumes><sounds><list/></sounds><variables/><blocks/><scripts/><sprites select="1"><sprite name="Sprite" x="0" y="0" heading="90" scale="1" rotation="1" costume="0" color="0,0,0,1"><costumes><list/></costumes><sounds><list/></sounds><variables/><blocks/><scripts>${script(block('receiveGo') + body)}${detached}</scripts></sprite></sprites></stage><variables>${variables}</variables></scene></scenes></project>`;
// As the bundled TurtleStitch saves definitions: plain `%name` inputs and a category its palette declares.
const customDefinitions = `<block-definition s="step %distance" type="command" category="Variables"><header></header><code></code><translations></translations><inputs><input type="%n"></input></inputs>${script(forward(variable('distance')))}</block-definition>` +
  `<block-definition s="twice %value" type="reporter" category="Variables"><header></header><code></code><translations></translations><inputs><input type="%n"></input></inputs>${script(block('doReport', block('reportVariadicProduct', `<list>${variable('value')}${literal(2)}</list>`)))}</block-definition>`;
const cases = [
  {name:'nested loops', xml:project(block('doRepeat', literal(2) + script(block('doRepeat', literal(3) + script(forward(literal(7)))) + block('turn', literal(90)))))},
  {name:'variables and arithmetic', xml:project(block('doSetVar', literal('steps') + literal(4)) + block('doRepeat', literal(3) + script(forward(variable('steps')) + block('doChangeVar', literal('steps') + literal(2)))), '', '<variable name="steps"><l>4</l></variable>')},
  {name:'custom command and reporter', xml:project('<custom-block s="step %n"><custom-block s="twice %n"><l>4</l></custom-block></custom-block>', customDefinitions)},
  {name:'detached script', xml:project(forward(literal(11)), '', '', script(forward(literal(999))))},
  {name:'empty numeric input', xml:project(forward(literal('')))},
  {name:'unsupported native block', xml:project(forward(literal(3)) + block('doThinkFor', literal('hello') + literal(0))), unsupported:true}
];

function result(reply) {
  function find(value) {
    if (!value || typeof value !== 'object') return;
    if (typeof value.json === 'string' && typeof value.classSimpleName === 'string') return JSON.parse(value.json);
    for (const child of Object.values(value)) { const found = find(child); if (found !== undefined) return found; }
  }
  const value = find(reply.response?.result);
  assert(value, JSON.stringify(reply));
  return value;
}

// Plain TurtleStitch as tools/dev/generate-native-snap-fixtures.mjs loads it: the original editor, not our
// exercise-configured one. Each fixture gets a fresh page, since one editor reused across loads alters later
// custom definitions.
const base = '/resources/programs/20260212TurtleStitch/';
const snapScripts = ['adjusted/adjustedMorphic.js', 'turtlestitchsrc/symbols.js', 'turtlestitchsrc/widgets.js',
  'turtlestitchsrc/blocks.js', 'turtlestitchsrc/threads.js', 'adjusted/adjustedObjects.js',
  'turtlestitchsrc/scenes.js', 'turtlestitchsrc/gui.js', 'turtlestitchsrc/paint.js',
  'turtlestitchsrc/lists.js', 'turtlestitchsrc/byob.js', 'turtlestitchsrc/tables.js',
  'turtlestitchsrc/sketch.js', 'turtlestitchsrc/video.js', 'turtlestitchsrc/maps.js',
  'turtlestitchsrc/extensions.js', 'turtlestitchsrc/xml.js', 'turtlestitchsrc/store.js',
  'turtlestitchsrc/locale.js', 'turtlestitchsrc/cloud.js', 'turtlestitchsrc/api.js',
  'turtlestitchsrc/embroider.js'];
const corpusPage = `<!doctype html><html><head><meta charset="utf-8"></head><body><canvas id="world" width="1200" height="800"></canvas>
<script>window.base_prog_dir = '${base}';</script>
${snapScripts.map(script => `<script src="${base}${script}"></script>`).join('\n')}</body></html>`;

test('native Snap exports retain execution behavior through shared parsing and Python conversion', async () => {
  const browser = await chromium.launch({chromiumSandbox:true, executablePath:process.env.CHROMIUM_PATH || chromium.executablePath()});
  try {
    const page = await browser.newPage();
    page.setDefaultTimeout(30000);
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      if (url.pathname === '/corpus.html') return route.fulfill({contentType:'text/html; charset=utf-8', body:corpusPage});
      const file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
      if (!file.startsWith(root + path.sep)) return route.abort();
      try {
        await route.fulfill({body:await readFile(file),contentType:mime[path.extname(file)] || 'application/octet-stream'});
      } catch {await route.fulfill({status:404,body:'Not found'});}
    });
    for (const fixture of cases) {
      await page.goto(origin + '/corpus.html');
      await page.waitForFunction(() => typeof IDE_Morph === 'function' && typeof SnapSerializer === 'function');
      await page.evaluate(() => {
        const worker = new Worker('/artifacts/newest/worker.js');
        const pending = new Map();
        worker.onmessage = event => {const reply = JSON.parse(event.data); const waiter = pending.get(reply.requestId); if (waiter) {pending.delete(reply.requestId); waiter.resolve(reply);}};
        worker.onerror = event => {for (const waiter of pending.values()) waiter.reject(new Error(event.message)); pending.clear();};
        window.corpusRequest = (requestId, state) => new Promise((resolve,reject) => {
          pending.set(requestId,{resolve,reject}); worker.postMessage(JSON.stringify({requestId,command:{name:'worker.programAnalysis',json:{state,environment:{libraries:['Turtle','System','Web','NumberList','Sudoku']}}}}));
        });
      });
      const native = await page.evaluate(async ({name, xml}) => {
        const world = new WorldMorph(document.getElementById('world'), false);
        const ide = new IDE_Morph({noAutoFill: true, noUserSettings: true, noSprites: false});
        ide.openIn(world);
        for (let i = 0; i < 5; i++) world.doOneCycle();
        // openProjectString opens on a later step; the raw call replaces the project before it is read and run.
        ide.rawOpenProjectString(xml);
        for (let i = 0; i < 3; i++) world.doOneCycle();
        const sprite = ide.currentSprite;
        const trace = [];
        for (const [method, command] of [['forward','Forward'],['turn','Right'],['turnLeft','Left']]) {
          const original = sprite[method];
          sprite[method] = function(value) {trace.push([command, Number(value)]); return original.call(this,value);};
        }
        const errors = [];
        const handleError = Process.prototype.handleError;
        Process.prototype.handleError = function(error, element) {
          errors.push(`${error?.message ?? error} (in ${element?.selector ?? 'block'})`);
          return handleError.call(this, error, element);
        };
        ide.scene.captureGlobalSettings();
        const exported = ide.serializer.serialize(new Project(ide.scenes, ide.scene));
        const hat = sprite.scripts.children.find(child => child.selector === 'receiveGo');
        const threads = ide.stage.threads;
        const process = threads.startProcess(hat,sprite);
        try {
          for (let tick=0;tick<500 && threads.processes.includes(process);tick++) {
            threads.step(); await new Promise(resolve => setTimeout(resolve,0));
          }
        } finally { Process.prototype.handleError = handleError; }
        if (process.errorFlag || threads.processes.includes(process))
          throw new Error(`Native Snap did not complete '${name}': ${process.errorFlag ? `the script raised ${JSON.stringify(errors)}` : 'still running after 500 steps'}; trace ${JSON.stringify(trace)}`);
        return {xml:exported,trace};
      }, fixture);
      const snap = result(await page.evaluate(({id,code}) => corpusRequest(id,{$type:'Raw',value:{$type:'ProgrammingStateRaw',language:'Snap',code}}), {id:fixture.name,code:native.xml}));
      if (fixture.unsupported) {
        assert(snap.unsupportedSource.length > 0 || snap.turtle[0] === 0, fixture.name);
        assert.equal(snap.python[0], 0, fixture.name);
        continue;
      }
      assert.equal(snap.turtle[0],1, JSON.stringify(snap));
      const commands = analysis => analysis.turtle[1].commands.map(command => [command.$type.split('.').at(-1), ...Object.entries(command).filter(([key]) => key !== '$type').map(([,value]) => value)]);
      assert.deepEqual(commands(snap),native.trace,fixture.name);
      assert.equal(snap.python[0],1, JSON.stringify(snap));
      const python = result(await page.evaluate(({id,code}) => corpusRequest(id,{$type:'Raw',value:{$type:'ProgrammingStateRaw',language:'Python',code}}), {id:fixture.name+'-python',code:snap.python[1]}));
      assert.deepEqual(commands(python),native.trace,`${fixture.name} via Python:\n${snap.python[1]}\nnative export:\n${native.xml}`);
    }
  } finally {await browser.close();}
});
