import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const mime = {'.html':'text/html', '.js':'text/javascript', '.mjs':'text/javascript', '.css':'text/css', '.json':'application/json', '.svg':'image/svg+xml', '.png':'image/png'};
const origin = 'http://localhost:9000';

// Requires sbt buildJS and the project's existing Playwright dev dependency.
// Every request is local or aborted. No server, email account or new dependency is used.
test('mail simulator triages original messages, restores progress and composes only locally', async () => {
  await fs.access(path.join(root, 'artifacts/newest/client.js'));
  const browser = await chromium.launch({executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium', args:['--no-sandbox']});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:900}});
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    const external = [];
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) { external.push(url.href); return route.abort(); }
      const file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
      if (!file.startsWith(root + path.sep)) return route.abort();
      try { await route.fulfill({body:await fs.readFile(file), contentType:mime[path.extname(file)] || 'application/octet-stream'}); }
      catch { await route.fulfill({status:404,body:'Not found'}); }
    });
    await page.goto(origin + '/homepage/phishingWorkbook/index.html');
    const login = page.locator('.section-register .login-area').nth(1).locator('button');
    await login.click({timeout:60000});
    const open = page.getByRole('button', {name:/Open editor/i});
    await open.first().click();
    const editor = page.locator('dialog[open] .mail-simulator');
    await editor.waitFor();
    const toolbar = editor.locator('.mail-toolbar');
    const folder = name => editor.locator('.mail-folders').getByRole('button', {name:new RegExp(name)});
    const action = name => toolbar.getByRole('button', {name,exact:true});
    assert.equal(await editor.locator('.mail-list-item').count(), 15);
    assert.equal(await editor.locator('.mail-folders button').count(), 5);
    assert(await action('New email').isDisabled());
    assert(await action('Reply').isDisabled());
    assert(await action('Archive').isDisabled());
    await editor.locator('.mail-list-item').first().click();
    assert((await editor.locator('.mail-folder-summary').innerText()).includes('Inbox'));
    assert((await editor.locator('.mail-status').innerText()).includes('Simulation'));
    assert.equal(await editor.locator('.mail-list-item').first().evaluate(el=>getComputedStyle(el).borderTopWidth),'0px');
    const closeLayout = await page.locator('dialog[open] .fullscreen-close-button').evaluate(el => {
      const r=el.getBoundingClientRect();
      return {top:r.top,left:r.left,right:r.right,bottom:r.bottom,width:r.width,height:r.height,
        covered:document.elementFromPoint(r.left+r.width/2,r.top+r.height/2)!==el,
        position:getComputedStyle(el).position};
    });
    assert(closeLayout.top>=0 && closeLayout.bottom<=900 && !closeLayout.covered, `fullscreen close control is visible: ${JSON.stringify(closeLayout)}`);
    const selectedSubject = await editor.locator('.mail-viewer h2').innerText();
    if (process.env.MAIL_SCREENSHOT) await page.screenshot({path:process.env.MAIL_SCREENSHOT});
    assert.equal(await editor.locator('.mail-list-item.is-unread').count(), 14);
    await action('Archive').click();
    assert.equal(await editor.locator('.mail-list-item').count(), 14);
    await folder('Archive').click();
    assert.equal(await editor.locator('.mail-list-item').count(), 1);
    await editor.locator('.mail-list-item').click();
    assert.equal(await editor.locator('.mail-viewer h2').innerText(), selectedSubject);
    await action('Move to inbox').click();
    await folder('Inbox').click();
    await editor.locator('.mail-list-item').filter({hasText:'Postbank'}).click();
    const link = editor.locator('.mail-body .mail-link').first();
    await link.hover();
    const target = await link.getAttribute('title');
    assert((await editor.locator('.mail-status').innerText()).includes(target));
    const externalBefore = external.length;
    const pagesBefore = browser.contexts()[0].pages().length;
    await link.click();
    assert.equal(page.url(), origin + '/homepage/phishingWorkbook/index.html');
    assert.equal(browser.contexts()[0].pages().length, pagesBefore);
    assert.equal(external.length, externalBefore);
    assert((await editor.locator('.mail-status').innerText()).includes('Simulated link'));
    await action('Delete').click();
    await folder('Trash').click();
    assert.equal(await editor.locator('.mail-list-item').count(), 1);
    await page.locator('.fullscreen-close-button').click();
    await open.first().click();
    assert.equal(await editor.locator('.mail-list-item').count(), 14, 'reopening keeps inbox placement');
    await folder('Trash').click();
    assert.equal(await editor.locator('.mail-list-item').count(), 1);
    await folder('Inbox').click();
    // Render all original messages, checking that no active HTML or arbitrary URLs were attached.
    const downloads = [];
    page.on('download', download => downloads.push(download.suggestedFilename()));
    for (let i=0;i<14;i++) {
      await editor.locator('.mail-list-item').nth(i).click();
      assert.equal(await editor.locator('.mail-body a, .mail-body iframe, .mail-body script, .mail-body style, .mail-body [onclick], .mail-body [style]').count(), 0);
      for (const src of await editor.locator('.mail-body img').evaluateAll(nodes => nodes.map(n => n.src)))
        assert(src.startsWith(origin + '/resources/workbookresources/phishing/'));
      if (await editor.locator('.mail-attachment').count()) {
        await editor.locator('.mail-attachment').click();
        assert((await editor.locator('.mail-status').innerText()).includes('Simulated attachment'));
      }
    }
    assert.equal(downloads.length,0);
    await page.locator('.fullscreen-close-button').click();
    await open.nth(1).click();
    await action('New email').click();
    const draft = editor.locator('.mail-compose');
    await draft.getByRole('button',{name:'Send locally',exact:true}).click();
    assert((await editor.locator('.mail-status').innerText()).includes('valid email'));
    await draft.locator('input').nth(0).fill('friend@example.com');
    await draft.locator('input').nth(1).fill('Local practice');
    await draft.locator('textarea').fill('<script>this stays text</script>');
    await draft.getByRole('button',{name:'Send locally',exact:true}).click();
    assert.equal(await editor.locator('.mail-list-item').count(),1);
    assert.equal(await editor.locator('.mail-viewer h2').innerText(),'Local practice');
    assert((await editor.locator('.mail-body').innerText()).includes('<script>this stays text</script>'));
    await action('Reply').click();
    assert.equal(await draft.locator('input').nth(0).inputValue(),'opa.jürgen@gmail.com');
    assert.equal(await draft.locator('input').nth(1).inputValue(),'Re: Local practice');
    await draft.getByRole('button',{name:'Cancel',exact:true}).click();
    await action('Forward').click();
    assert.equal(await draft.locator('input').nth(0).inputValue(),'');
    assert.equal(await draft.locator('input').nth(1).inputValue(),'Fwd: Local practice');
    await draft.getByRole('button',{name:'Cancel',exact:true}).click();
    for (const viewport of [{width:1440,height:900},{width:390,height:844}]) {
      await page.setViewportSize(viewport);
      const bounds = await editor.evaluate(el => ({scroll:el.scrollWidth,width:el.clientWidth}));
      assert(bounds.scroll <= bounds.width+1,'mailbox fits the fullscreen width');
    }
    await page.locator('.fullscreen-close-button').click();
    // Wait for the application's asynchronous IndexedDB sync, rather than sleeping.
    await page.waitForFunction(async () => {
      const db = await new Promise((resolve,reject) => { const r=indexedDB.open('EvaDidInteractionDB'); r.onsuccess=()=>resolve(r.result); r.onerror=()=>reject(r.error); });
      const rows = await new Promise((resolve,reject) => { const r=db.transaction('variableHistoryStore').objectStore('variableHistoryStore').getAll(); r.onsuccess=()=>resolve(r.result); r.onerror=()=>reject(r.error); });
      db.close(); return rows.some(row => JSON.stringify(row).includes('Local practice'));
    });
    await page.reload();
    if (await login.isVisible()) await login.click();
    await page.locator('.section-block').first().click({timeout:60000});
    await page.getByText('Write emails – simulation',{exact:true}).waitFor();
    await open.nth(1).click();
    await folder('Sent').click();
    assert.equal(await editor.locator('.mail-list-item').count(),1,'sent mail survives a page reload');
    await page.locator('.fullscreen-close-button').click();
    await open.first().click();
    await folder('Trash').click();
    assert.equal(await editor.locator('.mail-list-item').count(),1,'triage survives a page reload');
    await folder('Inbox').click();
    await editor.getByRole('button',{name:'Check sorting',exact:true}).click();
    assert((await editor.locator('.mail-feedback').innerText()).includes('Sort all messages first'));
    const dataFile=await fs.readFile(path.join(root,'modules/client/src/main/scala/it/evadid/homepage/workbook/content/PhishingMailboxData.scala'),'utf8');
    const originals=JSON.parse(dataFile.split('private val data = """')[1].split('"""')[0]);
    for (const original of originals.filter(m=>m.senderName!=='Postbank')) {
      await editor.locator('.mail-list-item').filter({hasText:original.subject}).click();
      const targetAction={'Archiv':'Archive','Markiert':'Mark','Papierkorb':'Delete'}[original.expectedFolder];
      await action(targetAction).click();
    }
    assert((await editor.locator('.mail-feedback').innerText()).includes('All messages sorted correctly!'));
    await folder('Archive').click();
    await editor.locator('.mail-list-item').first().click();
    await action('Move to inbox').click();
    assert((await editor.locator('.mail-feedback').innerText()).includes('Sort all messages first'));
    await page.locator('.fullscreen-close-button').click();
    // Exercise the actual renderer with a restored, hostile authored body. The original
    // nodes must stay inert; external images and scripts cannot enter the live document.
    const hostile = `<script>window.mailAttack=true</script><iframe src="https://mail-attacker.invalid/frame"></iframe>
      <svg onload="window.mailAttack=true"></svg><form action="https://mail-attacker.invalid/send"><input></form>
      <a href="javascript:window.mailAttack=true" onclick="window.mailAttack=true">Hostile link</a>
      <img src="https://mail-attacker.invalid/pixel" onerror="window.mailAttack=true" alt="Blocked remote image">
      <img src="pics/telekom.png" onload="window.mailAttack=true" style="position:fixed">
      <div style="background:url(https://mail-attacker.invalid/css)">Safe content</div>`;
    const changed = await page.evaluate(async body => {
      const db = await new Promise((resolve,reject) => { const r=indexedDB.open('EvaDidInteractionDB'); r.onsuccess=()=>resolve(r.result); r.onerror=()=>reject(r.error); });
      const rows = await new Promise((resolve,reject) => { const r=db.transaction('variableHistoryStore').objectStore('variableHistoryStore').getAll(); r.onsuccess=()=>resolve(r.result); r.onerror=()=>reject(r.error); });
      const row = rows.find(row => row.value.includes('phishing-01'));
      if (!row) { db.close(); return false; }
      function change(value) {
        if (typeof value === 'string') {
          try { return JSON.stringify(change(JSON.parse(value))); } catch { return value; }
        }
        if (Array.isArray(value)) return value.map(change);
        if (value && typeof value === 'object') {
          if (Array.isArray(value.mailList)) {
            const mail=value.mailList.find(m => m.id==='phishing-01');
            if (mail) { mail.body=body; mail.bodyHtml=true; mail.subject='HTML safety example'; mail.realFolder='Posteingang'; }
          }
          return Object.fromEntries(Object.entries(value).map(([key,v])=>[key,change(v)]));
        }
        return value;
      }
      row.value=JSON.stringify(change(JSON.parse(row.value)));
      await new Promise((resolve,reject) => { const tx=db.transaction('variableHistoryStore','readwrite'); tx.objectStore('variableHistoryStore').put(row); tx.oncomplete=resolve; tx.onerror=()=>reject(tx.error); });
      db.close(); return true;
    }, hostile);
    assert(changed,'stored mailbox is available for renderer safety testing');
    await page.reload();
    if (await login.isVisible()) await login.click();
    await page.locator('.section-block').first().click({timeout:60000});
    await page.getByText('Write emails – simulation',{exact:true}).waitFor();
    await open.first().click();
    await editor.locator('.mail-list-item').filter({hasText:'HTML safety example'}).click();
    assert((await editor.locator('.mail-body').innerText()).includes('Safe content'));
    assert.equal(await editor.locator('.mail-body script, .mail-body iframe, .mail-body svg, .mail-body form, .mail-body input, .mail-body [style], .mail-body [onclick], .mail-body [onload]').count(),0);
    assert.equal(await editor.locator('.mail-body img').count(),1,'only the approved local image remains');
    await editor.locator('.mail-link').click();
    assert.equal(await page.evaluate(()=>window.mailAttack),undefined);
    assert.equal(external.filter(url=>url.includes('mail-attacker.invalid')).length,0,'inert source cannot fetch remote resources');
    assert.equal(downloads.length,0);
    assert.deepEqual(errors,[],'no browser runtime errors');
  } finally { await browser.close(); }
});
