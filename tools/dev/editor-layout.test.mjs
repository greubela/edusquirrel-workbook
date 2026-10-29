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
const viewports = [{width: 1440, height: 900}, {width: 1440, height: 480}, {width: 760, height: 900},
  {width: 390, height: 844}, {width: 320, height: 740}, {width: 844, height: 390}];
const compactJavaNavigation = viewport => viewport.width <= 760 || viewport.height <= 600;
const javaIcon = '<svg class="eva-editor__language-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M5 11h11v5a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4Zm11 1h2a3 3 0 0 1 0 6h-3M4 23h14M8 8c-3-3 3-3 0-6M12 8c-3-3 3-3 0-6"/></svg>';
const board = '<div class="turtle-gradig-panel"><svg viewBox="0 0 200 200"><path class="turtle-line turtle-line--correct" d="M20 180H180V20" fill="none" stroke="currentColor"/></svg></div>';
const drawingPanel = `<div class="turtle-execution"><h3>Drawing</h3><div class="turtle-execution__actions"><button type="button">Run</button><button type="button" disabled>Stop</button></div><p class="turtle-execution__status" role="status">Run your program to see its drawing.</p><figure class="turtle-execution__figure"><figcaption>Target</figcaption><div class="turtle-execution__canvas">${board}</div></figure></div>`;
const codeMirrorLayout = `<style>
  .cm-editor { display: flex !important; flex-direction: column; }
  .cm-scroller { display: flex !important; align-items: flex-start !important; height: 100%; overflow-x: auto; position: relative; }
  .cm-content { flex-grow: 2; flex-shrink: 0; display: block; white-space: pre; word-wrap: normal; box-sizing: border-box; min-height: 100%; }
  .cm-gutters { flex-shrink: 0; display: flex; height: 100%; box-sizing: border-box; }
</style>`;
const codeEditor = language => `<div class="code-editor code-mirror-editor">${language === 'Fallback'
  ? '<textarea class="code-mirror-editor__fallback">forward(100)</textarea>'
  : `<textarea class="code-mirror-editor__fallback" hidden></textarea><div class="cm-editor edusquirrel-code-mirror"><div class="cm-scroller"><div class="cm-gutters"><div class="cm-lineNumbers"><div class="cm-gutterElement">1</div></div></div><div class="cm-content" contenteditable="true" role="textbox"><div class="cm-line"><span class="cm-plain-name">${language === 'Java' ? 'Turtle.forward(100);' : 'forward(100)'}</span></div><div class="cm-line"><span class="cm-comment">// retained draft</span></div></div></div></div>`}</div>`;

