import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root=path.resolve(import.meta.dirname,'../..');
const origin='http://localhost:9000';
const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
async function saved(page,id,value) {
  await page.waitForFunction(({id,value})=>new Promise(resolve=>{
    const request=indexedDB.open('EvaDidInteractionDB');
    request.onerror=()=>resolve(false);
    request.onsuccess=()=>{
      const db=request.result, transaction=db.transaction('variableHistoryStore','readonly');
      const records=transaction.objectStore('variableHistoryStore').getAll();
      records.onsuccess=()=>resolve(records.result.some(r=>r.id.includes(id) && r.value.includes(value)));
      records.onerror=()=>resolve(false);
      transaction.oncomplete=()=>db.close();
    };
  }),{id,value});
}
test('Phishing learning chapters inspect raw Unicode locally, save judgments and grade factual exercises',async()=>{
  const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',args:['--no-sandbox']});
  try {
    const page=await browser.newPage({viewport:{width:1440,height:900}});
    const errors=[], external=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.route('**/*',async route=>{
      const url=new URL(route.request().url());
      if(url.origin!==origin) {external.push(url.href);return route.abort();}
      const file=path.resolve(root,'.'+decodeURIComponent(url.pathname));
      if(!file.startsWith(root+path.sep)) return route.abort();
      try {await route.fulfill({body:await fs.readFile(file),contentType:mime[path.extname(file)] || 'application/octet-stream'});}
      catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await page.goto(origin+'/homepage/phishingWorkbook/index.html');
    await page.locator('.section-register .login-area').nth(1).locator('button').click({timeout:60000});
    await page.locator('.section-block').nth(1).click();
    await page.getByText('Evidence from the mailbox',{exact:true}).waitFor();
    const evidence=page.locator('.answer-table');
    assert.equal(await evidence.locator('input').count(),12);
    for(let i=0;i<12;i++) await evidence.locator('input').nth(i).fill('Mailbox evidence '+i);
    assert.match(await evidence.innerText(),/no automatic grade/);
    await page.locator('.section-block').nth(2).click();
    await page.getByText('Hostname and registrable domain (these examples)',{exact:true}).waitFor();
    const domainTables=page.locator('.answer-table');
    const parts=domainTables.nth(0), judgments=domainTables.nth(1), codes=domainTables.nth(2);
    for(const [i,v] of ['www.planetarium.berlin','planetarium.berlin','www.dfb.fanshop.io','fanshop.io','www.sparkasse.de.suport.ru','suport.ru'].entries())
      await parts.locator('input').nth(i).fill(v);
    assert.match(await parts.innerText(),/6 of 6 checked cells/);
    for(let i=0;i<10;i++) await judgments.locator('input').nth(i).fill('Address evidence '+i);
    assert.match(await judgments.innerText(),/no automatic grade/);
    for(const [i,v] of ['65','97','223','1053 1077 1090','127757'].entries()) await codes.locator('input').nth(i).fill(v);
    assert.match(await codes.innerText(),/5 of 5 checked cells/);
    const inspector=page.locator('.unicode-comparison');
    const inputs=inspector.locator('textarea');
    assert.equal(await inputs.first().getAttribute('maxlength'),'256');
    assert.equal(await inputs.nth(1).inputValue(),'payраI.com');
    assert.match(await inspector.locator('.unicode-status').innerText(),/differ/);
    assert.equal(await inspector.locator('tbody').nth(1).locator('tr').nth(3).locator('td').nth(2).innerText(),'U+0440');
    const externalBefore=external.length;
    await inputs.first().fill('A🌍e\u0301\u200B');
    assert.equal(await inspector.locator('tbody').first().locator('tr').count(),5);
    assert.deepEqual(await inspector.locator('tbody').first().locator('tr td:nth-child(3)').allTextContents(),['U+0041','U+1F30D','U+0065','U+0301','U+200B']);
    await inputs.nth(1).fill('A🌍e\u0301\u200B');
    assert.match(await inspector.locator('.unicode-status').innerText(),/identical/);
    await inputs.first().fill('<img src="https://example.invalid/" onerror="alert(1)">');
    assert.equal(await inspector.locator('img,script,a,iframe').count(),0,'raw inputs never become markup or links');
    assert.equal(external.length,externalBefore);
    await inspector.getByRole('button',{name:'Reset inputs',exact:true}).click();
    assert.equal(await inputs.first().inputValue(),'paypal.com');
    const raw=' Nein heißt Нет 🌍\n';
    await inputs.first().fill(raw);
    await inputs.nth(1).fill(raw);
    for(const viewport of [{width:390,height:844},{width:844,height:390},{width:1440,height:900}]) {
      await page.setViewportSize(viewport);
      const geometry=await inspector.evaluate(el=>{
        const box=el.getBoundingClientRect();
        return {left:box.left,right:box.right,width:innerWidth,inline:el.querySelectorAll('[style],style').length,
          tables:[...el.querySelectorAll('table')].map(t=>t.getBoundingClientRect().right),border:getComputedStyle(el.querySelector('textarea')).borderTopWidth};
      });
      assert(geometry.left>=0 && geometry.right<=geometry.width+1);
      assert(geometry.tables.every(right=>right<=geometry.width+1));
      assert.equal(geometry.inline,0);
      assert.equal(geometry.border,'1px');
    }
    await saved(page,'phishing-unicode','Nein');
    await page.locator('.section-block').nth(3).click();
    await page.getByText('Attachment-risk analysis',{exact:true}).waitFor();
    const malwareTables=page.locator('.answer-table');
    const attachments=malwareTables.nth(0), malware=malwareTables.nth(1);
    assert.equal(await attachments.locator('input').count(),18);
    for(let i=0;i<18;i++) await attachments.locator('input').nth(i).fill('Attachment risk '+i);
    assert.match(await attachments.innerText(),/no automatic grade/);
    for(const [i,v] of ['Adware','Ransomware','Spyware'].entries()) await malware.locator('select').nth(i).selectOption(v);
    assert.match(await malware.innerText(),/3 of 3 checked cells/);
    await page.locator('.section-block').nth(4).click();
    await page.getByText('Five verification measures',{exact:true}).waitFor();
    const checklist=page.locator('.answer-table');
    for(let i=0;i<15;i++) await checklist.locator('input').nth(i).fill('Verification measure '+i);
    assert.match(await checklist.innerText(),/no automatic grade/);
    await saved(page,'phishing-final-checklist','Verification measure 14');
    await page.reload();
    await page.locator('.section-block').nth(4).click({timeout:60000});
    await page.getByText('Five verification measures',{exact:true}).waitFor();
    assert.equal(await checklist.locator('input').nth(14).inputValue(),'Verification measure 14');
    await page.locator('.section-block').nth(3).click();
    await page.getByText('Attachment-risk analysis',{exact:true}).waitFor();
    assert.equal(await attachments.locator('input').nth(17).inputValue(),'Attachment risk 17');
    assert.match(await malware.innerText(),/3 of 3 checked cells/);
    await page.locator('.section-block').nth(2).click();
    await page.getByText('Hostname and registrable domain (these examples)',{exact:true}).waitFor();
    assert.equal(await inputs.first().inputValue(),raw);
    assert.equal(await inputs.nth(1).inputValue(),raw);
    assert.match(await parts.innerText(),/6 of 6 checked cells/);
    assert.match(await codes.innerText(),/5 of 5 checked cells/);
    assert.equal(await judgments.locator('input').nth(9).inputValue(),'Address evidence 9');
    assert.deepEqual(errors,[]);
  } finally {await browser.close();}
});
