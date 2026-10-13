import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';
import {startWorkbookTestServer} from './workbook-test-server.mjs';

const root=path.resolve(import.meta.dirname,'../..');
const origin='http://localhost:9000';
const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

test('the first shell paints before the bundle, translations, config and optional runtimes finish',async()=>{
  const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true});
  const releases=[];
  try {
    const page=await browser.newPage();
    const requests=[];
    function gate() { let release;const waiting=new Promise(resolve=>{release=resolve;});releases.push(release);return {waiting,release}; }
    const bundle=gate(), languages=gate(), optional=gate();
    let languageRequested;
    const startedLanguages=new Promise(resolve=>{languageRequested=resolve;});
    await page.route('**/*',async route=>{
      const url=new URL(route.request().url());
      requests.push(url.pathname);
      if(url.origin!==origin) return route.abort();
      if(url.pathname.endsWith('/client.js')) await bundle.waiting;
      if(url.pathname.includes('/languageMaps/')||url.pathname.includes('/locale/lang-')) {languageRequested();await languages.waiting;}
      if(url.pathname.endsWith('/.env')||url.pathname.endsWith('/adjustedMorphic.js')) await optional.waiting;
      let file=path.resolve(root,'.'+decodeURIComponent(url.pathname));
      if(!file.startsWith(root+path.sep))return route.abort();
      if(url.pathname.endsWith('/CodeMirrorLoader.js'))file=process.env.CODEMIRROR_TEST_BUNDLE;
      try {await route.fulfill({body:await fs.readFile(file),contentType:mime[path.extname(file)]||'application/octet-stream'});}
      catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await page.goto(origin+'/homepage/workbookDesign/index.html',{waitUntil:'commit'});
    await page.locator('.startup-shell').waitFor({state:'visible'});
    assert.equal(requests.filter(url=>url.endsWith('/.env')).length,0,'config never gates the first bundle');
    assert.equal(requests.filter(url=>/fullturtle|jsxgraphcore|adjustedMorphic|CodeMirrorLoader|pyodide-worker/.test(url)).length,0);
    bundle.release();
    await page.waitForFunction(()=>performance.getEntriesByType('paint').some(entry=>entry.name==='first-contentful-paint'));
    await startedLanguages;
    assert.equal(requests.filter(url=>url.endsWith('/.env')).length,0);
    await page.locator('.startup-shell').waitFor({state:'visible'});
    languages.release();
    await page.locator('.container-login').waitFor();
    await page.waitForFunction(()=>document.querySelector('script[src$="adjusted/adjustedMorphic.js"]'));
    assert.equal(await page.locator('.startup-shell').count(),0);
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.section-block').first().waitFor();
    assert.equal(await page.evaluate(()=>typeof window.WorldMorph),'undefined','overview and login remain usable while Snap is pending');
    optional.release();
    await page.waitForFunction(()=>!!window.TurtleStitchPoC?._impl);
  } finally {releases.forEach(release=>release());await browser.close();}
});

test('Python warms before an editor opens and executes commands without creating another worker',async()=>{
  const server=await startWorkbookTestServer();
  const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true,
    args:['--proxy-server=http://127.0.0.1:1','--proxy-bypass-list=127.0.0.1']});
  try {
    const context=await browser.newContext();
    await context.addInitScript(()=>{
      const NativeWorker=window.Worker;
      window.startupWorkers=[];
      window.startupWorkerHandles=[];
      window.Worker=class extends NativeWorker {
        constructor(url,options) {
          super(url,options);
          this.record={url:String(url),requests:[],responses:[],warmReady:false};
          window.startupWorkers.push(this.record);
          window.startupWorkerHandles.push(this);
          this.addEventListener('message',event=>{
            try {
              const reply=JSON.parse(event.data);
              this.record.responses.push(reply);
              const request=this.record.requests.find(request=>request.requestId===reply.requestId);
              if(request?.command?.name==='worker.runPython'&&request.command.json.code===''&&reply.response.result[0]===1) this.record.warmReady=true;
            } catch {}
          });
        }
        postMessage(message,...rest) {
          try {this.record.requests.push(JSON.parse(message));} catch {}
          return super.postMessage(message,...rest);
        }
      };
    });
    const page=await context.newPage();
    const messages=[];
    page.on('console',message=>messages.push(message.text()));
    await page.goto(server.origin+'/homepage/workbookDesign/index.html',{waitUntil:'domcontentloaded'});
    try {await page.waitForFunction(()=>window.startupWorkers.some(worker=>worker.warmReady),{},{timeout:90000});}
    catch(error) {console.error(JSON.stringify(await page.evaluate(()=>window.startupWorkers)));console.error(messages.join('\n'));throw error;}
    assert.equal(await page.locator('dialog[open]').count(),0,'warmup does not create an editor');
    await page.waitForFunction(()=>window.startupWorkers.filter(worker=>worker.url.endsWith('/worker.js')).length===2);
    const before=await page.evaluate(()=>window.startupWorkers.length);
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.section-block').first().click();
    await page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first().click();
    const dialog=page.locator('dialog[open]');
    await dialog.getByRole('button',{name:'Python',exact:true}).click();
    await dialog.locator('.cm-editor').waitFor();
    await page.evaluate(()=>{
      const index=window.startupWorkers.findIndex(worker=>worker.warmReady);
      window.startupWorkerHandles[index].postMessage(JSON.stringify({requestId:'warmup-run',
        command:{name:'worker.runPython',json:{code:'3 + 4'}}}));
    });
    await page.waitForFunction(()=>window.startupWorkers.some(worker=>worker.responses.some(reply=>reply.requestId==='warmup-run')));
    const result=await page.evaluate(()=>{
      const reply=window.startupWorkers.flatMap(worker=>worker.responses).find(reply=>reply.requestId==='warmup-run');
      return JSON.parse(reply.response.result[1].json);
    });
    assert.equal(result.value,7);
    assert.equal(await page.evaluate(()=>window.startupWorkers.length),before,'execution uses the warmed interpreter without starting another worker');
  } finally {await browser.close();await server.close();}
});
