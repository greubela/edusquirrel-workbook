import {installCodeMirrorModules} from './codemirror-test-modules.mjs';
import {readCommandRequest} from './command-request.mjs';
import {syncSuccess} from './sync-success-fixture.mjs';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
const origin = 'http://localhost:9000';
const mime = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const formats = ['BlobFormat', 'RelationalFormat'];
const user = {name:'Sync student', id:'sync-reset-student', mail:'sync-reset@example.test'};
const destinations = [
  {syncDestination:{$type:'LocalIndexedDbSyncDefinition',dbName:'EvaDidInteractionDB',storeName:'variableHistoryStore',dbVersion:1},syncStrategy:'SYNC_EVERYTHING'},
  {syncDestination:{$type:'LocalStorageSyncDefinition',maxValueCharakterSize:50000},syncStrategy:'SYNC_EVERYTHING'},
  ...formats.map(format => ({syncDestination:{$type:'SyncDestinationBasedOnHistoryDatabase',format,buffered:true,
    databaseConfig:{$type:'DatabaseConfig',dbFormat:'mysql',host:'example.test',port:'3306',database:'fixture'}},
    syncStrategy:'SYNC_EVERYTHING'}))
];
const token = {info:{user,createdAt:'2026-01-01T00:00:00.000000000Z',expiresAt:'2099-01-01T00:00:00.000000000Z'},tokenStringWithSignature:'fixture-token'};
const profile = {user,credentials:{signedBackendToken:token,singleAccessToken:null,llmApiConfig:null,databaseConfig:null},
  config:{syncDestinations:destinations,isOnlineAccount:true}};
const context = {programId:'edusquirrel',scenarioId:'workbookTest',userId:user.id,keyForSerialisation:'demo-feedback-1_history'};
const answer = 'This answer was restored during the first automatic login.';
const savedHistory = {format:'TextInputWithLlmGradingAndScaffoldingFormat', historyJson:{events:{values:[
  {$type:'AnswerChangedEvent',eventId:'a86a2e47-40ed-4c65-9fd7-01bf45460b73',newState:answer,updateImportance:'MAJOR',timestamp:'2026-01-01T12:00:00.000000000Z'}]}}};
