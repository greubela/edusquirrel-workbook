import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root=path.resolve(import.meta.dirname,'../..');
const origin='http://localhost:9000';
const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg','.webp':'image/webp'};

async function cacheRecord(page, value) {
  return page.evaluate(value=>new Promise((resolve,reject)=>{
    const request=indexedDB.open('EvaDidCacheDb');
    request.onerror=()=>reject(request.error);
    request.onsuccess=()=>{
      const db=request.result, tx=db.transaction('cache',value===undefined?'readonly':'readwrite'), store=tx.objectStore('cache');
      const result=value===undefined?store.get('tripleCache'):store.put({id:'tripleCache',value});
      let record;
      result.onsuccess=()=>record=result.result;
      tx.oncomplete=()=>{db.close();resolve(record);};
      tx.onabort=()=>{db.close();reject(tx.error);};
    };
  }), value);
}

test('catalogue translations, native targets and artwork exist', async()=>{
  const source=await fs.readFile(path.join(root,'modules/client/src/main/scala/it/evadid/homepage/workbook/content/DigitalWorkbookCatalog.scala'),'utf8');
  const en=JSON.parse(await fs.readFile(path.join(root,'resources/languageMaps/eva/workbookSelection/map-en.json')));
  const de=JSON.parse(await fs.readFile(path.join(root,'resources/languageMaps/eva/workbookSelection/map-de.json')));
  assert.deepEqual(Object.keys(en).sort(),Object.keys(de).sort());
  for(const [,id] of source.matchAll(/(?:workbook|Entry)\("([^"]+)"/g)) {
    assert(en[id+'-title'] && en[id+'-description']);
    assert(de[id+'-title'] && de[id+'-description']);
  }
  for(const [,id,author,image,container,page] of source.matchAll(/workbook\("([^"]+)", "([^"]+)", "([^"]+)", "([^"]+)", "([^"]+)"/g)) {
    await fs.access(path.join(root,'resources/img/art/mockup',image));
    const html=await fs.readFile(path.join(root,'homepage',page,'index.html'),'utf8');
    assert(html.includes(`id="${container}"`));
    assert(html.includes('workbook-catalog.css'));
  }
  const entry=await fs.readFile(path.join(root,'homepage/workbooks/index.html'),'utf8');
  assert(entry.includes('id="landingPage"'));
  assert(!entry.includes('CodeMirrorLoader') && !entry.includes('fullturtle.js'), 'catalogue does not load editor dependencies');
  assert(!entry.includes('<figure'), 'catalogue content is rendered by Laminar');
});

