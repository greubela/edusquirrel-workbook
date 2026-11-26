import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg'};
const readMap = file => readFile(path.join(root, 'resources/languageMaps/eva', file), 'utf8').then(JSON.parse);

/* Sample solutions of the Java editor tasks, written as complete classes (see CreateMonksWorkbook.EditorCode). */
const helpers = `
  static void stein(double breite) {
    Turtle.forward(breite); Turtle.turnLeft(90); Turtle.forward(20); Turtle.turnLeft(90);
    Turtle.forward(breite); Turtle.turnLeft(90); Turtle.forward(20); Turtle.turnLeft(90);
  }
  static void hoch() { Turtle.penUp(); Turtle.turnLeft(90); Turtle.forward(20); Turtle.turnRight(90); Turtle.penDown(); }
  static void runter() { Turtle.penUp(); Turtle.turnRight(90); Turtle.forward(20); Turtle.turnLeft(90); Turtle.penDown(); }
  static void springe(double weite) { Turtle.penUp(); Turtle.forward(weite); Turtle.penDown(); }
`;
const koch = `
  static void koch(double laenge, int tiefe) {
    if (tiefe == 0) { Turtle.forward(laenge); return; }
    koch(laenge / 3, tiefe - 1); Turtle.turnLeft(60);
    koch(laenge / 3, tiefe - 1); Turtle.turnRight(120);
    koch(laenge / 3, tiefe - 1); Turtle.turnLeft(60);
    koch(laenge / 3, tiefe - 1);
  }
`;
const program = (body, main) => `public class Zeichnung {\n${body}\n  public static void main(String[] args) {\n    ${main}\n  }\n}\n`;
const stairs = back => `
  static void zeichneTreppe(int n) {
    if (n == 0) { return; }
    stein(n * 20); hoch(); zeichneTreppe(n - 1);${back ? ' runter();' : ''}
  }
`;
const editorSolutions = {
  e1: program('', 'Turtle.forward(60); Turtle.turnLeft(90); Turtle.forward(20); Turtle.turnLeft(90); ' +
    'Turtle.forward(60); Turtle.turnLeft(90); Turtle.forward(20);'),
  e2: program(helpers, 'stein(80); hoch(); stein(60); hoch(); stein(40);'),
  e3: program(`
  static void vieleck(int ecken, double laenge) {
    for (int i = 0; i < ecken; i++) { Turtle.forward(laenge); Turtle.turnLeft(360.0 / ecken); }
  }
  static void springe(double weite) { Turtle.penUp(); Turtle.forward(weite); Turtle.penDown(); }
`, 'vieleck(4, 60); springe(100); vieleck(6, 40);'),
  e4: program(helpers + `
  static void zeichneTurm(int n) {
    if (n == 0) { return; }
    stein(40); hoch(); zeichneTurm(n - 1);
  }
`, 'zeichneTurm(5);'),
  e5: program(helpers + `
  static void zeichnePyramide(int n) {
    if (n == 0) { return; }
    stein(n * 20); hoch(); springe(10); zeichnePyramide(n - 1); springe(-10); runter();
  }
`, 'zeichnePyramide(4); springe(100); zeichnePyramide(3);'),
  e6a: program(helpers + stairs(false), 'zeichneTreppe(4);'),
  e6b: program(helpers + stairs(true), 'zeichneTreppe(4); springe(120); zeichneTreppe(3);'),
  k2: program(koch, 'koch(270, 2);'),
  k4: program(koch, 'for (int i = 0; i < 3; i++) { koch(270, 2); Turtle.turnRight(120); }')
};