const javaEditor = ({theme, standalone, fullSource, collapsed}) => `
  <div class="java-function-based-editor editor-appearance${standalone ? ' java-function-based-editor--with-reference' : ''}${collapsed ? ' java-function-based-editor--structure-collapsed' : ''}" data-editor-theme="${theme}">
    <div class="java-function-editor__topbar"><div><h2 class="java-function-editor__title">Java editor</h2><p class="java-function-editor__subtitle">Explore classes and edit one method at a time.</p></div>
      <div class="java-function-editor__topbar-actions"><button class="java-function-editor__button java-function-editor__button--secondary" aria-controls="java-navigation" aria-expanded="${!collapsed}">${collapsed ? 'Show' : 'Hide'} classes</button><span class="java-function-editor__sync" role="status">${fullSource ? 'Full source' : 'Method view'}</span></div>
    </div>
    <nav class="java-function-editor__diagram-section" id="java-navigation" aria-label="Java classes and methods">
      <div class="java-function-editor__section-heading"><div><h3 class="java-function-editor__section-title">Classes</h3><p class="java-function-editor__section-hint">Select a method to open it in the editor.</p></div><button class="java-function-editor__button java-function-editor__button--primary">Add class</button></div>
      <div class="java-function-editor__class-row"><div class="java-function-editor__class-card"><div class="java-function-editor__class-header"><strong class="java-function-editor__class-name">Drawing</strong><button class="java-function-editor__button java-function-editor__button--small">Add function</button></div>
        <div class="java-function-editor__function-list"><button class="java-function-editor__function java-function-editor__function--selected" aria-pressed="true">void main(String[] args)</button><button class="java-function-editor__function" aria-pressed="false">void draw(int length)</button></div>
      </div></div>
    </nav>
    <div class="java-function-editor__workspace"><div class="java-function-editor__workspace-heading"><div><h3 class="java-function-editor__section-title">${fullSource ? 'Java source' : 'Drawing.main'}</h3><p class="java-function-editor__section-hint">Your edits are kept, including unfinished code.</p></div>
      <div class="java-function-editor__view-toggle"><button class="java-function-editor__button ${fullSource ? 'java-function-editor__button--secondary' : 'java-function-editor__button--primary'}" aria-pressed="${!fullSource}">Method</button><button class="java-function-editor__button ${fullSource ? 'java-function-editor__button--primary' : 'java-function-editor__button--secondary'}" aria-pressed="${fullSource}">Full source</button></div>
      <label class="java-function-editor__compact-navigation"><span>Method</span><select aria-label="Java method"><option>Drawing.main(String[] args)</option><option>Drawing.draw(int length)</option></select></label></div>
      <div class="java-function-editor__editor-panel"><div class="java-function-editor__${fullSource ? 'source' : 'method'}">${codeEditor('Java')}</div><span class="java-function-editor__draft-warning java-function-editor__draft-warning--hidden">Your draft is kept.</span></div>
    </div>
    ${standalone ? `<aside class="java-function-editor__reference" aria-label="Task reference">${drawingPanel}</aside>` : ''}
    <dialog class="java-function-editor__dialog" aria-label="Add function"><h2 class="java-function-editor__dialog-title">Add function</h2><div class="java-function-editor__dialog-fields">
      ${['Function name', 'Return type', 'Parameters'].map(label => `<label class="java-function-editor__field"><span>${label}</span><input type="text" value="${label === 'Return type' ? 'void' : ''}"></label>`).join('')}
      <label class="java-function-editor__method-option"><input type="checkbox" checked><span>Static method</span></label></div><p class="java-function-editor__dialog-error" role="alert"></p><div class="java-function-editor__dialog-actions"><button class="java-function-editor__button java-function-editor__button--secondary">Cancel</button><button class="java-function-editor__button java-function-editor__button--primary">Add function</button></div></dialog>
  </div>`;

const fixture = (language, preview = true, standalone = false, theme = 'dark', fullSource = false, collapsed = false) => {
  const editor = language === 'Snap'
    ? '<div class="code-editor-container be-program-snap-fullscreen"><div class="code-editor be-program-snap-renderer be-program-snap-renderer--editor"><canvas class="be-program-snap-renderer__canvas"></canvas></div></div>'
    : language === 'Java' ? javaEditor({theme, standalone, fullSource, collapsed}) : codeEditor(language);
  return `<!doctype html><html><head>${codeMirrorLayout}<link rel="stylesheet" href="/css/custom-elements.css"><link rel="stylesheet" href="/css/workbook/workbook-structure.css"><link rel="stylesheet" href="/css/workbook/workbook-header.css"><link rel="stylesheet" href="/css/workbook/workbook-interactions.css"><link rel="stylesheet" href="/css/JavaFunctionEditor.css"></head><body>
    <dialog class="fullscreen-overlay-dialog"><div class="fullscreen-content-container">${standalone ? editor : `
      <div class="eva-editor editor-appearance${language === 'Java' ? ' eva-editor--java' : ''}" data-editor-theme="${theme}"><div class="eva-editor__toolbar"><div class="eva-editor__tabs">
        ${['Snap', 'Python', 'Java'].map(tab => `<button type="button" role="tab" aria-label="${tab}" title="${tab}" aria-selected="${tab === (language === 'Fallback' ? 'Python' : language)}">${tab === 'Java' ? javaIcon : `<span>${tab}</span>`}</button>`).join('')}
      </div></div><div class="eva-editor__content">${editor}</div>${preview ? `<div class="turtle-sidebar">${language === 'Java' ? drawingPanel : `<div class="turtle-live-preview"><div class="element-card"><h3 class="element-card-label">Preview (Turtle)</h3><div class="element-card-content"><div>${board}</div></div></div></div>`}</div>` : ''}</div>`}
    </div><button class="fullscreen-close-button" aria-label="Exit full screen">×</button></dialog>
    <script>document.querySelector('.fullscreen-overlay-dialog').showModal()</script></body></html>`;
};

