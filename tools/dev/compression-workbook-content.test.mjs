import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import test from 'node:test';
import {JSDOM} from 'jsdom';
const root=path.resolve(import.meta.dirname,'../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const base='resources/programs/20260907Datenkompression/';
const inventory=JSON.parse(read('resources/workbookresources/compression/source-inventory.json'));
const generated=read('modules/client/src/main/scala/it/evadid/homepage/workbook/content/CompressionSourceContent.scala');
const map=JSON.parse(read('resources/languageMaps/eva/compressionworkbook/map-de.json'));
const corrections=JSON.parse(read('resources/workbookresources/compression/source-corrections.json'));
const doc=new JSDOM(read(base+'index.html')).window.document;
const c={window:{}};vm.createContext(c);vm.runInContext(read(base+'lang/de.js'),c);
const sourceValue=k=>k.split('.').reduce((v,k)=>v[k],c.window.LANG_DE);
const key=k=>'src_'+k.replaceAll('.','_');
test('every original chapter, text answer, choice, widget and hint has a native node',()=>{
 assert.deepEqual(inventory.sections,[...doc.querySelectorAll('main>section.panel')].map(e=>e.id));
 assert.equal(inventory.sections.length,7);
 const textareas=[...doc.querySelectorAll('textarea[id]')].map(e=>e.id).filter(id=>id!=='answer-intro-ethics');
 assert.deepEqual(inventory.answers,textareas);
 assert.equal(inventory.answers.length,41);
 assert.deepEqual(inventory.widgets,[...doc.querySelectorAll('[id^="widget-"]')].map(e=>e.id));
 assert.equal(inventory.widgets.length,17);
 assert.deepEqual(inventory.forms,[...doc.querySelectorAll('form')].map(e=>e.id));
 assert.equal(inventory.forms.length,8);
 assert.equal(inventory.hints.length,9);
 for(const id of [...inventory.answers,...inventory.widgets,...inventory.forms.filter(x=>x!=='ethics-form'),...inventory.hints])assert(generated.includes(JSON.stringify(id)),id);
 assert(generated.includes('answer-intro-ethics'));
 assert(generated.includes('calculator-photo'));
 assert(generated.includes('calculator-video'));
});
test('all original lesson text remains present; technical corrections are explicit',()=>{
 for(const k of [...inventory.languageKeys,...inventory.hints]){
  assert(map[key(k)]?.trim(),`unresolved: ${k}`);
  const actual=new JSDOM(map[key(k)]).window.document.body.textContent;
  const original=new JSDOM(sourceValue(k)).window.document.body.textContent;
  assert(actual.startsWith(original),`text omitted/changed: ${k}`);
 }
 for(const [k,v] of Object.entries(corrections))assert.equal(map[k],v,k);
 assert.match(map.src_lossless_task4_quoteEncrypted,/Base64.*nicht verschlüsselt/);
 assert.match(map.src_filetypes_task4_intro,/ZIP komprimiert Dateien.*einzeln/);
});
test('materials, six illustrations and exact image data resolve without standalone scripts at runtime',()=>{
 const source=read('modules/client/src/main/scala/it/evadid/homepage/workbook/content/CreateCompressionWorkbook.scala');
 assert(!/iframe|20260907Datenkompression\/js\//.test(source));
 for(const value of Object.values(map))for(const [,href] of value.matchAll(/href=['"]([^'"]+)['"]/g))if(href.startsWith('../../resources/'))
  assert(fs.existsSync(path.resolve(root,'homepage/compressionWorkbook',decodeURIComponent(href))),href);
 assert(map.materialsDownloads.includes('Material.zip'));
 assert(fs.readFileSync(path.join(root,base,'Material/Sektion 2/Screenshot.jpg')).subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])));
 assert.match(map.screenshotFormatNote,/PNG-Daten/);
 for(let i=0;i<6;i++)assert(fs.statSync(path.join(root,'resources',map['jpegImage'+i])).size>1000);
 const embedded=read(base+'img/katze-data.js').match(/base64,([^"']+)/)[1];
 assert.deepEqual(fs.readFileSync(path.join(root,'resources/workbookresources/compression/source-cat.jpg')),Buffer.from(embedded,'base64'));
 assert(read('homepage/compressionWorkbook/index.html').includes('../js/app-loader.js'));
 const catalog=new JSDOM(read('homepage/index.html')).window.document;
 assert(catalog.querySelector('a[href="./compressionWorkbook/"]'));
 assert(catalog.querySelector('a[href="../resources/programs/20260907Datenkompression/"]'));
});
