import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';

const root=path.resolve(import.meta.dirname,'../..');

test('catalogue translations, native targets and artwork exist', async()=>{
  const source=await fs.readFile(path.join(root,'modules/client/src/main/scala/it/evadid/homepage/workbook/content/DigitalWorkbookCatalog.scala'),'utf8');
  const en=JSON.parse(await fs.readFile(path.join(root,'resources/languageMaps/eva/workbookSelection/map-en.json')));
  const de=JSON.parse(await fs.readFile(path.join(root,'resources/languageMaps/eva/workbookSelection/map-de.json')));
  assert.deepEqual(Object.keys(en).sort(),Object.keys(de).sort());
  for(const [,id] of source.matchAll(/(?:workbook|Entry)\("([^"]+)"/g)) {
    assert(en[id+'-title'] && en[id+'-description']);
    assert(de[id+'-title'] && de[id+'-description']);
  }
  for(const [,id,author,image,container,page] of source.matchAll(/workbook\("([^"]+)", "([^"]+)", "([^"]+)", "([^"]+)", "([^"]+)"/g)) {
    await fs.access(path.join(root,'resources/img/art/mockup',image));
    const html=await fs.readFile(path.join(root,'homepage',page,'index.html'),'utf8');
    assert(html.includes(`id="${container}"`));
    assert(html.includes('workbook-catalog.css'));
  }
  const entry=await fs.readFile(path.join(root,'homepage/workbooks/index.html'),'utf8');
  assert(entry.includes('id="landingPage"'));
  assert(!entry.includes('CodeMirrorLoader') && !entry.includes('fullturtle.js'), 'catalogue does not load editor dependencies');
  assert(!entry.includes('<figure'), 'catalogue content is rendered by Laminar');
});
