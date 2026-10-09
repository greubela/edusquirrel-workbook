import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';

const root = path.resolve(import.meta.dirname, '../..');
test('evacuation tasks are translated in both languages and preserve source quotation costs', async () => {
  const source = await readFile(path.join(root, 'modules/client/src/main/scala/it/evadid/homepage/workbook/content/CreateEvacuationWorkbook.scala'), 'utf8');
  const keys = [...source.matchAll(/"digitalWorkbooks\/(evacuation\w+)"/g)].map(match => match[1]);
  const costs = {Training:8000, MoveAssembly:1500, AddAssembly:2000, WidenDoor:14000,
    RemoveObstacles:4000, HalfPeople:18000, AddObstacles:2000, Custom:18000};
  for (const language of ['en', 'de']) {
    const map = JSON.parse(await readFile(path.join(root, `resources/languageMaps/eva/digitalWorkbooks/map-${language}.json`), 'utf8'));
    for (const key of keys) assert(map[key]?.trim(), `${language}: missing ${key}`);
    for (const [measure,cost] of Object.entries(costs)) {
      const label = map['evacuationBudget' + measure];
      assert(label, `${language}: missing measure ${measure}`);
      assert.equal(Number(label.replace(/[^0-9]/g, '')), cost, `${language}: quotation cost for ${measure}`);
    }
    assert(map.evacuationLayoutProvenance.includes('PDF'));
    assert(map.evacuationDoorResults.includes(language === 'en' ? '1.2 m' : '1,2 m'));
  }
});
