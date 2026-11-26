import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
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
  `<project name="native-corpus" app="TurtleStitch 2.11" version="2"><notes/><scenes select="1"><scene name="native-corpus"><notes/><hidden/><headers/><code/><blocks>${definitions}</blocks><primitives/><stage name="Stage" width="480" height="360" costume="0" tempo="60" color="255,255,255,1"><costumes><list/></costumes><sounds><list/></sounds><variables/><blocks/><scripts/><sprites select="1"><sprite name="Sprite" x="0" y="0" heading="90" scale="1" rotation="1" costume="0" color="0,0,0,1"><costumes><list/></costumes><sounds><list/></sounds><variables/><blocks/><scripts>${script(block('receiveGo') + body)}${detached}</scripts></sprite></sprites></stage><variables>${variables}</variables></scene></scenes></project>`;
const customDefinitions = `<block-definition s="step %'distance'" type="command" category="motion"><inputs><input type="%n">0</input></inputs>${script(forward(variable('distance')))}</block-definition>` +
  `<block-definition s="twice %'value'" type="reporter" category="operators"><inputs><input type="%n">0</input></inputs>${script(block('doReport', block('reportProduct', variable('value') + literal(2))))}</block-definition>`;
const cases = [
  {name:'nested loops', xml:project(block('doRepeat', literal(2) + script(block('doRepeat', literal(3) + script(forward(literal(7)))) + block('turn', literal(90)))))},
  {name:'variables and arithmetic', xml:project(block('doSetVar', literal('steps') + literal(4)) + block('doRepeat', literal(3) + script(forward(variable('steps')) + block('doChangeVar', literal('steps') + literal(2)))), '', '<variable name="steps"><l>4</l></variable>')},
  {name:'custom command and reporter', xml:project('<custom-block s="step %n"><custom-block s="twice %n"><l>4</l></custom-block></custom-block>', customDefinitions)},
  {name:'detached script', xml:project(forward(literal(11)), '', '', script(forward(literal(999))))},
  {name:'empty numeric input', xml:project(forward(literal('')))},
  {name:'unsupported native block', xml:project(forward(literal(3)) + block('doSayFor', literal('hello') + literal(0))), unsupported:true}
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

test('native Snap exports retain execution behavior through shared parsing and Python conversion', async () => {
  const browser = await chromium.launch({chromiumSandbox:true, executablePath:process.env.CHROMIUM_PATH || chromium.executablePath()});
  try {
    const page = await browser.newPage();
    page.setDefaultTimeout(30000);
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      const file = path.resolve(root, '.' + decodeURIComponent(url.pathname) + (url.pathname.endsWith('/') ? 'index.html' : ''));
      if (!file.startsWith(root + path.sep)) return route.abort();
      try {
        let body = await readFile(file);
        if (url.pathname.endsWith('/turtlestitchsrc/gui.js')) body = Buffer.from(body.toString() + `
          (() => { const open = IDE_Morph.prototype.openIn; IDE_Morph.prototype.openIn = function(world,...args) {
            const result = open.call(this,world,...args); if (world.worldCanvas.closest('dialog')) window.nativeCorpusIde = this; return result;
          }; })();`);
        await route.fulfill({body,contentType:mime[path.extname(file)] || 'application/octet-stream'});
      } catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await installCodeMirrorModules(page);
    await page.goto(origin + '/homepage/workbookDesign/');
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.section-block').first().click();
    await page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first().click();
    await page.locator('dialog[open]').getByRole('tab',{name:'Snap!',exact:true}).click();
    await page.waitForFunction(() => window.nativeCorpusIde?.currentSprite);
    await page.evaluate(() => {
      const worker = new Worker('/artifacts/newest/worker.js');
      const pending = new Map();
      worker.onmessage = event => {const reply = JSON.parse(event.data); const waiter = pending.get(reply.requestId); if (waiter) {pending.delete(reply.requestId); waiter.resolve(reply);}};
      worker.onerror = event => {for (const waiter of pending.values()) waiter.reject(new Error(event.message)); pending.clear();};
      window.corpusRequest = (requestId, state) => new Promise((resolve,reject) => {
        pending.set(requestId,{resolve,reject}); worker.postMessage(JSON.stringify({requestId,command:{name:'worker.programAnalysis',json:{state,environment:{libraries:['Turtle','System','Web','NumberList','Sudoku']}}}}));
      });
      window.corpusWorker = worker;
    });
    for (const fixture of cases) {
      const native = await page.evaluate(async ({name, xml}) => {
        // openProjectString opens on a later step; the raw call replaces the project before it is read and run.
        nativeCorpusIde.rawOpenProjectString(xml);
        const sprite = nativeCorpusIde.currentSprite;
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
        const exported = nativeCorpusIde.getProjectXML();
        const hat = sprite.scripts.children.find(child => child.selector === 'receiveGo');
        const threads = nativeCorpusIde.stage.threads;
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
    await page.evaluate(() => corpusWorker.terminate());
  } finally {await browser.close();}
});
