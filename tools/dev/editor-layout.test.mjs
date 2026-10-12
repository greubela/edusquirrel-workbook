import assert from 'node:assert/strict';
import { readFile, access } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { chromium } from 'playwright';

const homepage = fileURLToPath(new URL('../../homepage/', import.meta.url));
let executablePath = process.env.CHROMIUM_PATH;
if (!executablePath) {
  try { await access('/usr/bin/chromium'); executablePath = '/usr/bin/chromium'; } catch { /* Use Playwright's browser. */ }
}

// Exercise the production stylesheet cascade with the editors' DOM structure.
// No remote CodeMirror/Snap runtime is needed for these layout regressions.
const fixture = (language, preview = true, standalone = false) => {
  const editor = language === 'Snap'
    ? '<div class="code-editor-container be-program-snap-fullscreen"><div class="code-editor be-program-snap-renderer be-program-snap-renderer--editor"><canvas class="be-program-snap-renderer__canvas"></canvas></div></div>'
    : `<div class="code-mirror-editor">${language === 'Fallback'
      ? '<textarea class="code-mirror-editor__fallback">forward(100)</textarea>'
      : '<textarea class="code-mirror-editor__fallback" hidden></textarea><div class="cm-editor"><div class="cm-scroller"><pre>forward(100)\nturn_right(15)</pre></div></div>'}</div>`;
  return `<!doctype html><html><head><link rel="stylesheet" href="/css/custom-elements.css"><link rel="stylesheet" href="/css/workbook/workbook-structure.css"><link rel="stylesheet" href="/css/workbook/workbook-header.css"><link rel="stylesheet" href="/css/workbook/workbook-interactions.css"><link rel="stylesheet" href="/css/JavaFunctionEditor.css"></head><body>
    <dialog class="fullscreen-overlay-dialog"><div class="fullscreen-content-container">${standalone ? editor : `
      <div class="eva-editor"><div class="eva-editor__tabs">
        <button type="button" aria-pressed="false">Snap</button><button type="button" aria-pressed="true">Python</button><button type="button" disabled>Java</button>
      </div><div class="eva-editor__content">${editor}</div>${preview ? '<div class="turtle-sidebar"><div class="element-card"><h3 class="element-card-label">Preview (Turtle)</h3><div class="element-card-content"><div class="turtle-gradig-panel"><svg viewBox="0 0 200 200"><path d="M20 180H180V20" fill="none" stroke="currentColor"/></svg></div></div></div></div>' : ''}</div>`}
    </div><button class="fullscreen-close-button" aria-label="Exit full screen">×</button></dialog>
    <script>document.querySelector('dialog').showModal()</script></body></html>`;
};

test('editor tabs stay compact and panels fit on desktop, mobile and short screens', async () => {
  const browser = await chromium.launch({ executablePath, headless: true, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage();
    let html;
    await page.route('http://editor.test/**', async route => {
      const pathname = new URL(route.request().url()).pathname;
      if (pathname === '/') return route.fulfill({ contentType: 'text/html; charset=utf-8', body: html });
      return route.fulfill({ contentType: 'text/css', body: await readFile(homepage + pathname, 'utf8') });
    });
    for (const viewport of [{width: 1440, height: 900}, {width: 390, height: 844}, {width: 844, height: 390}]) {
      await page.setViewportSize(viewport);
      for (const language of ['Snap', 'Python', 'Fallback']) {
        for (const preview of [true, false]) {
          html = fixture(language, preview);
          await page.goto('http://editor.test/');
          const geometry = await page.evaluate(() => {
            const box = selector => document.querySelector(selector)?.getBoundingClientRect().toJSON();
            return {tabs: box('.eva-editor__tabs'), lastTab: box('.eva-editor__tabs > button:last-child'), content: box('.eva-editor__content'),
              sidebar: box('.turtle-sidebar'), board: box('.turtle-gradig-panel'), shell: box('.fullscreen-content-container'),
              editor: box('.cm-editor, textarea:not([hidden]), canvas'), close: box('.fullscreen-close-button')};
          });
          assert(geometry.tabs.height < 60, 'tabs must not consume the editor height');
          assert(geometry.content.height > 80, 'editing area remains usable');
          for (const panel of [geometry.content, geometry.sidebar, geometry.editor].filter(Boolean)) {
            assert(panel.left >= geometry.shell.left && panel.right <= geometry.shell.right + 1, 'no horizontal overflow');
            assert(panel.bottom <= geometry.shell.bottom + 1, 'panels fit the dialog height');
          }
          if (preview) assert(geometry.board.width <= geometry.sidebar.width, 'preview graph fits its panel');
          assert(geometry.lastTab.right <= geometry.close.left, 'tabs reserve space for the close control');
          if (preview && viewport.width > 860) assert(geometry.sidebar.left >= geometry.content.right, 'desktop preview sits beside editor');
          if (preview && viewport.width <= 860) assert(geometry.sidebar.top >= geometry.content.bottom, 'mobile preview sits below editor');
          if (language === 'Python') assert.equal(await page.locator('textarea[hidden]').isVisible(), false, 'CodeMirror hides its fallback');
          if (language === 'Snap') {
            const dimensions = await page.locator('canvas').evaluate(canvas => {
              const style = getComputedStyle(canvas);
              return ['--snap-category-row-gap', '--snap-category-border', '--snap-category-padding', '--snap-category-label-growth']
                .map(name => style.getPropertyValue(name).trim());
            });
            assert(dimensions.every(value => /^\d+(\.\d+)?px$/.test(value)), 'canvas category dimensions resolve to CSS pixels');
          }
          if (process.env.EDITOR_SCREENSHOT && viewport.width === 1440 && language === 'Python' && preview) await page.screenshot({path: process.env.EDITOR_SCREENSHOT});
          await page.locator('.eva-editor__tabs > button').first().focus();
          assert.equal(await page.locator('.eva-editor__tabs > button').first().evaluate(el => getComputedStyle(el).outlineStyle), 'solid');
        }
      }
      html = fixture('Snap', false, true);
      await page.goto('http://editor.test/');
      const canvas = await page.locator('canvas').boundingBox();
      const close = await page.locator('.fullscreen-close-button').boundingBox();
      assert(canvas.width > viewport.width * 0.8 && canvas.height > viewport.height * 0.6);
      assert(canvas.y >= close.y + close.height, 'standalone Snap reserves the close-control row');
    }
  } finally { await browser.close(); }
});
