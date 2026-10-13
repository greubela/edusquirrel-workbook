import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import vm from 'node:vm';
import test from 'node:test';

const root=path.resolve(import.meta.dirname,'../..');
const source=await fs.readFile(path.join(root,'homepage/js/app-loader.js'),'utf8');
async function boot(overrides={}, fetchImpl=async()=>new Promise(()=>{})) {
  const scripts=[], workers=[];
  const window={location:{origin:'https://example.test'},EduSquirrelCodeMirrorReady:new Promise(()=>{}),...overrides};
  const document={readyState:'complete',head:{appendChild:script=>scripts.push(script)},createElement:()=>({}),getElementsByTagName:()=>[{src:'https://example.test/js/CodeMirrorLoader.js'}]};
  vm.runInNewContext(source,{window,document,console,Date,Promise,
    fetch:fetchImpl,
    Worker:class {constructor(url){workers.push(url);}},
    setTimeout:()=>{throw new Error('optional editor must not delay startup');}});
  await new Promise(resolve=>setImmediate(resolve));
  return {scripts,workers,window};
}

test('the app loads even when CodeMirror readiness never resolves',async()=>{
  const {scripts,workers}=await boot();
  assert.equal(scripts.length,1);
  assert.match(scripts[0].src,/artifacts\/newest\/client\.js$/);
  assert.equal(scripts[0].type,'module');
  assert.equal(workers.length,0, "workers belong to the Scala.js background phase");
});

test('configured bundle fallbacks and explicit page overrides remain usable',async()=>{
  const {scripts}=await boot({EDUSQUIRREL_APP_PATHS:['first.js','second.js']});
  assert.match(scripts[0].src,/^first\.js$/);
  scripts[0].onerror();
  assert.match(scripts[1].src,/^second\.js$/);
});

test('bootstrap does not fetch configuration before the first bundle and honors explicit versions', async()=>{
  let fetches=0;
  const {scripts}=await boot({EDUSQUIRREL_APP_VERSION:'release 2'},async()=>{fetches++;return {ok:true,text:async()=>''};});
  assert.equal(fetches,0);
  assert.match(scripts[0].src,/client\.js\?v=release%202$/);
});

test('legacy configuration paths are discovered only after the default bundle fails', async()=>{
  let fetches=0;
  const {scripts}=await boot({},async()=>{fetches++;return {ok:true,text:async()=> 'EDUSQUIRREL_APP_SCRIPT_PRIMARY=custom-client.js'};});
  assert.equal(fetches,0);
  scripts[0].onerror();
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(fetches,1);
  assert.equal(scripts[1].src,'custom-client.js');
});
