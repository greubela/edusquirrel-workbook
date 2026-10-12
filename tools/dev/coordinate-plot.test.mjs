import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html', '.js':'text/javascript', '.mjs':'text/javascript', '.css':'text/css', '.json':'application/json', '.svg':'image/svg+xml', '.png':'image/png'};

test('coordinate plots preserve hypotheses and observations, validate drafts and use CSS on mobile', async () => {
  await fs.access(path.join(root, 'artifacts/newest/client.js'));
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium', args:['--no-sandbox']});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:900}});
    page.setDefaultTimeout(60000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      const file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
      if (!file.startsWith(root + path.sep)) return route.abort();
      try { await route.fulfill({body:await fs.readFile(file), contentType:mime[path.extname(file)] || 'application/octet-stream'}); }
      catch { await route.fulfill({status:404,body:'Not found'}); }
    });
    await page.goto(origin + '/homepage/evacuationWorkbook/index.html');
    const login = page.locator('.section-register .login-area').nth(1).locator('button');
    await login.click();
    const chapter = page.locator('.section-block').filter({hasText:'How does door width affect evacuation time?'});
    await chapter.click();
    const plots = page.locator('.coordinate-plot');
    await plots.first().waitFor();
    assert.equal(await plots.count(),2);
    const predicted = plots.first(), observed = plots.last();
    const add = plot => plot.getByRole('button',{name:'Add or replace point'});
    async function point(plot,x,y) {
      await plot.getByRole('textbox',{name:'Door width (m)'}).fill(x);
      await plot.getByRole('textbox',{name:'Evacuation time (s)'}).fill(y);
      await add(plot).click();
    }
    await predicted.getByRole('textbox',{name:'Door width (m)'}).fill('-');
    await predicted.getByRole('textbox',{name:'Evacuation time (s)'}).fill('12');
    assert(await add(predicted).isDisabled());
    await predicted.getByRole('textbox',{name:'Door width (m)'}).fill('3.1');
    assert(await add(predicted).isDisabled(),'out-of-axis values are rejected rather than clipped');
    await point(predicted,'1,2','10,5');
    assert.equal(await predicted.locator('circle').count(),1);
    assert(Math.abs(Number(await predicted.locator('circle').first().getAttribute('cx')) - 268) < 1e-6);
    assert(Math.abs(Number(await predicted.locator('circle').first().getAttribute('cy')) - 215) < 1e-6);
    await point(predicted,'2','8');
    assert.equal(await predicted.locator('.coordinate-plot__curve').count(),1);
    await point(predicted,'1.2','12');
    assert.equal(await predicted.locator('circle').count(),2,'an existing x coordinate replaces its y value');
    await predicted.getByRole('checkbox',{name:'Connect points'}).uncheck();
    assert.equal(await predicted.locator('.coordinate-plot__curve').count(),0);
    await point(observed,'1','6');
    assert.equal(await observed.locator('circle').count(),1);
    assert.equal(await predicted.locator('circle').count(),2,'observations do not replace predictions');
    const choices = page.locator('.choice-interaction');
    await choices.first().getByRole('radio',{name:'Inverse proportionality',exact:true}).check();
    await choices.last().getByRole('radio',{name:/Another relationship/}).check();
    await predicted.getByRole('button',{name:'Remove point'}).last().click();
    assert.equal(await predicted.locator('circle').count(),1);
    await point(predicted,'2','7');
    const presentation = await predicted.locator('circle').first().evaluate(el=>{
      const s=getComputedStyle(el); return {r:s.r,fill:s.fill};
    });
    assert.equal(presentation.r,'5px');
    assert.notEqual(presentation.fill,'rgb(0, 0, 0)');
    const custom = await page.addStyleTag({content:':root { --color-plot-data: rgb(123, 45, 67); --plot-point-radius: 8px; --plot-stroke-width: 3px; }'});
    assert.deepEqual(await predicted.locator('circle').first().evaluate(el=>({fill:getComputedStyle(el).fill,r:getComputedStyle(el).r})),
      {fill:'rgb(123, 45, 67)',r:'8px'},'shared CSS variables control point presentation');
    assert.equal(await predicted.locator('.coordinate-plot__axes').evaluate(el=>getComputedStyle(el).strokeWidth),'3px');
    await custom.evaluate(el=>el.remove());
    assert.equal(await plots.locator('[style], style').count(),0);
    await page.setViewportSize({width:390,height:844});
    assert(await predicted.evaluate(el=>{const b=el.getBoundingClientRect();return b.left>=0 && b.right<=innerWidth+1;}));
    await page.waitForFunction(async () => {
      const db = await new Promise((resolve,reject) => {const r=indexedDB.open('EvaDidInteractionDB');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
      const rows = await new Promise((resolve,reject) => {const r=db.transaction('variableHistoryStore').objectStore('variableHistoryStore').getAll();r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
      db.close(); return rows.some(row => String(row.value).includes('"y":12'));
    });
    await page.reload();
    if (await login.isVisible()) await login.click();
    await chapter.click();
    await predicted.waitFor();
    assert.equal(await predicted.locator('circle').count(),2);
    assert.equal(await observed.locator('circle').count(),1);
    assert.equal(await predicted.locator('tbody tr').first().locator('td').nth(1).innerText(),'12');
    assert(!(await predicted.getByRole('checkbox',{name:'Connect points'}).isChecked()));
    assert(await choices.first().getByRole('radio',{name:'Inverse proportionality',exact:true}).isChecked());
    assert(await choices.last().getByRole('radio',{name:/Another relationship/}).isChecked());
    assert.deepEqual(errors,[]);
  } finally { await browser.close(); }
});