const launch = () => chromium.launch({executablePath, headless: true, chromiumSandbox: true});
const loadFixture = async page => {
  let html;
  await page.route('http://editor.test/**', async route => {
    const pathname = new URL(route.request().url()).pathname;
    if (pathname === '/') return route.fulfill({contentType: 'text/html; charset=utf-8', body: html});
    return route.fulfill({contentType: 'text/css', body: await readFile(homepage + pathname, 'utf8')});
  });
  return async (language, preview = true, standalone = false, theme = 'dark', fullSource = false, collapsed) => {
    const initialCollapse = collapsed ?? compactJavaNavigation(page.viewportSize());
    html = fixture(language, preview, standalone, theme, fullSource, initialCollapse);
    await page.goto('http://editor.test/');
  };
};

const assertHorizontalFit = async page => {
  const overflow = await page.evaluate(() => {
    const shell = document.querySelector('.fullscreen-content-container').getBoundingClientRect();
    return Array.from(document.querySelectorAll('.editor-appearance, .eva-editor__toolbar, .eva-editor__tabs, .eva-editor__content, .turtle-sidebar, .java-function-editor__topbar, .java-function-editor__diagram-section, .java-function-editor__workspace, .java-function-editor__reference, .cm-editor, textarea:not([hidden]), canvas'))
      .filter(element => element.getClientRects().length).filter(element => {
        const box = element.getBoundingClientRect();
        return box.left < shell.left - 1 || box.right > shell.right + 1;
      }).map(element => element.className);
  });
  assert.deepEqual(overflow, [], 'editor panels never overflow the dialog horizontally');
};

const assertEditorFrame = async page => {
  const frame = await page.evaluate(() => {
    const shell = document.querySelector('.fullscreen-content-container');
    const appearance = shell.querySelector(':scope > .editor-appearance');
    const shellStyle = getComputedStyle(shell);
    const radius = element => ['borderTopLeftRadius', 'borderTopRightRadius', 'borderBottomRightRadius', 'borderBottomLeftRadius']
      .map(property => getComputedStyle(element)[property]);
    const context = document.createElement('canvas').getContext('2d');
    context.fillStyle = shellStyle.backgroundColor;
    context.fillRect(0, 0, 1, 1);
    const owners = [shell, appearance, ...document.querySelectorAll('.eva-editor__content, .java-function-based-editor, .java-function-editor__workspace, .java-function-editor__editor-panel')];
    return {shellRadius: radius(shell), editorRadius: radius(appearance), backgroundAlpha: context.getImageData(0, 0, 1, 1).data[3],
      owners: [...new Set(owners)].map(element => {
        element.scrollTop = 100;
        element.scrollLeft = 100;
        return {name: element.className, clientHeight: element.clientHeight, scrollHeight: element.scrollHeight,
          clientWidth: element.clientWidth, scrollWidth: element.scrollWidth, scrollTop: element.scrollTop, scrollLeft: element.scrollLeft};
      })};
  });
  assert.deepEqual(frame.shellRadius, frame.editorRadius, 'fullscreen and editor share one outer contour');
  assert.equal(frame.backgroundAlpha, 0, 'the fullscreen shell adds no second background behind editor corners');
  for (const owner of frame.owners) {
    assert(owner.scrollHeight <= owner.clientHeight + 1 && owner.scrollWidth <= owner.clientWidth + 1,
      `outer editor containers do not grow beyond their fixed slot: ${JSON.stringify(owner)}`);
    assert.equal(owner.scrollTop, 0, `${owner.name} cannot scroll vertically`);
    assert.equal(owner.scrollLeft, 0, `${owner.name} cannot scroll horizontally`);
  }
};