test('the monk workbook can be solved section by section with every interaction turning green', async () => {
  const browser = await chromium.launch({chromiumSandbox:true,
    executablePath:process.env.CHROMIUM_PATH || chromium.executablePath()});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}});
    page.setDefaultTimeout(60000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      const file = path.resolve(root, '.' + decodeURIComponent(url.pathname) + (url.pathname.endsWith('/') ? 'index.html' : ''));
      if (!file.startsWith(root + path.sep)) return route.abort();
      try { await route.fulfill({body:await readFile(file),contentType:mime[path.extname(file)] || 'application/octet-stream'}); }
      catch { await route.fulfill({status:404,body:'Not found'}); }
    });
    await installCodeMirrorModules(page);
    const [labels, basic, editor] = await Promise.all(['monksworkbook/map-de.json', 'basic/map-de.json', 'blockeditor/map-de.json'].map(readMap));

    const body = page.locator('.workbook-body');
    const interaction = id => body.locator(`.interaction-element[data-element-id="${id}"]`);
    const solved = id => interaction(id).locator('.interaction-element-info.status-correct').waitFor();
    const check = async id => {
      await interaction(id).locator('button.feedback-action--primary').click();
      await solved(id);
    };

    // ---- solvers per interaction type; each enters the correct answer through the UI and requests a check
    const slideshow = slides => async id => {
      const counter = interaction(id).locator('.slide-deck-counter');
      const next = interaction(id).locator('.slide-deck-navigation button').last();
      for (let i = 2; i <= slides; i++) {
        await next.click();
        await counter.getByText(`${i}/${slides}`, {exact:true}).waitFor();
      }
      assert(await next.isDisabled());
      await solved(id);
    };
    const choice = (...indices) => async id => {
      for (const index of indices) await interaction(id).locator('.choice-interaction input').nth(index).check();
      await check(id);
    };
    const cells = (...answers) => async id => {
      for (const [index, answer] of answers.entries()) await interaction(id).locator('input[type=text]').nth(index).fill(answer);
      await check(id);
    };
    // Rows on the left are connected to the categories on the right in the given order.
    const matching = (...categories) => async id => {
      const left = interaction(id).locator('.matching-item--left');
      const right = interaction(id).locator('.matching-item--right');
      for (const [row, category] of categories.entries()) {
        await left.nth(row).click();
        await right.nth(category).click();
      }
      assert.equal(await interaction(id).locator('.matching-line').count(), categories.length);
      await check(id);
    };
    // The items are shuffled; drag each into place, from the top, by dropping it on the upper edge of its target slot.
    const reorder = keys => async id => {
      const items = interaction(id).locator('.reorder-item');
      const expected = keys.map(key => labels[key]);
      for (const [index, text] of expected.entries()) {
        const current = (await items.allInnerTexts()).map(item => item.trim());
        if (current[index] === text) continue;
        await items.filter({hasText: text}).dragTo(items.nth(index), {targetPosition: {x: 10, y: 2}});
        await page.waitForFunction(([selector, index, text]) =>
          document.querySelectorAll(selector)[index]?.innerText.trim() === text,
          [`.interaction-element[data-element-id="${id}"] .reorder-item`, index, text]);
      }
      await check(id);
    };
    const editorTask = task => async id => {
      await interaction(id).getByRole('button', {name: basic.openEditor, exact: true}).click();
      const dialog = page.locator('dialog[open]');
      await dialog.getByRole('tab', {name: 'Java', exact: true}).click();
      await dialog.getByRole('tab', {name: editor.javaModeClass, exact: true}).click();
      // Until CodeMirror has loaded, the plain text fallback edits the same answer.
      const code = dialog.locator('.java-function-editor__class').locator('.cm-content, textarea.code-mirror-editor__fallback:visible').first();
      await code.fill(editorSolutions[task]);
      await dialog.locator('button.feedback-action--primary').first().click();
      await dialog.locator('.feedback-score-chip.feedback-status--correct').first().waitFor();
      await page.locator('.fullscreen-close-button').click();
      await solved(id);
    };

    // ---- the workbook in navigation order; every interaction of a section must be listed here
    const sections = [
      {title: 'welcomeTitle', tasks: {}},
      {title: 'parta', tasks: {
        'monks-theater': slideshow(13),
        'monks-a1': reorder([...Array(8).keys()].map(i => `a1-item${i}`)),
        'monks-a2': cells('3', '5', '5 4 2', '5'),
        'monks-a3': choice(1),
        'monks-a4-match': matching(1, 2, 0, 3, 4),
        'monks-stack': choice(0, 2),
        'monks-ac-1': choice(0), 'monks-ac-2': choice(1), 'monks-ac-3': choice(2), 'monks-ac-4': choice(1),
        'monks-a5-match': matching(0, 1, 2, 3),
        'monks-a6': choice(1, 3, 5),
        'monks-a7': choice(1, 3, 5),
        'monks-a8a': choice(1), 'monks-a8b': cells('6'), 'monks-a8c': choice(2)}},
      {title: 'partc', tasks: {
        'monks-counting-story': slideshow(4),
        'monks-c1-match': matching(1, 4, 2, 0, 3),
        'monks-c2': cells('2', '1', '1', '1', '2', '2', '0', '3', '3'), 'monks-c2b': cells('4'),
        'monks-c3-match': matching(0, 1, 2),
        'monks-c4': cells('6', '3', '5', '3'),
        'monks-c5': choice(1, 3, 4, 5)}},
      {title: 'partzero', tasks: {'monks-e1': editorTask('e1'), 'monks-e2': editorTask('e2'), 'monks-e3': editorTask('e3')}},
      {title: 'partd', tasks: {
        'monks-e4': editorTask('e4'), 'monks-e5': editorTask('e5'), 'monks-e6b': editorTask('e6b'), 'monks-e6a': editorTask('e6a'),
        'monks-d1a': choice(1), 'monks-d1b': choice(1, 3)}},
      {title: 'kochtitle', tasks: {
        'monks-k1': cells('1', '270', '4', '90', '16', '30', '64', '10'), 'monks-k1b': choice(0),
        'monks-k2': editorTask('k2'),
        'monks-k3a': choice(1), 'monks-k3b': choice(0, 1),
        'monks-k4': editorTask('k4'),
        'monks-k5': cells('6', '21', '85', '64')}},
      {title: 'finishTitle', tasks: {}}
    ];

    await page.goto(origin + '/homepage/monksWorkbook/');
    await page.locator('.section-register .login-area').nth(1).locator('button').click();
    await page.locator('.select-language-line > div:has(.flag-fill-german-red)').click();
    assert.equal(await page.locator('.section-block').count(), sections.length);
    await page.locator('.section-block').first().click();
    const next = page.locator('.section-navigation button').last();
    for (const [index, {title, tasks}] of sections.entries()) {
      await body.getByText(labels[title], {exact: true}).first().waitFor();
      const ids = await body.locator('.interaction-element').evaluateAll(nodes => nodes.map(node => node.dataset.elementId));
      // Every gradable interaction of the section has a solution, and every solution has its interaction.
      const gradable = [];
      for (const id of ids) if (await interaction(id).locator('.feedback-score-chip, button.feedback-action--primary').count() > 0) gradable.push(id);
      assert.deepEqual(gradable.filter(id => !(id in tasks)), [], `section ${title}: unsolved gradable interactions`);
      assert.deepEqual(Object.keys(tasks).filter(id => !ids.includes(id)), [], `section ${title}: missing interactions`);
      for (const [id, solve] of Object.entries(tasks)) {
        console.log(`solving ${id}`);
        await solve(id);
      }
      for (const id of Object.keys(tasks)) assert.equal(await interaction(id).locator('.interaction-element-info.status-correct').count(), 1,
        `${id} is still green after solving the rest of its section`);
      if (index < sections.length - 1) await next.click();
    }
    assert(await next.isDisabled(), 'the final section has no next section');

    // The last section closes with the ungraded survey; consent is opt-in.
    const rows = page.locator('.likert-interaction .likert-row');
    assert.equal(await rows.count(), 8);
    for (let row = 0; row < 8; row++) await rows.nth(row).locator('input[type=radio]').nth(3).check();
    const consent = page.locator('.interaction-element').filter({hasText: labels['survey-consent']}).locator('input[type=checkbox]');
    assert.equal(await consent.isChecked(), false);
    await consent.check();
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});
