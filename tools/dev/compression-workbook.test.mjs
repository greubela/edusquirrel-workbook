import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const bundle = path.resolve(root, process.env.COMPRESSION_CLIENT_BUNDLE || 'target/client/scala-3.8.4/client-fastopt/main.js');
const mime = {'.html':'text/html', '.js':'text/javascript', '.mjs':'text/javascript', '.css':'text/css', '.json':'application/json',
  '.svg':'image/svg+xml', '.png':'image/png', '.jpg':'image/jpeg', '.txt':'text/plain'};

test('native compression experiments use bound answers and survive fullscreen, chapters and reload', async () => {
  await fs.access(bundle);
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium', args:['--no-sandbox']});
  let page;
  try {
    page = await browser.newPage({viewport:{width:1440,height:900}});
    page.setDefaultTimeout(45000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      if (url.pathname.endsWith('/.env')) return route.fulfill({body:'EDUSQUIRREL_BACKEND_MODULE_WORKERS=0',contentType:'text/plain'});
      const file = url.pathname === '/artifacts/newest/client.js' ? bundle : path.resolve(root, '.' + decodeURIComponent(url.pathname));
      if (!file.startsWith(root + path.sep)) return route.abort();
      try { await route.fulfill({body:await fs.readFile(file),contentType:mime[path.extname(file)] || 'application/octet-stream'}); }
      catch { await route.fulfill({status:404,body:'Not found'}); }
    });
    await page.goto(origin + '/homepage/compressionWorkbook/index.html');
    const login = page.locator('.section-register .login-area').nth(1).locator('button');
    await login.click();
    const chapter = text => page.locator('.section-block').filter({hasText:text});
    const editor = page.locator('dialog[open] .compression-editor');
    const close = async () => page.locator('.fullscreen-close-button').click();
    const open = async index => {
      await page.locator('.compression-preview').nth(index).locator('button').click();
      await editor.waitFor();
    };
    await page.locator('.compression-preview').first().waitFor();
    assert(!/Kommt bald|\[ Widget:/.test(await page.locator('body').innerText()));
    await open(0);
    await editor.getByLabel('Anzahl der Videos').fill('10');
    assert.match(await editor.locator('.compression-summary').innerText(), /1875\.00 MB/);
    await editor.getByLabel('Anzahl der Videos').fill('0');
    assert.equal(await editor.locator('[aria-invalid="true"]').count(), 1);
    assert.match(await editor.locator('.compression-summary').innerText(), /1875\.00 MB/, 'invalid draft preserves accepted value');
    await close();
    await open(0);
    assert.equal(await editor.getByLabel('Anzahl der Videos').inputValue(), '10');
    await close();
    await chapter('Sektion 1').click();
    await page.getByText('Ein Bit im Passwort ändern',{exact:true}).waitFor();
    await page.locator('.compression-preview').first().waitFor();
    await open(0);
    await editor.locator('.compression-byte button').first().click();
    assert.equal(await editor.locator('.compression-bit-selected').count(), 1);
    await editor.locator('.compression-byte button').nth(9).click();
    assert.equal(await editor.locator('.compression-bit-selected').count(), 1, 'only one password bit can differ');
    await close();
    await open(1);
    await editor.locator('textarea').fill('AAAAAAAAAAAA');
    assert.match(await editor.locator('.compression-output').first().innerText(), /\(A,12\)/);
    assert.match(await editor.locator('.compression-summary').innerText(), /12 UTF-8-Byte.*3 Byte/);
    await close();
    await open(2);
    assert.match(await editor.locator('textarea').inputValue(), /Die Daten bleiben geheim/,'prose experiment has independent state');
    await close();
    await open(3);
    await editor.getByRole('button',{name:'Alle Schritte',exact:true}).click();
    assert.match(await editor.locator('.compression-output').last().innerText(), /Die Daten bleiben geheim/);
    assert.equal(await editor.getByRole('button',{name:'Nächster Schritt',exact:true}).isDisabled(), true);
    await close();
    await page.locator('.pixel-cell').first().click();
    await chapter('Sektion 2').click();
    await page.getByText('Helligkeit und Farbe vergleichen',{exact:true}).waitFor();
    await page.locator('.compression-preview').first().waitFor();
    await open(0);
    await page.waitForFunction(() => {
      const canvas=document.querySelector('dialog[open] .compression-images canvas');
      return canvas && canvas.getContext('2d').getImageData(0,0,1,1).data[3]===255;
    });
    assert.equal(await editor.locator('select').count(),2);
    const original = await editor.locator('canvas').evaluate(c => c.toDataURL());
    await editor.getByLabel('Farbe: Blockgröße').selectOption('8');
    await editor.getByLabel('Helligkeit: Blockgröße').selectOption('4');
    assert.notEqual(await editor.locator('canvas').evaluate(c => c.toDataURL()), original);
    await close();
    await open(1);
    assert.equal(await editor.locator('select').count(),1);
    await editor.locator('select').selectOption('64');
    assert.match(await editor.locator('.compression-summary').innerText(), /Modellgröße: 3 Byte/);
    await close();
    await chapter('Sektion 3').click();
    await page.locator('.compression-file-comparison').waitFor();
    assert.equal(await page.locator('.compression-file-comparison tbody tr').count(),4);
    assert.equal(await page.getByRole('link',{name:/Word-Datei \(2545 Byte\)/}).count(),1);
    await open(0);
    await editor.getByLabel('Verwaltungsaufwand pro Datei').fill('0');
    assert.match(await editor.locator('.compression-summary').innerText(), /10\.00 s.*10\.31 s/);
    await close();
    await chapter('Sektion 4').click();
    await page.getByText('Datenpakete untersuchen',{exact:true}).waitFor();
    await page.locator('.compression-preview').first().waitFor();
    await page.locator('textarea:visible').first().fill('Zuerst die großen Gruppen prüfen; verschlüsselte Daten unverändert erhalten.');
    await open(0);
    await editor.getByLabel('Datenpaket').selectOption('2');
    assert.equal(await editor.locator('tbody tr').count(),5);
    assert.match(await editor.locator('.compression-summary').innerText(), /Fehlender Platz: 10000 MB/);
    await close();
    await page.waitForFunction(async () => {
      const db=await new Promise((resolve,reject)=>{const r=indexedDB.open('EvaDidInteractionDB');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
      const rows=await new Promise((resolve,reject)=>{const r=db.transaction('variableHistoryStore').objectStore('variableHistoryStore').getAll();r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
      db.close(); return rows.some(row=>JSON.stringify(row).includes('verschlüsselte Daten unverändert'));
    });
    await page.reload();
    if (await login.isVisible()) await login.click();
    await chapter('Sektion 1').click();
    await page.getByText('Ein Bit im Passwort ändern',{exact:true}).waitFor();
    await page.locator('.compression-preview').first().waitFor();
    await open(1);
    assert.equal(await editor.locator('textarea').inputValue(),'AAAAAAAAAAAA');
    await close();
    await chapter('Sektion 4').click();
    await page.getByText('Datenpakete untersuchen',{exact:true}).waitFor();
    await page.locator('.compression-preview').first().waitFor();
    assert.match(await page.locator('textarea:visible').first().inputValue(),/verschlüsselte Daten unverändert/);
    await open(0);
    assert.equal(await editor.getByLabel('Datenpaket').inputValue(),'2');
    await page.setViewportSize({width:390,height:844});
    assert(await editor.evaluate(e=>e.scrollWidth<=e.clientWidth+1),'mobile editor fits its viewport');
    assert.equal(await page.locator('iframe').count(),0);
    assert.deepEqual(errors,[]);
  } catch (error) {
    if (page) {
      await fs.mkdir(path.join(root,'work'),{recursive:true});
      await page.screenshot({path:path.join(root,'work/compression-browser-failure.png'),fullPage:true});
      await fs.writeFile(path.join(root,'work/compression-browser-failure.txt'),await page.locator('body').innerText());
    }
    throw error;
  } finally { await browser.close(); }
});
