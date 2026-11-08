import assert from 'node:assert/strict';
import {access, readdir, readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import test from 'node:test';

const root = path.resolve(import.meta.dirname, '../..');
async function filesBelow(directory, extension) {
  const files = [];
  for (const entry of await readdir(directory, {withFileTypes:true})) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) files.push(...await filesBelow(file, extension));
    else if (file.endsWith(extension)) files.push(file);
  }
  return files;
}
async function workbookPages() {
  const pages=[];
  for (const file of await filesBelow(path.join(root,'homepage'), path.sep + 'index.html')) {
    const html=await readFile(file,'utf8');
    if (/id=["'](?:workbook[^"']*|plantWorkshopApp)["']/.test(html)) pages.push({file,html});
  }
  assert(pages.length>0, 'discover the workbook entry pages');
  return pages;
}
function localPath(href, from) {
  const url=new URL(href,pathToFileURL(from));
  return url.protocol==='file:' ? fileURLToPath(url) : null;
}

// Workbook entry pages have static, quoted link attributes; no DOM or npm package is needed.
function stylesheetHrefs(html) {
  return [...html.matchAll(/<link\b[^>]*>/gi)].flatMap(([tag]) => {
    const attributes=Object.fromEntries([...tag.matchAll(/([\w:-]+)\s*=\s*(["'])(.*?)\2/g)]
      .map(([,name,,value])=>[name.toLowerCase(),value]));
    return attributes.rel?.toLowerCase()==='stylesheet' ? [attributes.href] : [];
  });
}

test('workbook stylesheet links and their local imports all resolve to existing CSS files', async () => {
  const visited=new Set();
  async function check(file) {
    if (visited.has(file)) return;
    visited.add(file);
    await access(file);
    assert.equal(path.extname(file),'.css');
    const css=await readFile(file,'utf8');
    for (const match of css.matchAll(/@import\s+(?:url\(\s*['"]?([^'"\)]+)['"]?\s*\)|['"]([^'"]+)['"])\s*;/g)) {
      const imported=localPath(match[1] || match[2],file);
      if (imported) await check(imported);
    }
  }
  for (const {file,html} of await workbookPages()) {
    for (const href of stylesheetHrefs(html)) {
      const css=localPath(href,file);
      if (css) await check(css);
    }
  }
});
