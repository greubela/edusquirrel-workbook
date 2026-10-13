import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

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