test('catalogue renders before login, reuses IndexedDB immediately and refreshes translations deterministically', {skip:!process.env.CATALOG_BROWSER_TEST},async()=>{
  const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',args:['--no-sandbox']});
  try {
    const page=await browser.newPage({viewport:{width:1440,height:1000}});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    let gate, refreshTitle;
    const waiting=[];
    await page.addInitScript(()=>{
      window.cacheOps=[];
      for(const op of ['get','put','openCursor']) {
        const original=IDBObjectStore.prototype[op];
        IDBObjectStore.prototype[op]=function(...args){
          if(this.name==='cache')window.cacheOps.push({op,key:args[0]?.id||args[0],at:performance.now()});
          return original.apply(this,args);
        };
      }
    });
    await page.route('**/*',async route=>{
      const url=new URL(route.request().url());
      if(url.origin!==origin) {
        if(route.request().resourceType()==='image') return route.fulfill({body:await fs.readFile(path.join(root,'resources/img/art/mockup/Bitcoin.png')),contentType:'image/png'});
        return route.abort();
      }
      const language=url.pathname.includes('/languageMaps/')||url.pathname.includes('/locale/lang-');
      if(language && gate) {waiting.push(url.pathname);await gate;}
      const file=path.resolve(root,'.'+decodeURIComponent(url.pathname)+(url.pathname.endsWith('/')?'index.html':''));
      if(!file.startsWith(root+path.sep)) return route.abort();
      try {
        let body=await fs.readFile(file);
        if(refreshTitle && url.pathname==='/resources/languageMaps/eva/workbookSelection/map-en.json') {
          const data=JSON.parse(body);data.title=refreshTitle;body=Buffer.from(JSON.stringify(data));
        }
        await route.fulfill({body,contentType:mime[path.extname(file)] || 'application/octet-stream'});
      } catch {await route.fulfill({status:404,body:'Not found'});}
    });
    const entry=origin+'/homepage/workbooks/index.html';
    const coldStart=Date.now();await page.goto(entry);
    await page.getByRole('heading',{name:'Digital workbooks',exact:true}).waitFor({timeout:60000});
    assert.equal(await page.locator('.workbook-catalog-card').count(),13);
    assert.equal(await page.locator('.container-login').count(),0, 'catalogue is public');
    const evacuation = page.getByRole('link',{name:/Evacuation with grid automata/});
    assert.equal(await evacuation.getAttribute('href'),'../evacuationWorkbook/');
    assert.match(await evacuation.innerText(),/in development/);
    assert.equal(await page.locator('.workbook-catalog-grid').first().locator('a').count(),8);
    assert.equal(await page.locator('a[href$=".pdf"], a[href$=".zip"]').count(),0);
    const coldMs=Date.now()-coldStart;
    await page.waitForFunction(()=>window.cacheOps.some(e=>e.op==='put' && e.key==='tripleCache'));
    const stored=await cacheRecord(page);
    assert(stored.value.length>100000,'real ParsedTriples cache was persisted');
    // Unrelated cache data must not be scanned during a targeted language read.
    await page.evaluate(()=>new Promise(resolve=>{const r=indexedDB.open('EvaDidCacheDb');r.onsuccess=()=>{const db=r.result, tx=db.transaction('cache','readwrite');tx.objectStore('cache').put({id:'unrelated',value:'x'.repeat(1000000)});tx.oncomplete=()=>{db.close();resolve();};};}));
    for(const size of [{width:390,height:844},{width:1440,height:1000}]) {
      await page.setViewportSize(size);
      assert(await page.locator('.workbook-catalog').evaluate(el=>{const b=el.getBoundingClientRect();return b.left>=0 && b.right<=innerWidth+1;}),'catalogue fits viewport');
      assert.equal(await page.locator('.workbook-catalog [style], .workbook-catalog style').count(),0);
      assert.equal(await page.locator('.workbook-footer').evaluate(el=>getComputedStyle(el).position),'static','footer does not obscure catalogue cards');
    }
    if(process.env.CATALOG_SCREENSHOT) await page.screenshot({path:process.env.CATALOG_SCREENSHOT,fullPage:true});
    await page.locator('.select-language-line > div').first().click();
    await page.getByRole('heading',{name:'Digitale Arbeitshefte',exact:true}).waitFor();
    await page.locator('.select-language-line > div').nth(1).click();
    await page.getByRole('heading',{name:'Digital workbooks',exact:true}).waitFor();

    let release;gate=new Promise(resolve=>release=resolve);
    const warmStart=Date.now();await page.reload();
    await page.getByRole('heading',{name:'Digital workbooks',exact:true}).waitFor({timeout:15000});
    const warmMs=Date.now()-warmStart;
    assert(waiting.length>0,'remote refresh is pending');
    const operations=await page.evaluate(()=>window.cacheOps);
    assert.deepEqual(operations.map(e=>e.op),['get'],'warm startup reads one key and does not rewrite restored data');
    assert.equal(operations[0].key,'tripleCache');
    refreshTitle='Fresh digital workbooks';release();gate=null;
    await page.getByRole('heading',{name:refreshTitle,exact:true}).waitFor();
    console.log(JSON.stringify({coldMs,warmMs,cachedBytes:stored.value.length,cacheReadMs:operations[0].at}));

    await page.getByRole('link',{name:/Unmask phishing/}).click();
    await page.locator('.section-register .login-area').nth(1).locator('button').click({timeout:60000});
    await page.locator('.section-block').nth(1).click();
    const answer=page.locator('.answer-table input').first();await answer.waitFor();
    await answer.fill('Saved from the digital catalogue');
    await page.waitForFunction(()=>new Promise(resolve=>{const r=indexedDB.open('EvaDidInteractionDB');r.onsuccess=()=>{const db=r.result,tx=db.transaction('variableHistoryStore'),q=tx.objectStore('variableHistoryStore').getAll();q.onsuccess=()=>resolve(q.result.some(record=>record.value.includes('Saved from the digital catalogue')));tx.oncomplete=()=>db.close();};}));
    await page.locator('.workbook-user-menu-button').click();
    await page.getByText('All digital workbooks',{exact:true}).click();
    await page.locator('.workbook-catalog').waitFor();
    await page.getByRole('link',{name:/Unmask phishing/}).click();
    await page.locator('.section-block').nth(1).click({timeout:60000});
    await page.waitForFunction(()=>document.querySelector('.answer-table input')?.value==='Saved from the digital catalogue');
    assert.equal(await page.locator('.container-login').count(),0,'existing session restores on direct entry');

    // A corrupt record and unavailable IndexedDB must each fall back to source files.
    await cacheRecord(page,'invalid cache');
    await page.goto(entry);
    await page.getByRole('heading',{name:refreshTitle,exact:true}).waitFor({timeout:60000});
    await page.addInitScript(()=>{const open=indexedDB.open.bind(indexedDB);indexedDB.open=function(name,...args){if(name==='EvaDidCacheDb')throw new DOMException('denied','SecurityError');return open(name,...args);};});
    await page.reload();
    await page.getByRole('heading',{name:refreshTitle,exact:true}).waitFor({timeout:60000});
    assert.deepEqual(errors,[],'cache fallback, refresh and selection do not raise page errors');
  } finally {await browser.close();}
});
