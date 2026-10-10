import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';

const root = path.resolve(import.meta.dirname, '../..');
const folder = path.join(root, 'resources/languageMaps/eva/embroiderypreviewworkbook');

test('preview edition has complete bilingual content and retains the original workbook', async () => {
  const source = await fs.readFile(path.join(root, 'modules/client/src/main/scala/it/evadid/homepage/workbook/content/CreateEmbroideryPreviewWorkbook.scala'), 'utf8');
  const universal = JSON.parse(await fs.readFile(path.join(folder, 'map-universal.json')));
  const maps = await Promise.all(['de','en'].map(async language => JSON.parse(await fs.readFile(path.join(folder, `map-${language}.json`)))));
  for (const map of maps) {
    for (const [,key] of source.matchAll(/"embroiderypreviewworkbook\/([^"\n]+)"/g)) assert(map[key] || universal[key] || ["modulo","level"].includes(key), key);
    for (const [,key] of source.matchAll(/prompt\("([^"\n]+)"\)/g)) assert(map[key], key);
    for (let i=0;i<4;i++) assert(map['level'+i]);
    for (let i=1;i<8;i++) assert(map['modulo'+i]);
    assert(!/unfinished|not quite finished|noch nicht ganz fertig|\bUpps\b|\bOops\b/i.test(JSON.stringify(map)));
    for (const key of ['RecreateShape','RecreateShapeWithBlocks','RecreateShapeWithCounting']) assert(!/upload|hochladen|lade es/i.test(map[key]));
  }
  assert.deepEqual(Object.keys(maps[0]).filter(k=>!k.endsWith('Scaff')).sort(), Object.keys(maps[1]).filter(k=>!k.endsWith('Scaff')).sort());
  for (const [key,value] of Object.entries(universal)) if(key.startsWith('file')) await fs.access(path.join(root,'resources/workbookresources',value));
  const original = await fs.readFile(path.join(root,'modules/client/src/main/scala/it/evadid/homepage/workbook/content/CreateEmbroideryWorkbook.scala'),'utf8');
  assert(original.includes('TurtleStitchRecreateShapeInteractionLegacy'));
  assert(!source.includes('TurtleStitchRecreateShapeInteractionLegacy'));
  const html = await fs.readFile(path.join(root,'homepage/embroideryPreviewWorkbook/index.html'),'utf8');
  assert(html.includes('id="workbookEmbroideryPreview"'));
  assert(html.includes('src="../js/CodeMirrorLoader.js"'));
});