const assertPaneBounds = async (page, viewport, minimumCodeHeight = viewport.height > 600 ? 80 : 40) => {
  const panes = await page.evaluate(() => {
    const shell = document.querySelector('.fullscreen-content-container').getBoundingClientRect().toJSON();
    const selectors = '.eva-editor__toolbar, .eva-editor__content, .turtle-sidebar, .java-function-editor__topbar, .java-function-editor__diagram-section, .java-function-editor__workspace, .java-function-editor__compact-navigation, .java-function-editor__compact-navigation select, .java-function-editor__reference, .cm-editor, .cm-scroller, textarea:not([hidden]), canvas, .turtle-gradig-panel';
    const box = element => element?.getBoundingClientRect().toJSON() ?? null;
    return {shell, panes: Array.from(document.querySelectorAll(selectors)).filter(element => element.getClientRects().length)
      .map(element => {
        const execution = element.matches('.turtle-gradig-panel') ? element.closest('.turtle-execution') : null;
        const actions = execution?.querySelector('.turtle-execution__actions');
        return {name: element.className, box: box(element), fontFamily: getComputedStyle(element).fontFamily,
          drawingChrome: execution ? {rows: getComputedStyle(execution).gridTemplateRows,
            status: box(execution.querySelector('.turtle-execution__status')), actions: box(actions),
            buttons: Array.from(actions?.children ?? []).map(button => ({text: button.textContent, box: box(button),
              fontFamily: getComputedStyle(button).fontFamily}))} : null,
          chain: element.matches('.cm-scroller, textarea') ? [element, element.parentElement, element.parentElement.parentElement,
            element.parentElement.parentElement.parentElement, element.closest('.java-function-editor__workspace'),
            element.closest('.java-function-based-editor')].filter(Boolean).map(owner => ({name: owner.className,
              height: owner.clientHeight, cssHeight: getComputedStyle(owner).height, flex: getComputedStyle(owner).flex,
              rows: getComputedStyle(owner).gridTemplateRows})) : []};
      })};
  });
  for (const {name, box, fontFamily, drawingChrome, chain} of panes.panes) {
    assert(box.left >= panes.shell.left - 1 && box.right <= panes.shell.right + 1 &&
      box.top >= panes.shell.top - 1 && box.bottom <= panes.shell.bottom + 1,
    `${name} remains visible within the fixed editor frame at ${viewport.width}x${viewport.height}`);
    if (name.includes('cm-scroller') || name.includes('fallback')) assert(box.height >= minimumCodeHeight,
      `code retains a usable internal scrolling area: ${viewport.width}x${viewport.height}, ${box.height}px < ${minimumCodeHeight}px ${JSON.stringify(chain)}`);
    if (name.includes('turtle-gradig-panel')) assert(box.width >= 64 && box.height >= 64,
      `the bounded drawing pane remains visible at ${viewport.width}x${viewport.height}: ${JSON.stringify({name, fontFamily, box, drawingChrome})}`);
  }
};

const assertFocus = async locator => {
  const before = await locator.boundingBox();
  await locator.focus();
  const focus = await locator.evaluate(element => {
    const style = getComputedStyle(element);
    return {active: element === document.activeElement, outline: style.outlineStyle, width: parseFloat(style.outlineWidth)};
  });
  assert(focus.active && focus.outline === 'solid' && focus.width >= 2, 'keyboard focus has a visible outline');
  const after = await locator.boundingBox();
  assert.equal(after.width, before.width, 'focus does not resize controls');
  assert.equal(after.height, before.height, 'focus does not resize controls');
};

const assertTouchTargets = async page => {
  const undersized = await page.evaluate(() => Array.from(document.querySelectorAll('.editor-appearance button, .java-function-editor__field input, .java-function-editor__compact-navigation select'))
    .filter(element => element.getClientRects().length).filter(element => {
      const box = element.getBoundingClientRect();
      return box.height < 44 || box.width < 44;
    })
    .map(element => element.textContent || element.className));
  assert.deepEqual(undersized, [], 'touch controls are at least 44 by 44 CSS pixels');
};

