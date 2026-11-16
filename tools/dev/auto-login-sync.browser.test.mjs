import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import {readCommandRequest} from './command-request.mjs';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const formats = ['ClassicJsonWithKey', 'ClassicKeylessJson', 'RelationalEvents'];
const user = {name:'Sync student', id:'sync-reset-student', mail:'sync-reset@example.test'};
const destinations = [
  {syncDestination:{$type:'LocalIndexedDbSyncDefinition',dbName:'EvaDidInteractionDB',storeName:'variableHistoryStore',dbVersion:1},syncStrategy:'SYNC_LAST_AND_MAJOR'},
  {syncDestination:{$type:'LocalStorageSyncDefinition',maxValueCharakterSize:50000},syncStrategy:'SYNC_LAST_AND_MAJOR'},
  ...formats.map(format => ({syncDestination:{$type:'SyncDestinationBasedOnHistoryDatabase',format,buffered:true,
    databaseConfig:{$type:'DatabaseConfig',dbFormat:'mysql',host:'example.test',port:'3306',database:'fixture'}},
    syncStrategy:{$type:'DesiredRelevance',desired:['MAJOR']}}))
];
const token = {info:{user,createdAt:'2026-01-01T00:00:00',expiresAt:'2099-01-01T00:00:00'},tokenStringWithSignature:'fixture-token'};
const profile = {user,credentials:{signedBackendToken:token,singleAccessToken:null,llmApiConfig:null,databaseConfig:null},
  config:{syncDestinations:destinations,isOnlineAccount:true}};
const context = {programId:'edusquirrel',scenarioId:'workbookTest',userId:user.id,keyForSerialisation:'demo-feedback-1_history'};
const answer = 'This answer was restored during the first automatic login.';
const savedHistory = {historyStates:{events:[{value:answer,updateImportance:'MAJOR',timestamp:'2026-01-01T12:00:00'}]},
  historyGrading:{gradingsMap:{}},historyScaffolding:{scaffoldingMap:{}}};
function envelope(command,value) {
  const now = new Date().toISOString().slice(0,-1);
  return JSON.stringify({timestamps:{timestampReceived:now,timestampStarted:now,timestampFinished:now},
    result:[1,{json:JSON.stringify(value),classSimpleName:'FixtureResponse'}],receivedExecutionCommand:command,
    additionalOutput:{loggerOut:'',loggerError:''}});
}
async function seed(page) {
  await page.evaluate(async ({profile}) => {
    function put(dbName,storeName,records) {
      return new Promise((resolve,reject) => {
        const open=indexedDB.open(dbName,1);
        open.onupgradeneeded=()=>open.result.createObjectStore(storeName,{keyPath:'id'});
        open.onerror=()=>reject(open.error);
        open.onsuccess=()=>{
          const db=open.result, transaction=db.transaction(storeName,'readwrite'),store=transaction.objectStore(storeName);
          for (const record of records) store.put(record);
          transaction.oncomplete=()=>{db.close();resolve();};
          transaction.onabort=()=>reject(transaction.error);
        };
      });
    }
    localStorage.setItem('obsolete-local-answer-fixture','obsolete data');
    await put('EvaDidCacheDb','cache',[{id:'edusquirrel-session',value:JSON.stringify(profile)},
      {id:'unrelated-cache-fixture',value:'keep this cache'}]);
    await put('EvaDidInteractionDB','variableHistoryStore',[{id:'unreadable-record-fixture',value:'obsolete data'}]);
  },{profile});
}
async function historyKeys(page) {
  return page.evaluate(()=>new Promise((resolve,reject)=>{
    const open=indexedDB.open('EvaDidInteractionDB',1);
    open.onerror=()=>reject(open.error);
    open.onsuccess=()=>{
      const db=open.result,transaction=db.transaction('variableHistoryStore','readonly');
      const request=transaction.objectStore('variableHistoryStore').getAllKeys();
      request.onsuccess=()=>resolve(request.result);
      transaction.oncomplete=()=>db.close();
    };
  }));
}

