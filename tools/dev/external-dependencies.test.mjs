import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
const root = path.resolve(import.meta.dirname, '../..');
test('Scala CodeMirror imports use the same pinned native packages as the offline fixtures', async () => {
  const source = await readFile(path.join(root,'modules/core/js/src/main/scala/it/evadid/facades/CodeMirror.scala'),'utf8');
  const packages = JSON.parse(await readFile(path.join(root,'package.json'),'utf8')).devDependencies;
  const imports = [...source.matchAll(/https:\/\/esm\.sh\/(@[^?"\s]+)@([^?"\s]+)/g)];
  assert(imports.length>0);
  for(const [,name,version] of imports) assert.equal(packages[name],version,name);
});
