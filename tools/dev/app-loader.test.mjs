import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';

const source=await fs.readFile(new URL('../../homepage/js/app-loader.js',import.meta.url),'utf8');
function boot(overrides={}) {
  const scripts=[];
  const window={location:{origin:'https://example.test'},...overrides};
  const document={readyState:'loading',head:{appendChild:script=>scripts.push(script)},createElement:()=>({})};
  vm.runInNewContext(source,{window,document,console,
    fetch:()=>{throw new Error('configuration must not gate bundle loading');},
    Worker:class {constructor(){throw new Error('workers belong to Scala.js startup');}}});
  return scripts;
}

test('the bundle starts during HTML parsing without waiting for configuration or optional libraries',()=>{
  const scripts=boot({EduSquirrelCodeMirrorReady:new Promise(()=>{})});
  assert.equal(scripts.length,1);
  assert.equal(scripts[0].src,'../../artifacts/newest/client.js');
  assert.equal(scripts[0].type,'module');
});

test('explicit bundle paths retain queries and use ordered fallbacks without timestamp cache busting',()=>{
  const scripts=boot({EDUSQUIRREL_APP_PATHS:['first.js?build=abc','second.js']});
  assert.equal(scripts[0].src,'first.js?build=abc');
  scripts[0].onerror();
  assert.equal(scripts[1].src,'second.js');
});

test('inline runtime configuration can also select the bootstrap bundle',()=>{
  const scripts=boot({EDUSQUIRREL_CLIENT_CONFIG:{EDUSQUIRREL_APP_SCRIPT_PRIMARY:'custom.js'}});
  assert.equal(scripts[0].src,'custom.js');
});