function mergeHistory(previous,incoming) {
  assert.equal(previous.format,incoming.format);
  const events=[...previous.historyJson.events.values,...incoming.historyJson.events.values];
  const byId = new Map();
  for (const event of events) {
    if (byId.has(event.eventId)) assert.deepEqual(event, byId.get(event.eventId), 'an event ID names immutable content');
    else byId.set(event.eventId, event);
  }
  return {...incoming,historyJson:{events:{values:[...byId.values()]}}};
}
function envelope(command,value) {
  const now = new Date().toISOString().replace('Z', '000000Z');
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

test('automatic login restores answers; full reset clears every destination, pauses failed resets and retries',async()=>{
  const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||chromium.executablePath(),chromiumSandbox:true});
  try {
    const page=await browser.newPage();
    const requests=[], errors=[], histories=new Map(formats.map(format=>[format,savedHistory]));
    let navigations=0, failReset=true, confirmReset=false, deleteAttempts=0;
    page.on('dialog',dialog=>confirmReset?dialog.accept():dialog.dismiss());
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
        else if(command.name==='fetch-events-from-db-request'){
          const json=histories.get(command.json.databaseFormat);
          value={fetchedElements:json?[[context,json]]:[]};
        } else if(command.name==='sync-histories-to-db-request'){
          const previous = histories.get(command.json.databaseFormat);
          const previousIds = new Set(previous ? previous.historyJson.events.values.map(event => event.eventId) : []);
          const storedIds = new Set();
          for (const history of command.json.histories) {
            const events = history.historyJson.events.values;
            if (events.some(event => event.newState === answer || event.newState === 'This updated answer should survive reload.')) {
              histories.set(command.json.databaseFormat,mergeHistory(histories.get(command.json.databaseFormat),history));
              events.forEach(event => storedIds.add(event.eventId));
            }
          }
          value = syncSuccess({elementsAdded: [...storedIds].filter(id => !previousIds.has(id)),
            elementsUnchanged: [...storedIds].filter(id => previousIds.has(id))});
        } else if(command.name==='reset-user-histories-in-db-request'){
          deleteAttempts++;
          assert.equal(command.json.allWorkbooks,true);
          assert.equal(command.json.usageContext.userId,user.id);
          assert.equal(command.json.limitToKey,null);
          if(failReset&&command.json.databaseFormat==='BlobFormat')
            return route.fulfill({status:503,headers,body:'fixture unavailable'});
          const removed = histories.has(command.json.databaseFormat) ? [context] : [];
          histories.delete(command.json.databaseFormat);
          value = syncSuccess({elementsRemoved: removed});
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
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').click();
    const feedbackTitle = JSON.parse(await readFile(path.join(root, 'resources/languageMaps/eva/testworkbook/map-en.json'), 'utf8')).sec7;
    await page.locator('.section-block').filter({hasText:feedbackTitle}).click();
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
      return command.name==='sync-histories-to-db-request'&&command.json.databaseFormat===format&&command.json.histories.some(history=>JSON.stringify(history.historyJson).includes(edited));
    }));
    await input.fill(edited);
    await Promise.all(writes);
    assert(formats.every(format=>histories.get(format).historyJson.events.values.some(event=>event.newState===edited)));
    await page.reload();
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').click();
    await page.locator('.section-block').filter({hasText:feedbackTitle}).click();
    await page.waitForFunction(expected=>document.querySelector('.text-input-feedback textarea')?.value===expected,edited);
    assert.equal(navigations,2);
    assert.equal(await page.locator('.container-login').count(),0,'reload preserves cached authentication');
    assert((await historyKeys(page)).includes('unreadable-record-fixture'),'one unreadable record must not erase the store');
    await page.locator('.select-language-line > div:has(.flag-fill-german-red)').click();
    await page.locator('.workbook-user-menu-button').click();
    const reset=page.getByRole('menuitem',{name:'Alle gespeicherten Antworten zurücksetzen',exact:true});
    await reset.click();
    assert.equal(deleteAttempts,0,'dismissing confirmation performs no deletion');
    confirmReset=true;
    await reset.click();
    await page.getByRole('alert').filter({hasText:'Nicht alle Ziele'}).waitFor();
    assert.equal(deleteAttempts,2,'both backend layouts were attempted despite one failure');
    assert.equal(navigations,2,'partial failure must not reload');
    assert.deepEqual(await historyKeys(page),[],'full local reset clears malformed records without parsing');
    assert.equal(await page.evaluate(()=>localStorage.getItem('obsolete-local-answer-fixture')),null);
    const storesAfterFailure=requests.filter(r=>r.name==='sync-histories-to-db-request').length;
    await input.fill('Editing during a failed reset must not recreate deleted rows.');
    await page.waitForTimeout(150);
    assert.equal(requests.filter(r=>r.name==='sync-histories-to-db-request').length,storesAfterFailure);
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').click();
    await page.getByRole('alert').filter({hasText:'Not every destination'}).waitFor();
    await page.locator('.workbook-user-menu-button').click();
    failReset=false;
    await Promise.all([
      page.waitForEvent('framenavigated', frame => frame === page.mainFrame()),
      page.getByRole('menuitem',{name:'Retry full reset',exact:true}).click()
    ]);
    await page.locator('.select-language-line > div:has(.flag-fill-british-blue)').click();
    await page.locator('.section-block').filter({hasText:feedbackTitle}).click();
    await page.locator('.text-input-feedback textarea').waitFor();
    assert.equal(await page.locator('.text-input-feedback textarea').inputValue(),'');
    assert.equal(navigations,3,'successful reset reloads exactly once');
    assert.equal(deleteAttempts,4);
    assert.equal(histories.size,0);
    assert(!(await historyKeys(page)).includes('unreadable-record-fixture'));
    assert.equal(await page.evaluate(()=>new Promise(resolve=>{
      const open=indexedDB.open('EvaDidCacheDb',1);
      open.onsuccess=()=>{
        const db=open.result, transaction=db.transaction('cache','readonly');
        const request=transaction.objectStore('cache').get('unrelated-cache-fixture');
        request.onsuccess=()=>resolve(request.result?.value);
        transaction.oncomplete=()=>db.close();
      };
    })),'keep this cache');
    assert.equal(await page.locator('.container-login').count(),0,'reset preserves cached authentication');
    assert.deepEqual(errors,[]);
  } finally {await browser.close();}
});
