import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {test} from 'node:test';
import {JSDOM} from 'jsdom';

const root = new URL('../../', import.meta.url);
const read = path => readFile(new URL(path, root), 'utf8');

test('turtle CSS overrides JSXGraph attributes through redraws and highlights', async () => {
  const colors = await read('homepage/css/generic/colors.css');
  // jsdom's CSS parser does not support the nested browser-default rules.
  const tokens = colors.slice(0, colors.indexOf('/* UPDATE BROWSER DEFAULTS */')) + '}';
  const css = [tokens, await read('homepage/css/generic/dimensions.css'), await read('homepage/css/client-components.css')]
    .join('\n').replace(/^@import[^;]+;/gm, '');
  const dom = new JSDOM('<div id="board" class="turtle-gradig-panel" style="width:300px;height:300px"></div>',
    {runScripts: 'outside-only', pretendToBeVisual: true});
  try {
    dom.window.matchMedia = () => ({matches: false, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {}});
    const style = dom.window.document.createElement('style');
    style.textContent = css;
    dom.window.document.head.append(style);
    dom.window.eval(await read('resources/programs/20261004JSXGraph/jsxgraphcore.js'));
    const board = dom.window.JXG.JSXGraph.initBoard('board', {
      renderer: 'svg', boundingbox: [-10,10,10,-10], axis: false, showCopyright: false, showNavigation: false,
      resize: {enabled: false}
    });
    const computed = obj => dom.window.getComputedStyle(obj.rendNode);
    for (const result of ['correct', 'unexpected', 'missing']) {
      const classes = `turtle-line turtle-line--${result} turtle-line--jump`;
      const line = board.create('segment', [[0,0],[1,1]], {cssClass: classes, highlightCssClass: classes});
      assert.notEqual(computed(line).strokeDasharray, 'none', 'jump lines are dashed');
      // A theme can override colors and widths without changing renderer code.
      style.textContent += `.turtle-gradig-panel .turtle-line--${result} {stroke: rgb(1, 2, 3); stroke-width: 7px;}`;
      for (const action of [() => board.update(), () => board.renderer.highlight(line), () => board.renderer.noHighlight(line)]) {
        action();
        assert.equal(computed(line).stroke, 'rgb(1, 2, 3)');
        assert.equal(computed(line).strokeWidth, '7px');
        assert.equal(line.rendNode.getAttribute('class'), classes);
      }
    }
    const point = board.create('point', [0,0], {name: '', cssClass: 'turtle-point', highlightCssClass: 'turtle-point is-emphasized'});
    assert.equal(computed(point).fillOpacity, '0.1');
    board.renderer.highlight(point);
    assert.equal(computed(point).fillOpacity, '1');
    board.renderer.noHighlight(point);
    assert.equal(computed(point).fillOpacity, '0.1');
    point.setAttribute({cssClass: 'turtle-point is-emphasized'});
    board.update();
    assert.equal(computed(point).fillOpacity, '1', 'related-line hover survives redraw');
    point.setAttribute({cssClass: 'turtle-point'});
    board.update();
    assert.equal(computed(point).fillOpacity, '0.1', 'leaving restores the default');
    const angle = board.create('angle', [[1,0],[0,0],[0,1]], {
      name: '', type: 'sector', cssClass: 'turtle-angle', highlightCssClass: 'turtle-angle is-emphasized'
    });
    assert.equal(computed(angle).strokeOpacity, '0.1');
    board.renderer.highlight(angle);
    assert.equal(computed(angle).strokeOpacity, '1');
    board.renderer.noHighlight(angle);
    assert.equal(computed(angle).strokeOpacity, '0.1');
  } finally {dom.window.close();}
});