test('automatic login restores answers and keeps sync destinations stable across display changes and reloads',async()=>{
  const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||chromium.executablePath(),chromiumSandbox:true});
  try {
    const page=await browser.newPage();
    const requests=[], errors=[], histories=new Map(formats.map(format=>[format,JSON.stringify(savedHistory)]));
    let navigations=0;
    page.on('pageerror',error=>errors.push(error.message));
    page.on('framenavigated',frame=>{if(frame===page.mainFrame()&&frame.url().includes('/homepage/'))navigations++;});
    await page.route('**/*',async route=>{
      const url=new URL(route.request().url());
      if(url.pathname==='/seed')return route.fulfill({contentType:'text/html',body:'<html></html>'});
      if(url.pathname==='/executeCommand'){
        const headers={'access-control-allow-origin':origin,'access-control-allow-credentials':'true',
          'access-control-allow-methods':'POST, OPTIONS','access-control-allow-headers':'Content-Type, Authorization, Content-Encoding'};
        if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers});
        const command=readCommandRequest(route.request());requests.push(command);
        let value;
        if(command.name==='login-command')value={userKnown:true,tokenValid:true,allUserInfo:profile};
        else if(command.name==='fetch-from-db-request'){
          const json=histories.get(command.json.databaseFormat);
          value={fetchedElements:json?[[context,json]]:[]};
        } else if(command.name==='sync-to-db-request'){
          histories.set(command.json.databaseFormat,command.json.historyJson);
          value={elementsAdded:0,elementsChanged:1,elementsRemoved:0,timestampCommitted:new Date().toISOString().slice(0,-1)};
        } else throw new Error('Unexpected command '+command.name);
        return route.fulfill({headers,contentType:'application/json',body:envelope(command,value)});
      }
      if(url.origin!==origin)return route.abort();
      const file=path.resolve(root,'.'+decodeURIComponent(url.pathname)+(url.pathname.endsWith('/')?'index.html':''));
      if(!file.startsWith(root+path.sep))return route.abort();
      try {await route.fulfill({body:await readFile(file),contentType:mime[path.extname(file)]||'application/octet-stream'});}
      catch {await route.fulfill({status:404,body:'Not found'});}
    });
    await installCodeMirrorModules(page);
    await page.goto(origin+'/seed');await seed(page);
    await page.goto(origin+'/homepage/workbookDesign/');
    await page.locator('.section-block').last().click();
    const input=page.locator('.text-input-feedback textarea');
    await input.waitFor();
    await page.waitForFunction(expected=>document.querySelector('.text-input-feedback textarea')?.value===expected,answer);
    assert.equal(navigations,1,'the restored answer appears without reloading');
    await page.locator('.select-language-line > div:has(.flag-fill-german-red)').click();
    assert.equal(await input.inputValue(),answer);
    const edited='This updated answer should survive reload.';
    const writes = formats.map(format=>page.waitForResponse(response=>{
      if(!response.url().endsWith('/executeCommand')||response.request().method()!=='POST')return false;
      const command=readCommandRequest(response.request());
      return command.name==='sync-to-db-request'&&command.json.databaseFormat===format&&command.json.historyJson.includes(edited);
    }));
    await input.fill(edited);
    await Promise.all(writes);
    assert(formats.every(format=>JSON.parse(histories.get(format)).historyStates.events.some(event=>event.value===edited)));
    await page.reload();
    await page.locator('.section-block').last().click();
    await page.waitForFunction(expected=>document.querySelector('.text-input-feedback textarea')?.value===expected,edited);
    assert.equal(navigations,2);
    assert.equal(await page.locator('.container-login').count(),0,'reload preserves cached authentication');
    assert((await historyKeys(page)).includes('unreadable-record-fixture'),'one unreadable record must not erase the store');
    assert.deepEqual(errors,[]);
  } finally {await browser.close();}
});
