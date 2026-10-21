import assert from 'node:assert/strict';
import {readFile, readdir} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
const root = path.resolve(import.meta.dirname, '../..');
async function files(directory) {
  const entries = await readdir(path.join(root,directory), {withFileTypes:true});
  return (await Promise.all(entries.map(entry => entry.isDirectory()
    ? files(path.join(directory,entry.name)) : [path.join(directory,entry.name)]))).flat();
}
test('native dependency declarations have one home rather than editor-local facades', async () => {
  for (const directory of ['modules/client/src/main/scala','modules/core/js/src/main/scala','modules/worker/src/main/scala']) {
    for (const file of (await files(directory)).filter(file=>file.endsWith('.scala'))) {
      const source = await readFile(path.join(root,file),'utf8');
      if (/@js\.native|@JSGlobal|@JSImport/.test(source))
        assert(file.startsWith('modules/core/js/src/main/scala/it/evadid/facades/'),file);
    }
  }
});
test('Scala CodeMirror imports use the same pinned native packages as the offline fixtures', async () => {
  const source = await readFile(path.join(root,'modules/core/js/src/main/scala/it/evadid/facades/CodeMirror.scala'),'utf8');
  const packages = JSON.parse(await readFile(path.join(root,'package.json'),'utf8')).devDependencies;
  const imports = [...source.matchAll(/https:\/\/esm\.sh\/(@[^?"\s]+)@([^?"\s]+)/g)];
  assert(imports.length>0);
  for(const [,name,version] of imports) assert.equal(packages[name],version,name);
});

test('core JS facades do not depend on client startup or editor implementations', async () => {
  for (const file of await files('modules/core/js/src/main/scala/it/evadid/facades')) {
    if (file.endsWith('.scala')) assert.doesNotMatch(await readFile(path.join(root,file),'utf8'), /it\.evadid\.homepage|todomove/,file);
  }
});
