import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {chromium} from 'playwright';

const read = path => readFile(new URL('../../' + path, import.meta.url), 'utf8');

test('two-column descriptions stay equal, wrap long text and stack on small screens', async () => {
  const browser = await chromium.launch({
    executablePath: process.env.CHROMIUM_PATH || chromium.executablePath(), args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage({viewport: {width: 1000, height: 800}});
    const css = (await Promise.all([
      'homepage/css/generic/dimensions.css', 'homepage/css/workbook/workbook-structure.css'
    ].map(read))).join('\n').replace(/^@import[^;]+;/gm, '');
    await page.setContent(`<style>${css}</style><div class="workbook-exercise-group">
      <div>Image</div><div class="workbook-two-column-panel">
      <div>Wiring ${'x'.repeat(300)}</div><div>Explanation</div></div></div>`);
    const boxes = () => page.locator('.workbook-two-column-panel > div').evaluateAll(elements =>
      elements.map(e => { const r = e.getBoundingClientRect(); return {x:r.x, y:r.y, width:r.width, bottom:r.bottom}; }));
    const wide = await boxes();
    assert.equal(wide[0].y, wide[1].y);
    assert(Math.abs(wide[0].width - wide[1].width) < 1);
    assert(wide[1].x > wide[0].x + wide[0].width);
    await page.setViewportSize({width: 375, height: 800});
    const narrow = await boxes();
    assert.equal(narrow[0].x, narrow[1].x);
    assert(narrow[1].y > narrow[0].bottom);
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  } finally { await browser.close(); }
});

test('restored wiring descriptions exist in both PlantWorkshop languages', async () => {
  for (const language of ['de', 'en']) {
    const entries = JSON.parse(await read(`resources/languageMaps/eva/plantworkshop/map-${language}.json`));
    const keys = ['LLabel', 'RLabel', 'wiringSlideHelp', 'wiringSlideCurrentStatus'];
    for (let slide = 1; slide <= 11; slide++) {
      keys.push(...([3,4,8].includes(slide)
        ? [`wiringSlideTextL${slide}`, `wiringSlideTextR${slide}`] : [`wiringSlideText${slide}`]));
    }
    for (const key of keys) assert(entries[key]?.trim(), `${language}: missing ${key}`);
  }
});

test('PlantWorkshop slides render grouped images and descriptions through the workbook factory', async () => {
  const browser = await chromium.launch({
    executablePath: process.env.CHROMIUM_PATH || chromium.executablePath(), args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage({viewport: {width: 1440, height: 1000}});
    const origin = 'http://localhost:9000';
    const mime = {html:'text/html', js:'text/javascript', css:'text/css', json:'application/json', svg:'image/svg+xml', png:'image/png', jpg:'image/jpeg'};
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      const relative = decodeURIComponent(url.pathname).slice(1);
      if (relative.split('/').includes('..')) return route.abort();
      try {
        const file = relative === 'homepage/js/CodeMirrorLoader.js' && process.env.CODEMIRROR_TEST_BUNDLE
          ? process.env.CODEMIRROR_TEST_BUNDLE : new URL('../../' + relative, import.meta.url);
        await route.fulfill({body: await readFile(file),
          contentType: mime[relative.split('.').at(-1)] || 'application/octet-stream'});
      } catch { await route.fulfill({status: 404, body: 'Not found'}); }
    });
    await page.goto(origin + '/homepage/plantWorkshopWorkbook/index.html');
    await page.locator('.section-register .login-area').nth(1).locator('button').click({timeout: 60000});
    for (const [section, count, columnSlides] of [[1, 5, [3, 4]], [4, 6, [3]]]) {
      await page.locator('.section-block').nth(section).click();
      await page.waitForFunction(expected => document.querySelector('.slide-deck-counter')?.textContent === expected, `1/${count}`);
      const deck = page.locator('.slide-deck-container').first();
      for (let slide = 1; slide <= count; slide++) {
        await page.waitForFunction(() => document.querySelector('.workbook-exercise-group')?.innerText.trim().length > 30);
        const group = deck.locator('.workbook-exercise-group');
        assert.equal(await group.count(), 1);
        await group.locator('img').waitFor();
        assert.equal(await group.locator('img').count(), 1);
        assert.equal(await group.locator('.workbook-two-column-panel').count(), columnSlides.includes(slide) ? 1 : 0);
        assert((await group.innerText()).trim().length > 30, 'localized description is visible');
        assert(!/Cannot render|cannot yet render/.test(await deck.innerText()));
        if (slide < count) {
          await page.locator('.slide-deck-navigation button').last().click();
          await page.waitForFunction(expected => document.querySelector('.slide-deck-counter')?.textContent === expected, `${slide + 1}/${count}`);
        }
      }
    }
  } finally { await browser.close(); }
});