test('editor tabs stay compact and panels fit on desktop, mobile and short screens', async () => {
  const browser = await launch();
  try {
    for (const viewport of viewports) {
      const context = await browser.newContext({viewport, hasTouch: viewport.width <= 760});
      const page = await context.newPage();
      const load = await loadFixture(page);
      for (const language of ['Snap', 'Python', 'Fallback']) {
        for (const preview of [true, false]) {
          for (const theme of ['dark', 'light']) {
            await load(language, preview, false, theme);
            const geometry = await page.evaluate(() => {
              const box = selector => document.querySelector(selector)?.getBoundingClientRect().toJSON();
              return {tabs: box('.eva-editor__tabs'), content: box('.eva-editor__content'),
                sidebar: box('.turtle-sidebar'), board: box('.turtle-gradig-panel'), shell: box('.fullscreen-content-container'),
                editor: box('.cm-editor, textarea:not([hidden]), canvas'), close: box('.fullscreen-close-button')};
            });
            assert(geometry.tabs.height < 60, 'tabs must not consume the editor height');
            assert(geometry.content.height > 80, 'editing area remains usable');
            await assertHorizontalFit(page);
            await assertEditorFrame(page);
            await assertPaneBounds(page, viewport);
            const javaTab = page.locator('.eva-editor__tabs').getByRole('tab', {name: 'Java', exact: true});
            assert.equal(await javaTab.innerText(), '', 'Java is displayed as an icon with an accessible label');
            assert.equal(await javaTab.locator('.eva-editor__language-icon').count(), 1);
            for (const panel of [geometry.content, geometry.sidebar, geometry.editor].filter(Boolean)) {
              assert(panel.left >= geometry.shell.left && panel.right <= geometry.shell.right + 1, 'no horizontal overflow');
              assert(panel.bottom <= geometry.shell.bottom + 1, 'panels fit the dialog height');
            }
            if (preview) assert(geometry.board.width <= geometry.sidebar.width, 'preview graph fits its panel');
            const buttons = await page.locator('.eva-editor__toolbar button').evaluateAll(elements => elements.map(element => element.getBoundingClientRect().toJSON()));
            assert(buttons.every(button => button.right <= geometry.close.left || button.bottom <= geometry.close.top || button.top >= geometry.close.bottom), 'toolbar controls reserve space for the close control');
            const sideBySide = viewport.width > 860 || (viewport.width > 760 && viewport.height <= 600);
            if (preview && sideBySide) assert(geometry.sidebar.left >= geometry.content.right, 'desktop and short-landscape previews sit beside the editor');
            if (preview && !sideBySide) assert(geometry.sidebar.top >= geometry.content.bottom, 'portrait preview sits below the editor');
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
            await assertFocus(page.locator('.eva-editor__tabs > button').first());
            await assertFocus(page.locator('.eva-editor__tabs > button[aria-selected="true"]'));
            if (viewport.width <= 760) await assertTouchTargets(page);
          }
        }
      }
      await load('Snap', false, true);
      const canvas = await page.locator('canvas').boundingBox();
      const close = await page.locator('.fullscreen-close-button').boundingBox();
      assert(canvas.width > viewport.width * 0.8 && canvas.height > viewport.height * 0.6);
      assert(canvas.y >= close.y + close.height, 'standalone Snap reserves the close-control row');
      await context.close();
    }
  } finally { await browser.close(); }
});

