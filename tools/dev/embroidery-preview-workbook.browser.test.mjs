import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const folder = path.join(root, 'resources/languageMaps/eva/embroiderypreviewworkbook');
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

const koch = `def koch(length, level):
    if level == 0:
        forward(length)
    else:
        koch(length / 3, level - 1)
        turn(-60)
        koch(length / 3, level - 1)
        turn(120)
        koch(length / 3, level - 1)
        turn(-60)
        koch(length / 3, level - 1)
`;
const branch = `def branch(length, level):
    forward(length)
    if level > 0:
        turn(-60)
        branch(length / 3, level - 1)
        turn(60)
        branch(length / 3, level - 1)
        turn(60)
        branch(length / 3, level - 1)
        turn(-60)
    penup()
    forward(-length)
    pendown()

for spoke in range(6):
    branch(90, 2)
    turn(60)
`;

test('all sections render, solutions match previews and answers persist after reopening and reload', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || chromium.executablePath(),args:['--no-sandbox']});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}});
    page.setDefaultTimeout(60000);
    page.on('console', message=>{if(message.text().startsWith('Preview timing')) console.log(message.text());});
    const errors=[]; page.on('pageerror', error=>{errors.push(error.message);console.error('Browser error:',error.message);});
    await page.route('**/*', async route => {
      const url=new URL(route.request().url());
      if(url.origin!==origin) return route.abort();
      let file=path.resolve(root,'.'+decodeURIComponent(url.pathname)+(url.pathname.endsWith('/')?'index.html':''));
      if(!file.startsWith(root+path.sep)) return route.abort();
      if(url.pathname==='/homepage/js/CodeMirrorLoader.js' && process.env.CODEMIRROR_TEST_BUNDLE) file=process.env.CODEMIRROR_TEST_BUNDLE;
      try {
        let body=await fs.readFile(file);
        if(process.env.EMBROIDERY_DEBUG && url.pathname.endsWith('/jsxgraphcore.js')) body=Buffer.from(body.toString()+`
          (()=>{const original=JXG.JSXGraph.initBoard;JXG.JSXGraph.initBoard=function(...args){
            const board=original.apply(this,args),create=board.create;let count=0;const start=performance.now();
            board.create=function(...parts){count++;if(count%100===0)console.log('Preview timing '+args[0]+' objects='+count+' ms='+Math.round(performance.now()-start));return create.apply(this,parts);};
            console.log('Preview timing start '+args[0]);return board;
          };})();`);
        await route.fulfill({body,contentType:mime[path.extname(file)] || 'application/octet-stream'});
      }
      catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await page.goto(origin+'/homepage/embroideryPreviewWorkbook/index.html');
    await page.locator('.section-register .login-area').nth(1).locator('button').click({timeout:60000});
    assert.equal(await page.locator('.section-block').count(),8);
    const sections = page.locator('.section-block');
    const openButtons = page.getByRole('button',{name:/Editor.*öffnen|Open editor/i});
    async function solve(section,index,code,count) {
      console.log("Solving section",section,"exercise",index);
      await sections.nth(section).click();
      await page.waitForFunction(count => document.querySelectorAll(".turtle-gradig-panel:not(dialog *)").length===count, {2:8,5:4,6:13}[section]);
      const previewIds = () => page.locator('.turtle-gradig-panel:not(dialog *)').evaluateAll(panels=>panels.map(panel=>panel.id));
      const idsBeforeOpen = await previewIds();
      await openButtons.nth(index).click();
      const dialog=page.locator('dialog[open]');
      // The shell retains the last closed editor until the new one mounts.
      await page.waitForFunction(count=>document.querySelectorAll('dialog[open] .turtle-line:not(.turtle-line--unexpected):not(.turtle-line--jump)').length===count,count);
      await dialog.locator('.eva-editor').waitFor();
      assert.deepEqual(await previewIds(),idsBeforeOpen,'opening an editor preserves the workbook previews');
      await dialog.getByRole('button',{name:'Python',exact:true}).click();
      await dialog.locator('.cm-content').fill(code);
      await page.waitForFunction(count => document.querySelectorAll('dialog[open] .turtle-line--correct:not(.turtle-line--jump)').length===count,count);
      assert.equal(await dialog.locator('.turtle-line--missing, .turtle-line--unexpected').count(),0);
      const idsBeforeClose = await previewIds();
      await page.locator('.fullscreen-close-button').click();
      assert.deepEqual(await previewIds(),idsBeforeClose,'closing an editor preserves the workbook previews');
      await openButtons.nth(index).click();
      await dialog.getByRole('button',{name:'Python',exact:true}).click();
      // The editor normalizes blank lines around function declarations on restore.
      const normalize = text => text.split('\n').filter(line=>line.trim()).join('\n').trim();
      assert.equal(normalize(await dialog.locator('.cm-content').innerText()),normalize(code));
      await page.locator('.fullscreen-close-button').click();
    }
    for(const [section,count] of [[2,8],[3,3],[4,2],[5,4],[6,13]]) {
      await sections.nth(section).click();
      console.log("Checking section",section,"expected previews",count);
      await page.waitForFunction(count => document.querySelectorAll(".turtle-gradig-panel:not(dialog *)").length===count,count,{timeout:60000});
      assert.equal(await page.locator('.turtle-gradig-panel:not(dialog *)').count(),count);
      if(section===2 || section===3 || section===4) assert.equal(await page.locator(".be-program-snap-renderer__canvas").count(),0,"unsolved exercises defer Snap thumbnails");
    }
    if(process.env.EMBROIDERY_SCREENSHOT) {
      await sections.nth(6).click();
      await page.waitForFunction(()=>document.querySelectorAll('.turtle-gradig-panel:not(dialog *)').length===13);
      await page.screenshot({path:process.env.EMBROIDERY_SCREENSHOT,fullPage:true});
    }
    await solve(2,0,'for edge in range(4):\n    forward(50)\n    turn(90)\n',4);
    await sections.nth(5).click();
    await page.waitForFunction(()=>document.querySelectorAll(".turtle-gradig-panel:not(dialog *)").length===4);
    await openButtons.nth(0).click();
    let dialog=page.locator('dialog[open]');
    await page.waitForFunction(()=>document.querySelectorAll('dialog[open] .turtle-line--correct:not(.turtle-line--jump)').length===21);
    await dialog.getByRole('button',{name:'Python',exact:true}).click();
    assert.match(await dialog.locator('.cm-content').innerText(),/if i % 2 == 0/);
    await page.waitForFunction(()=>document.querySelectorAll('dialog[open] .turtle-line--correct:not(.turtle-line--jump)').length===21);
    assert.equal(await dialog.locator('.turtle-line--missing, .turtle-line--unexpected').count(),0);
    await page.locator('.fullscreen-close-button').click();
    await solve(6,6,koch+'\nkoch(270, 2)\n',16);
    await solve(6,10,koch+'\nfor side in range(3):\n    koch(270, 2)\n    turn(120)\n',48);
    await solve(6,12,branch,78);
    await sections.nth(7).click();
    await page.waitForFunction(()=>document.querySelectorAll(".turtle-gradig-panel:not(dialog *)").length===0);
    await openButtons.first().waitFor();
    assert.equal(await page.locator('.turtle-gradig-panel:not(dialog *)').count(),0,'free design has no artificial empty target');
    await openButtons.click();
    dialog=page.locator('dialog[open]');
    await page.waitForFunction(()=>document.querySelector('dialog[open] .eva-editor') && !document.querySelector('dialog[open] .turtle-gradig-panel'));
    await dialog.getByRole('button',{name:'Python',exact:true}).click();
    await dialog.locator('.cm-content').fill('forward(123)\n');
    await page.locator('.fullscreen-close-button').click();
    await page.reload();
    await page.locator('.section-block').first().waitFor({timeout:60000});
    await sections.nth(7).click();
    await page.waitForFunction(()=>document.querySelectorAll(".turtle-gradig-panel:not(dialog *)").length===0);
    await openButtons.first().waitFor();
    await openButtons.click();
    await page.waitForFunction(()=>document.querySelector('dialog[open] .eva-editor') && !document.querySelector('dialog[open] .turtle-gradig-panel'));
    await page.locator('dialog[open]').getByRole('button',{name:'Python',exact:true}).click();
    assert.match(await page.locator('dialog[open] .cm-content').innerText(),/forward\(123\)/);
    assert.deepEqual(errors,[]);
  } finally {await browser.close();}
});
