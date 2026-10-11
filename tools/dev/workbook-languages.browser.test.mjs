import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const entries = [
  ['evacuationWorkbook', 'digitalWorkbooks', 'evacuationWorkbookTitle'],
  ['plantWorkshopWorkbook', 'plantworkshop', 'workbookTitle'],
  ['blockchainWorkbook', 'blockchainworkbook', 'title'],
  ['imageRecognitionWorkbook', 'digitalWorkbooks', 'workbookTitle'],
  ['monksWorkbook', 'monksworkbook', 'workbooktitle'],
  ['embroideryWorkbook', 'embroideryworkbook', 'workbookTitle'],
  ['embroideryPreviewWorkbook', 'embroiderypreviewworkbook', 'workbookTitle'],
  ['compressionWorkbook', 'compressionworkbook', 'workbookTitle'],
  ['phishingWorkbook', 'emailSimulator', 'workbookTitle']
];
const mime = {'.html':'text/html', '.js':'text/javascript', '.css':'text/css', '.json':'application/json',
  '.svg':'image/svg+xml', '.png':'image/png', '.jpg':'image/jpeg', '.txt':'text/plain'};
async function workbookPage(browser, entry) {
  const page = await browser.newPage({viewport:{width:1440,height:1000}});
  page.setDefaultTimeout(60000);
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.origin !== origin) return route.abort();
    if (url.pathname.endsWith('/.env')) return route.fulfill({body:'EDUSQUIRREL_BACKEND_MODULE_WORKERS=0',contentType:'text/plain'});
    const file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
    if (!file.startsWith(root + path.sep)) return route.abort();
    try { await route.fulfill({body:await fs.readFile(file),contentType:mime[path.extname(file)] || 'application/octet-stream'}); }
    catch { await route.fulfill({status:404,body:'Not found'}); }
  });
  await page.goto(`${origin}/homepage/${entry}/index.html`);
  await page.locator('.section-register .login-area').nth(1).locator('button').click();
  return page;
}

test('all nine native digital workbook pages can switch between complete German and English headings', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',args:['--no-sandbox']});
  try {
    for (const [entry, directory, key] of entries) {
      const page = await workbookPage(browser, entry);
      try {
        for (const [language, index] of [['de','german-red'], ['en','british-blue'], ['de','german-red']]) {
          const map = JSON.parse(await fs.readFile(path.join(root, `resources/languageMaps/eva/${directory}/map-${language}.json`),'utf8'));
          const title = map[key];
          assert(title, `${entry}: ${language} title exists`);
          await page.locator(`.select-language-line > div:has(.flag-fill-${index})`).click();
          await page.getByRole('heading',{name:title,exact:true}).waitFor();
          assert((await page.locator('.section-block').count()) > 0);
        }
      } finally { await page.close(); }
    }
  } finally { await browser.close(); }
});

test('English compression chapters and controls render translated content and preserve edits across a language change', async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',args:['--no-sandbox']});
  try {
    const page = await workbookPage(browser, 'compressionWorkbook');
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').click();
    const map = JSON.parse(await fs.readFile(path.join(root,'resources/languageMaps/eva/compressionworkbook/map-en.json'),'utf8'));
    const headings = ['intro','section0','lossless','losslessOptional','lossy','filetypes','final'];
    for (let i=0; i<headings.length; i++) {
      const heading = map[`src_${headings[i]}_heading`];
      await page.locator('.section-block').nth(i).click();
      assert((await page.locator('body').innerText()).includes(heading), heading);
    }
    await page.locator('.section-block').nth(1).click();
    await page.locator('.compression-preview').first().getByRole('button',{name:'Open experiment'}).click();
    const editor = page.locator('dialog[open] .compression-editor');
    await editor.getByLabel('Number of videos', {exact:true}).fill('10');
    assert.match(await editor.locator('.compression-summary').innerText(), /1875\.00 MB/);
    await page.locator('.fullscreen-close-button').click();
    await page.locator('.select-language-line > div:has(.flag-fill-german-red)').click();
    await page.getByRole('heading',{name:'Datenkompression und gesellschaftliche Verantwortung',exact:true}).waitFor();
    await page.locator('.compression-preview').first().getByRole('button',{name:'Experiment öffnen'}).click();
    assert.equal(await editor.getByLabel('Anzahl der Videos', {exact:true}).inputValue(),'10');
    await page.locator('.fullscreen-close-button').click();
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').click();
    await page.locator('.section-block').nth(4).click();
    await page.locator('.compression-preview').first().getByRole('button',{name:'Open experiment'}).click();
    assert.equal(await editor.getByRole('button',{name:'Flip one bit',exact:true}).count(),2);
    assert((await editor.innerText()).includes('0 bits changed'));
    await editor.getByRole('button',{name:'Flip one bit',exact:true}).last().click();
    await page.waitForFunction(() => document.querySelector('dialog[open] .compression-editor')?.textContent.includes('1 bit changed:'));
    await page.locator('.fullscreen-close-button').click();
    await page.locator('.section-block').nth(6).click();
    await page.locator('.compression-preview').nth(1).getByRole('button',{name:'Open experiment'}).click();
    await editor.getByRole('button',{name:'Compress lossily',exact:true}).click();
    assert.equal(await editor.getByRole('columnheader',{name:'Metadata / file type',exact:true}).count(),1);
    assert((await editor.innerText()).includes('Problematic metadata:'));
    assert((await editor.innerText()).includes('Practise the controls'));

  } finally { await browser.close(); }
});