test('Java method, full-source and dialog chrome fit Eva and standalone editors', async () => {
  const browser = await launch();
  try {
    for (const viewport of viewports) {
      const context = await browser.newContext({viewport, hasTouch: viewport.width <= 760});
      const page = await context.newPage();
      const load = await loadFixture(page);
      for (const standalone of [false, true]) {
        for (const theme of ['dark', 'light']) {
          for (const fullSource of [false, true]) {
            const collapsed = fullSource || compactJavaNavigation(viewport);
            await load('Java', !standalone, standalone, theme, fullSource, collapsed);
            await assertHorizontalFit(page);
            await assertEditorFrame(page);
            await assertPaneBounds(page, viewport);
            assert.equal(await page.locator('.java-function-editor__diagram-section').isVisible(), !collapsed, 'initial mobile and short-screen navigation is collapsed; an open desktop list remains visible');
            assert.equal(await page.locator('.java-function-editor__compact-navigation').isVisible(), collapsed, 'compact method selection follows the initial viewport state and explicit collapse');
            assert.equal(await page.locator('.editor-appearance__toggle').count(), 0, 'the editor follows the page theme and has no private toggle');
            assert(await page.locator('.cm-editor').isVisible(), 'method and full-source editing panes remain visible');
            await assertFocus(page.locator('.java-function-editor__view-toggle button').first());
            if (viewport.width <= 760) await assertTouchTargets(page);
            const close = await page.locator('.fullscreen-close-button').boundingBox();
            const buttons = await page.locator('.java-function-editor__topbar-actions button').evaluateAll(elements => elements.map(element => element.getBoundingClientRect().toJSON()));
            assert(buttons.every(button => button.x + button.width <= close.x || button.y >= close.y + close.height || button.y + button.height <= close.y), 'Java controls do not overlap fullscreen close');
            if (!standalone && viewport.width > 760 && viewport.height <= 600) {
              for (const fontFamily of ['"Verdana", sans-serif', '"Courier New", monospace', '"Arial Black", sans-serif']) {
                await page.addStyleTag({content: `
                  .eva-editor--java.editor-appearance[data-editor-theme],
                  .eva-editor--java .java-function-based-editor.editor-appearance[data-editor-theme] {
                    font-family: ${fontFamily};
                  }
                `});
                await assertHorizontalFit(page);
                await assertEditorFrame(page);
                await assertPaneBounds(page, viewport);
              }
            }
          }
          await page.locator('.java-function-editor__dialog').evaluate(dialog => dialog.showModal());
          const dialog = await page.locator('.java-function-editor__dialog').boundingBox();
          assert(dialog.x >= 0 && dialog.x + dialog.width <= viewport.width + 1, 'Java modal fits horizontally');
          assert(dialog.y >= 0 && dialog.y + dialog.height <= viewport.height + 1, 'Java modal fits short screens');
          await assertFocus(page.locator('.java-function-editor__dialog input').first());
          if (viewport.width <= 760) await assertTouchTargets(page);
          await page.locator('.java-function-editor__dialog').evaluate(dialog => dialog.close());
          if (viewport.width <= 760 && viewport.height >= 844) {
            await load('Java', !standalone, standalone, theme, false, false);
            assert(await page.locator('.java-function-editor__diagram-section').isVisible(), 'explicitly opening classes is not blocked by a mobile CSS hide rule');
            await assertEditorFrame(page);
            await assertPaneBounds(page, viewport, 40);
          }
        }
      }
      await context.close();
    }
  } finally { await browser.close(); }
});

test('long drafts and class lists scroll internally without moving the editor frame', async () => {
  const browser = await launch();
  try {
    for (const viewport of viewports) {
      const context = await browser.newContext({viewport, hasTouch: viewport.width <= 760});
      const page = await context.newPage();
      const load = await loadFixture(page);
      for (const [language, standalone] of [['Java', false], ['Java', true], ['Python', false], ['Fallback', false]]) {
        await load(language, true, standalone);
        await page.locator('.cm-content, textarea:not([hidden])').evaluate(element => {
          const text = Array.from({length: 240}, (_, index) => `forward(${index}) ${'x'.repeat(240)}`);
          if (element.tagName === 'TEXTAREA') element.value = text.join('\n');
          else element.replaceChildren(...text.map(value => {
            const line = document.createElement('div');
            line.className = 'cm-line';
            line.textContent = value;
            return line;
          }));
        });
        const scroll = await page.locator('.cm-scroller, textarea:not([hidden])').evaluate(element => {
          element.scrollTop = 200;
          element.scrollLeft = 200;
          const style = getComputedStyle(element);
          const wrapper = element.closest('.code-mirror-editor');
          return {top: element.scrollTop, left: element.scrollLeft, clientHeight: element.clientHeight,
            scrollHeight: element.scrollHeight, clientWidth: element.clientWidth, scrollWidth: element.scrollWidth,
            overflowX: style.overflowX, overflowY: style.overflowY, height: style.height,
            wrapperHeight: getComputedStyle(wrapper).height, wrapperBoxSizing: getComputedStyle(wrapper).boxSizing};
        });
        const label = `${language} ${standalone ? 'standalone' : 'Eva'} ${viewport.width}x${viewport.height}: ${JSON.stringify(scroll)}`;
        assert(scroll.top > 0, `long code scrolls vertically in its own pane: ${label}`);
        if (language !== 'Fallback') assert(scroll.left > 0, `unwrapped CodeMirror lines scroll horizontally inside the code pane: ${label}`);
        if (language === 'Java' && !compactJavaNavigation(viewport)) {
          const navigation = await page.locator('.java-function-editor__class-row').evaluate(element => {
            const card = element.firstElementChild;
            element.append(...Array.from({length: 20}, () => card.cloneNode(true)));
            element.scrollTop = 200;
            return element.scrollTop;
          });
          assert(navigation > 0, 'the desktop class list has its own scroll area');
        }
        await assertEditorFrame(page);
        await assertPaneBounds(page, viewport);
      }
      await context.close();
    }
  } finally { await browser.close(); }
});

