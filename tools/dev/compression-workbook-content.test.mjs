import assert from 'node:assert/strict';
import {readFile, access} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';

const root = path.resolve(import.meta.dirname, '../..');
test('native compression content resolves its language keys and contains no promised widget placeholders', async () => {
  const source = await readFile(path.join(root, 'modules/client/src/main/scala/it/evadid/homepage/workbook/content/CreateCompressionWorkbook.scala'), 'utf8');
  const map = JSON.parse(await readFile(path.join(root, 'resources/languageMaps/eva/compressionworkbook/map-de.json'), 'utf8'));
  const keys = [...source.matchAll(/(?:t|experiment)\("(\w+)"/g)].map(m => m[1]);
  for (const key of keys) assert(map[key]?.trim(), `Missing compression content: ${key}`);
  for (const [key, value] of Object.entries(map)) assert(!/Kommt bald|\[ Widget:/.test(value), `Placeholder still present: ${key}`);
  assert(!/iframe|20260907Datenkompression\/js\//.test(source));
  const page = await readFile(path.join(root, 'homepage/compressionWorkbook/index.html'), 'utf8');
  assert(page.includes('../js/app-loader.js'));
  assert(page.includes('compression-editor.css'));
  for (const value of Object.values(map)) {
    for (const [, href] of value.matchAll(/href=['"]([^'"]+)['"]/g)) {
      if (href.startsWith('../../resources/')) await access(path.resolve(root, 'homepage/compressionWorkbook', decodeURIComponent(href)));
    }
  }
  const txt = await readFile(path.join(root, 'resources/workbookresources/compression/document-content.txt'), 'utf8');
  assert(map.s3Task1Widget.includes(txt), 'comparison download matches visible text');
  assert(map.s3Task1Widget.includes('2545 Byte'), 'Word size matches the supplied source file');
  assert(map.imageCostNote.includes('keine JPEG-Dateigröße'));
  assert(map.archiveCostNote.includes('nicht gemessen'));
});
