import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import {runInNewContext} from 'node:vm';

const source = readFileSync(new URL('../../homepage/js/CodeMirrorLoader.js', import.meta.url), 'utf8');
const imports = /^import\s+\{([\s\S]*?)\}\s+from\s+"[^"]+";/gm;
const dependencies = Object.fromEntries([...source.matchAll(imports)]
  .flatMap(match => match[1].split(',').map(name => name.trim()))
  .map(name => [name, Object.freeze({name})]));
const bridge = source.replace(imports, '');

function load(facade) {
  const globals = {...dependencies, EduSquirrelCodeMirrorScala: facade};
  runInNewContext(bridge, globals);
  return globals;
}

test('the loader publishes the matching Scala facade when it is already loaded', async () => {
  const facade = {createEditor() {}};
  const globals = load(facade);
  assert.equal(globals.EduSquirrelCodeMirror, facade);
  assert.equal(await globals.EduSquirrelCodeMirrorReady, facade);
});

test('the loader does not advertise a missing implementation to an older client', async () => {
  for (const missing of [undefined, null]) {
    const globals = load(missing);
    assert.equal(globals.EduSquirrelCodeMirror, missing);
    assert.equal(await globals.EduSquirrelCodeMirrorReady, missing);
  }
});

test('the library namespace preserves the imported identities and optional Java loader', () => {
  const libraries = load(undefined).EduSquirrelCodeMirrorLibraries;
  assert(Object.isFrozen(libraries));
  for (const [name, dependency] of Object.entries(dependencies)) {
    assert.equal(libraries[name], dependency, name);
  }
  assert.equal(libraries.MySQL, dependencies.MySQL);
  assert.equal(typeof libraries.loadJavaModule, 'function');
});
