import assert from 'node:assert/strict';
import {access, readdir, readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import test from 'node:test';

const root = path.resolve(import.meta.dirname, '../..');
const dedicatedCss = ['email-simulator.css', 'digital-workbooks.css']
  .map(file => path.join(root, 'homepage/css/workbook', file));
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

test('every workbook page explicitly loads the dedicated simulator and digital exercise stylesheets once', async () => {
  for (const {file,html} of await workbookPages()) {
    for (const css of dedicatedCss)
      assert.equal(stylesheetHrefs(html).filter(href=>localPath(href,file)===css).length,1,path.relative(root,file) + ': ' + path.basename(css));
    assert(!/<style(?:\s|>)/i.test(html) && !/\sstyle\s*=/.test(html),'workbook pages use external stylesheets');
  }
});

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

test('mail and digital exercise components bind classes rather than inline Laminar styles', async () => {
  const directories=[
    'modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/MailEditor',
    'modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/emailSimulator',
    'modules/client/src/main/scala/it/evadid/homepage/webElements/editor/neuron',
    'modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/neuron',
    'modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/choice',
    'modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/table',
    'modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/pixel',
    'modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/blockchain',
    'modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/text'
  ];
  const inlineStyle=/\b(?:styleAttr|color|backgroundColor|fontSize|fontFamily|width|height|padding|margin|border|display)\s*(?::=|<--)|\bstyleTag\s*\(|\.style\s*(?:\.|=)/;
  for (const directory of directories) {
    for (const file of await filesBelow(path.join(root,directory),'.scala'))
      assert(!inlineStyle.test(await readFile(file,'utf8')),path.relative(root,file));
  }
});

test('every authored stylesheet is reachable from a homepage entry page', async () => {
  // Include the landing page and feedback studio: their styles are used outside workbooks.
  const visited = new Set();
  async function visit(file) {
    if (visited.has(file)) return;
    visited.add(file);
    const css = await readFile(file, 'utf8');
    for (const match of css.matchAll(/@import\s+(?:url\(\s*['"]?([^'"\)]+)['"]?\s*\)|['"]([^'"]+)['"])\s*;/g)) {
      const imported = localPath(match[1] || match[2], file);
      if (imported) await visit(imported);
    }
  }
  for (const file of await filesBelow(path.join(root, 'homepage'), path.sep + 'index.html')) {
    for (const href of stylesheetHrefs(await readFile(file, 'utf8'))) {
      const css = localPath(href, file);
      if (css) await visit(css);
    }
  }
  const unused = (await filesBelow(path.join(root, 'homepage/css'), '.css'))
    .filter(file => !visited.has(file)).map(file => path.relative(root, file));
  assert.deepEqual(unused, [], 'remove orphan stylesheets or link them from their entry page');
});

test('digital exercise CSS references defined shared color and dimension tokens', async () => {
  const css = await readFile(path.join(root, 'homepage/css/workbook/digital-workbooks.css'), 'utf8');
  const definitions = (await Promise.all(['colors.css', 'dimensions.css'].map(file =>
    readFile(path.join(root, 'homepage/css/generic', file), 'utf8')))).join('\n');
  const used = new Set([...css.matchAll(/var\((--[\w-]+)/g)].map(match => match[1]));
  const defined = new Set([...definitions.matchAll(/(--[\w-]+)\s*:/g)].map(match => match[1]));
  assert(used.size > 0, 'exercise styles reuse shared tokens');
  assert.deepEqual([...used].filter(token => !defined.has(token)), [], 'undefined CSS tokens invalidate declarations');
});
