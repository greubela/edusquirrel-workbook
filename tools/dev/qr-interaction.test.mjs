import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';
import jsQR from 'jsqr';

const root = path.resolve(import.meta.dirname, '../..');
const mime = {'.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png'};

test('QR interaction edits, grades, rejects oversized drafts and restores the saved preview', async () => {
  // Requires sbt buildJS. Every request is served locally or aborted; no backend/account is needed.
  await fs.access(path.join(root, 'artifacts/newest/client.js'));
  const browser = await chromium.launch({executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium', args: ['--no-sandbox']});
  try {
    const page = await browser.newPage();
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== 'http://localhost:9000') return route.abort();
      const file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
      if (!file.startsWith(root + path.sep)) return route.abort();
      try {
        await route.fulfill({body: await fs.readFile(file), contentType: mime[path.extname(file)] || 'application/octet-stream'});
      } catch { await route.fulfill({status: 404, body: 'Not found'}); }
    });
    await page.goto('http://localhost:9000/homepage/workbookDesign/index.html');
    await page.locator('.section-register .login-area').nth(1).locator('button').click({timeout: 60000});
    await page.locator('.section-block').nth(2).click();
    const preview = page.locator('.element-card-content > .qr-preview');
    await preview.waitFor();
    assert.equal(await preview.locator('.qr-requirement.is-passed').count(), 1, 'empty code has not met the byte requirement');
    await page.getByRole('button', {name: 'Open editor', exact: false}).click();
    const editor = page.locator('.qr-editor');
    await editor.waitFor();
    const text = 'Grüße 🐿 — a payload longer than thirty-two bytes';
    await editor.locator('textarea').fill(text);
    assert.equal(await editor.locator('.qr-requirement.is-passed').count(), 2);
    const code = editor.locator('.qr-symbol');
    const paths = locator => locator.locator('path').evaluateAll(nodes => nodes.map(n => n.getAttribute('d')).join(''));
    const validPath = await paths(code);
    assert(validPath.startsWith('M4,4'), 'finder has the required quiet zone');
    const palette = await code.evaluate(svg => ({background: getComputedStyle(svg).backgroundColor, fill: getComputedStyle(svg).fill}));
    assert.deepEqual(palette, {background: 'rgb(255, 255, 255)', fill: 'rgb(0, 0, 0)'});
    assert(await editor.locator('.qr-legend').innerText());
    const formatPath = code.locator('.qr-region--format');
    const encodingPath = code.locator('.qr-region--encoding');
    const formatFill = await formatPath.evaluate(el => getComputedStyle(el).fill);
    const encodingFill = await encodingPath.evaluate(el => getComputedStyle(el).fill);
    assert.notEqual(formatFill, encodingFill, 'format and encoding regions have distinct default colors');
    await code.evaluate(el => el.style.setProperty('--color-qr-format', 'rgb(12, 34, 56)'));
    assert.equal(await formatPath.evaluate(el => getComputedStyle(el).fill), 'rgb(12, 34, 56)', 'CSS token customizes format modules');
    assert.equal(await encodingPath.evaluate(el => getComputedStyle(el).fill), encodingFill, 'other regions retain their colors');
    await code.evaluate(el => el.style.removeProperty('--color-qr-format'));
    const raster = await code.evaluate(async svg => {
      const copy = svg.cloneNode(true);
      copy.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
      copy.setAttribute('fill', getComputedStyle(svg).fill);
      copy.querySelectorAll('path').forEach((p, i) => p.setAttribute('fill', getComputedStyle(svg.querySelectorAll('path')[i]).fill));
      const size = svg.viewBox.baseVal.width * 8;
      copy.setAttribute('width', size);
      copy.setAttribute('height', size);
      const image = new Image();
      image.src = 'data:image/svg+xml,' + encodeURIComponent(copy.outerHTML);
      await image.decode();
      const canvas = document.createElement('canvas');
      canvas.width = canvas.height = size;
      const ctx = canvas.getContext('2d');
      ctx.fillStyle = getComputedStyle(svg).backgroundColor;
      ctx.fillRect(0, 0, size, size);
      ctx.drawImage(image, 0, 0, size, size);
      return {size, data: Array.from(ctx.getImageData(0, 0, size, size).data)};
    });
    const decoded = jsQR(Uint8ClampedArray.from(raster.data), raster.size, raster.size);
    assert.equal(decoded?.data, text, 'independent scanner decodes the rendered QR to its UTF-8 text');
    await editor.locator('select').nth(1).selectOption('1');
    assert.equal(await editor.locator('.qr-symbol').count(), 0, 'invalid draft cannot show a stale QR symbol');
    assert(await editor.getByRole('alert').innerText());
    await editor.locator('select').nth(1).selectOption('');
    assert.equal(await paths(editor.locator('.qr-symbol')), validPath);
    await page.locator('.fullscreen-close-button').click();
    assert.equal(await paths(preview.locator('.qr-symbol')), validPath);
    await page.getByRole('button', {name: 'Open editor', exact: false}).click();
    assert.equal(await editor.locator('textarea').inputValue(), text);
    for (const viewport of [{width: 1440, height: 900}, {width: 390, height: 844}]) {
      await page.setViewportSize(viewport);
      const bounds = await editor.evaluate(el => {
        const rect = el.getBoundingClientRect();
        const symbol = el.querySelector('.qr-symbol').getBoundingClientRect();
        return {left: rect.left, right: rect.right, scroll: el.scrollWidth, width: el.clientWidth, symbolLeft: symbol.left, symbolRight: symbol.right};
      });
      assert(bounds.scroll <= bounds.width + 1, 'editor must not overflow horizontally');
      assert(bounds.symbolLeft >= bounds.left && bounds.symbolRight <= bounds.right, 'QR preview fits the editor');
    }
  } finally { await browser.close(); }
});
