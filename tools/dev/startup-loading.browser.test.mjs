import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root=path.resolve(import.meta.dirname,'../..');
const origin='http://localhost:9000';
const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

for(const prefix of ['/', '/edusquirrel-workbook/']) {
  test(`startup renders and preheats while configuration, translations and optional scripts are stalled (${prefix})`,async()=>{
    const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true});
    let releaseLibraries,releaseLanguages,releaseConfig;
    const libraries=new Promise(resolve=>releaseLibraries=resolve);
    const languages=new Promise(resolve=>releaseLanguages=resolve);
    const config=new Promise(resolve=>releaseConfig=resolve);
    try {
      const page=await browser.newPage();
      const errors=[],requests=[];
      await page.addInitScript(()=>{
        window.startedWorkers=[];
        const NativeWorker=window.Worker;
        window.Worker=class extends NativeWorker {
          constructor(url,options){super(url,options);window.startedWorkers.push(new URL(url,document.baseURI).href);}
        };
      });
      page.on('pageerror',error=>errors.push(error.message));
      await page.route('**/*',async route=>{
        const url=new URL(route.request().url());
        requests.push(url.pathname);
        if(url.origin!==origin || !url.pathname.startsWith(prefix))return route.abort();
        const relative=decodeURIComponent(url.pathname.slice(prefix.length));
        if(relative.split('/').includes('..'))return route.abort();
        if(relative==='deployment/client/.env') {await config;return route.fulfill({status:404,body:'Not found'});}
        if(relative.startsWith('resources/languageMaps/') || relative.includes('/20260704Snap/locale/'))await languages;
        if(relative.endsWith('/adjusted/adjustedMorphic.js') || relative.endsWith('/jsxgraphcore.js') || relative==='homepage/js/CodeMirrorLoader.js')await libraries;
        let file=path.join(root,relative+(relative.endsWith('/')?'index.html':''));
        if(relative==='homepage/js/CodeMirrorLoader.js')file=process.env.CODEMIRROR_TEST_BUNDLE;
        try {await route.fulfill({body:await fs.readFile(file),contentType:mime[path.extname(file)]||'application/octet-stream'});}
        catch {await route.fulfill({status:404,body:'Not found'});}
      });
      const workerRequest=page.waitForRequest(request=>
        request.url()===origin+prefix+'artifacts/newest/worker.js');
      await page.goto(origin+prefix+'homepage/workbookDesign/',{waitUntil:'domcontentloaded'});
      await page.locator('.workbook-header').waitFor();
      await page.locator('.section-register').waitFor();
      await page.waitForFunction(()=>document.querySelector('script[src$="CodeMirrorLoader.js"]') &&
        document.querySelector('script[src$="adjusted/adjustedMorphic.js"]') && document.querySelector('script[src$="jsxgraphcore.js"]'));
      assert(requests.some(url=>url.includes('/languageMaps/')),'language loading has started independently');
      await page.waitForFunction(()=>performance.getEntriesByType('resource').some(entry=>entry.name.endsWith('/turtlestitchsrc/embroider.js')));
      assert.equal(await page.evaluate(()=>typeof window.WorldMorph),'undefined','parallel prefetch must not execute later dependencies out of order');
      assert.equal(await page.locator('dialog[open]').count(),0,'preheating does not construct an editor');
      await page.waitForFunction(()=>window.startedWorkers.some(url=>url.endsWith('/artifacts/newest/worker.js')));
      assert.equal((await workerRequest).url(),origin+prefix+'artifacts/newest/worker.js','configuration timeout cannot stall worker preheating');
      assert(!requests.some(url=>url.includes('/homepage/deployment/')),'configuration uses one known location');
      releaseLanguages();
      await page.getByRole('button',{name:'Start Using Locally',exact:true}).click();
      await page.locator('.section-block').first().click();
      await page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first().click();
      await page.locator('dialog[open] .be-program-snap-renderer__canvas').waitFor();
      await page.locator('.fullscreen-close-button').click();
      releaseLibraries();releaseConfig();
      await page.waitForFunction(()=>typeof window.SnapSerializer?.prototype.eduSquirrelOriginalLoadProjectModel === 'function' && !!window.EduSquirrelCodeMirror && !!window.JXG);
      assert.equal(await page.locator('dialog[open]').count(),0,'late readiness cannot resurrect an unmounted editor');
      await page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first().click();
      await page.waitForFunction(()=>{
        const canvas=document.querySelector('dialog[open] canvas[aria-label="Block program editor"]');
        return canvas && canvas.width>1 && canvas.height>1 && new Set(canvas.getContext('2d')
          .getImageData(0,0,canvas.width,canvas.height).data).size>8;
      });
      assert.equal(requests.filter(url=>url.endsWith('/adjusted/adjustedMorphic.js')).length,1,'preload is reused by the script and editor');
      assert(!requests.some(url=>url.endsWith('/fullturtle.js')),'the compatibility code is part of the Scala.js bundle');
      assert.equal(await page.evaluate(()=>window.base_prog_dir),origin+prefix+'resources/programs/20260212TurtleStitch/');
      assert.deepEqual(errors,[]);
    } finally {releaseLibraries();releaseLanguages();releaseConfig();await browser.close();}
  });
}

test('a failed Snap dependency leaves the shell usable and a later editor open retries the load',async()=>{
  const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),chromiumSandbox:true});
  try {
    const page=await browser.newPage();
    const errors=[];
    let unavailable=true;
    page.on('pageerror',error=>errors.push(error.message));
    await page.route('**/*',async route=>{
      const url=new URL(route.request().url());
      if(url.origin!==origin)return route.abort();
      if(unavailable && url.pathname.endsWith('/adjusted/adjustedMorphic.js'))return route.fulfill({status:503,body:'Unavailable'});
      let file=path.resolve(root,'.'+url.pathname+(url.pathname.endsWith('/')?'index.html':''));
      if(!file.startsWith(root+path.sep))return route.abort();
      if(url.pathname==='/homepage/js/CodeMirrorLoader.js')file=process.env.CODEMIRROR_TEST_BUNDLE;
      try {await route.fulfill({body:await fs.readFile(file),contentType:mime[path.extname(file)]||'application/octet-stream'});}
      catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await page.goto(origin+'/homepage/workbookDesign/',{waitUntil:'domcontentloaded'});
    await page.getByRole('button',{name:'Start Using Locally',exact:true}).click();
    await page.locator('.section-block').first().click();
    const open=page.getByRole('button',{name:/Editor.*öffnen|Open editor/i}).first();
    await open.click();
    await page.locator('dialog[open]').getByRole('alert').filter({hasText:'Block editor unavailable'}).waitFor();
    assert.equal(await page.evaluate(()=>typeof window.WorldMorph),'undefined','later dependency scripts must not execute after failure');
    await page.locator('.fullscreen-close-button').click();
    unavailable=false;
    await open.click();
    await page.waitForFunction(()=>typeof window.SnapSerializer?.prototype.eduSquirrelOriginalLoadProjectModel === 'function');
    await page.waitForFunction(()=>{
      const canvas=document.querySelector('dialog[open] canvas[aria-label="Block program editor"]');
      return canvas && canvas.width>1 && canvas.height>1 && new Set(canvas.getContext('2d')
        .getImageData(0,0,canvas.width,canvas.height).data).size>8;
    });
    assert.deepEqual(errors,[]);
  } finally {await browser.close();}
});