test('editor appearance preserves code contrast, focus, reduced motion and drawing geometry', async () => {
  const browser = await launch();
  try {
    const page = await browser.newPage({viewport: {width: 1440, height: 900}, reducedMotion: 'reduce'});
    const load = await loadFixture(page);
    for (const [language, standalone] of [['Java', true], ['Java', false], ['Python', false], ['Fallback', false], ['Snap', false]]) {
      await load(language, true, standalone);
      const drawing = () => page.locator('.turtle-gradig-panel').evaluateAll(elements => elements.map(element => ({
        background: getComputedStyle(element).backgroundColor,
        svg: element.querySelector('svg').outerHTML
      })));
      const originalDrawing = await drawing();
      const originalCode = await page.locator('.cm-content, textarea:not([hidden])').allTextContents();
      const palettes = [];
      for (const theme of ['dark', 'light', 'dark']) {
        await page.locator('.editor-appearance').evaluateAll((elements, next) => elements.forEach(element => element.setAttribute('data-editor-theme', next)), theme);
        palettes.push(await page.locator('.editor-appearance').first().evaluate(element => {
          const style = getComputedStyle(element);
          return [style.backgroundColor, style.color, style.colorScheme];
        }));
        assert.deepEqual(await drawing(), originalDrawing, 'appearance changes leave board background and SVG geometry unchanged');
        assert.deepEqual(await page.locator('.cm-content, textarea:not([hidden])').allTextContents(), originalCode, 'appearance changes do not replace the draft DOM');
        const contrast = await page.locator('.cm-plain-name, .cm-comment, textarea:not([hidden])').evaluateAll(elements => {
          const context = document.createElement('canvas').getContext('2d');
          const rgba = color => {
            context.clearRect(0, 0, 1, 1);
            context.fillStyle = color;
            context.fillRect(0, 0, 1, 1);
            const channels = Array.from(context.getImageData(0, 0, 1, 1).data);
            return [...channels.slice(0, 3), channels[3] / 255];
          };
          const luminance = rgb => rgb.slice(0, 3).map(value => {
            const channel = value / 255;
            return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
          }).reduce((sum, channel, index) => sum + channel * [0.2126, 0.7152, 0.0722][index], 0);
          return elements.map(element => {
            const layers = [];
            for (let current = element; current; current = current.parentElement) layers.unshift(rgba(getComputedStyle(current).backgroundColor));
            let background = [255, 255, 255];
            for (const layer of layers) {
              const alpha = layer[3] ?? 1;
              background = background.map((value, index) => layer[index] * alpha + value * (1 - alpha));
            }
            const color = rgba(getComputedStyle(element).color);
            const foreground = background.map((value, index) => color[index] * color[3] + value * (1 - color[3]));
            const light = luminance(foreground), dark = luminance(background);
            const surface = rgba(getComputedStyle(element).getPropertyValue('--editor-code-surface'));
            return {text: element.textContent, ratio: (Math.max(light, dark) + 0.05) / (Math.min(light, dark) + 0.05),
              themedSurface: background.every((value, index) => Math.abs(value - surface[index]) <= 1)};
          });
        });
        assert(contrast.every(item => item.themedSurface), `${language} ${theme}: code uses its scoped surface token`);
        assert(contrast.every(item => item.ratio >= 4.5), `${language} ${theme}: code text contrast ${JSON.stringify(contrast)}`);
        await assertFocus(page.locator('.eva-editor__tabs > button, .java-function-editor__view-toggle button').first());
        const motion = await page.locator('.editor-appearance, .editor-appearance button').evaluateAll(elements => elements.map(element => {
          const style = getComputedStyle(element);
          return [style.transitionDuration, style.animationDuration].flatMap(value => value.split(',').map(parseFloat));
        }));
        assert(motion.flat().every(duration => duration <= 0.00001), 'reduced motion disables editor chrome animations');
      }
      assert.notDeepEqual(palettes[0], palettes[1], 'light and dark resolve to different editor palettes');
      assert.deepEqual(palettes[0], palettes[2], 'returning to dark restores the original palette');
    }
  } finally { await browser.close(); }
});
