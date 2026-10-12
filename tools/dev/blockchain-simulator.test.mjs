import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {createHash} from 'node:crypto';
import {chromium} from 'playwright';

const root=path.resolve(import.meta.dirname,'../..');
const origin='http://localhost:9000';
const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
test('linked-block simulator mines, propagates edits, rejects nonce drafts and cancels on stop/close',async()=>{
  const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',args:['--no-sandbox']});
  try {
    const page=await browser.newPage({viewport:{width:1440,height:900}});
    const errors=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.route('**/*',async route=>{
      const url=new URL(route.request().url());
      if(url.origin!==origin) return route.abort();
      const file=path.resolve(root,'.'+decodeURIComponent(url.pathname));
      if(!file.startsWith(root+path.sep)) return route.abort();
      try { await route.fulfill({body:await fs.readFile(file),contentType:mime[path.extname(file)]||'application/octet-stream'}); }
      catch { await route.fulfill({status:404,body:'Not found'}); }
    });
    await page.goto(origin+'/homepage/blockchainWorkbook/index.html');
    await page.locator('.section-register .login-area').nth(1).locator('button').click({timeout:60000});
    await page.locator('.section-block').nth(6).click();
    const previews=page.locator('.blockchain-preview');
    await previews.first().waitFor();
    assert.equal(await previews.count(),2);
    const open=async index=>page.getByRole('button',{name:/Open editor/i}).nth(index).click();
    await open(0);
    const editor=page.locator('dialog[open] .blockchain-editor');
    const cards=editor.locator('.blockchain-block');
    await editor.waitFor();
    assert.equal(await cards.count(),4);
    const originalData=await cards.locator('textarea').evaluateAll(nodes=>nodes.map(node=>node.value));
    assert.deepEqual(originalData.map(text=>text.split('\n').length),[2,2,2,1]);
    const checkHashes=async()=>{
      let previous='0'.repeat(64);
      for(let index=0;index<4;index++){
        const card=cards.nth(index),data=await card.locator('textarea').inputValue(),nonce=await card.locator('input').inputValue();
        assert.equal(await card.locator('.blockchain-previous').innerText(),previous);
        const header=`edusquirrel-teaching-block-v1\n${index}\n${previous}\n${nonce}\n${Buffer.byteLength(data,'utf8')}\n`;
        const expected=createHash('sha256').update(header,'utf8').update(data,'utf8').digest('hex');
        assert.equal(await card.locator('.blockchain-hash').innerText(),expected);
        previous=expected;
      }
    };
    await checkHashes();
    const firstHash=await cards.first().locator('.blockchain-hash').innerText();
    await cards.first().locator('input').fill('');
    assert.match(await cards.first().locator('.blockchain-error').innerText(),/has not changed/);
    assert.equal(await cards.first().getByRole('button',{name:'Mine this block'}).isDisabled(),true);
    assert.equal(await cards.first().locator('.blockchain-hash').innerText(),firstHash);
    await cards.first().locator('input').fill('2147483648');
    assert.equal(await cards.first().locator('input').getAttribute('aria-invalid'),'true');
    await cards.first().locator('input').fill('0');
    assert.equal(await cards.first().locator('input').getAttribute('aria-invalid'),'false');
    const mineAll=async()=>{
      for(let index=0;index<4;index++){
        await cards.nth(index).getByRole('button',{name:'Mine this block'}).click();
        await page.waitForFunction(count=>document.querySelectorAll('dialog[open] .blockchain-block--valid').length>=count,index+1);
      }
      assert.equal(await editor.locator('.blockchain-block--valid').count(),4);
    };
    await mineAll();await checkHashes();
    const before=await cards.locator('.blockchain-hash').allTextContents();
    await cards.nth(1).locator('textarea').fill('Edited record 🌍\n ');
    const after=await cards.locator('.blockchain-hash').allTextContents();
    assert.equal(after[0],before[0]);
    for(let index=1;index<4;index++) assert.notEqual(after[index],before[index]);
    await checkHashes();await mineAll();
    for(const viewport of [{width:390,height:844},{width:844,height:390},{width:1440,height:900}]){
      await page.setViewportSize(viewport);
      const geometry=await editor.evaluate(el=>{
        const bounds=el.getBoundingClientRect();
        return {left:bounds.left,right:bounds.right,width:innerWidth,inline:el.querySelectorAll('[style],style').length,
          cardsFit:[...el.querySelectorAll('.blockchain-block')].every(card=>card.scrollWidth<=card.clientWidth+1),
          hashWrap:getComputedStyle(el.querySelector('.blockchain-hash')).overflowWrap};
      });
      assert(geometry.left>=0&&geometry.right<=geometry.width+1);
      assert.equal(geometry.inline,0);assert(geometry.cardsFit);assert.equal(geometry.hashWrap,'anywhere');
    }
    await page.locator('.fullscreen-close-button').click();
    assert.match(await previews.first().innerText(),/4 of 4/);
    await page.reload();
    await page.locator('.section-block').nth(6).click({timeout:60000});
    await previews.first().waitFor();assert.match(await previews.first().innerText(),/4 of 4/);
    await open(0);await editor.waitFor();
    assert.equal(await cards.nth(1).locator('textarea').inputValue(),'Edited record 🌍\n ');
    await checkHashes();
    await editor.getByRole('button',{name:'Reset chain',exact:true}).click();
    assert.deepEqual(await cards.locator('textarea').evaluateAll(nodes=>nodes.map(node=>node.value)),originalData);
    await page.locator('.fullscreen-close-button').click();
    const arithmetic=page.locator('.answer-table');
    await arithmetic.locator('input').nth(0).fill('65536');
    await arithmetic.locator('input').nth(1).fill('160000/65536');
    assert.match(await arithmetic.innerText(),/2 of 2 checked cells/);
    await open(1);await editor.waitFor();
    // Dispatch in one browser turn so cancellation precedes the scheduled first chunk.
    await editor.evaluate(el=>{
      el.querySelector('.blockchain-block button').click();
      [...el.querySelectorAll('button')].find(button=>button.textContent==='Stop mining').click();
    });
    assert.match(await editor.locator('.blockchain-mining-status').innerText(),/stopped after 0/);
    const nonces=await cards.locator('input').evaluateAll(nodes=>nodes.map(node=>node.value));
    await page.evaluate(()=>{
      document.querySelector('dialog[open] .blockchain-block button').click();
      document.querySelector('.fullscreen-close-button').click();
    });
    await page.waitForFunction(()=>!document.querySelector('dialog[open]'));
    await open(1);await editor.waitFor();
    assert.deepEqual(await cards.locator('input').evaluateAll(nodes=>nodes.map(node=>node.value)),nonces,'closing prevents pending mining writes');
    assert.deepEqual(errors,[]);
  } finally { await browser.close(); }
});
