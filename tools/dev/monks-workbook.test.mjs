import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';

const root = path.resolve(import.meta.dirname, '../..');

test('monk story images exist and each speaking turn has one localized caption', async () => {
  const source = await fs.readFile(path.join(root, 'modules/client/src/main/scala/it/evadid/homepage/workbook/content/CreateMonksWorkbook.scala'), 'utf8');
  const folder = path.join(root, 'resources/languageMaps/eva/monksworkbook');
  const universal = JSON.parse(await fs.readFile(path.join(folder, 'map-universal.json')));
  const german = JSON.parse(await fs.readFile(path.join(folder, 'map-de.json')));
  const scenes = [...source.matchAll(/Scene\("([^"]+)", "([^"]+)"([^\n]*)/g)];
  assert.equal(scenes.length, 86);
  assert.equal(german.silence, '...');
  for (const [, id, imageKey, options] of scenes) {
    const image = universal[imageKey];
    assert(image, id + ' has an image mapping');
    await fs.access(path.join(root, 'resources/workbookresources', image));
    const caption = options.match(/dialogueKey = "([^"]+)"/)?.[1] || 'silence';
    assert(german[caption], id + ' has a caption');
    if (caption !== 'silence') {
      assert.equal((german[caption].match(/\*\*[RMK]:\*\*/g) || []).length, 1, id + ' has one speaker');
      assert(!german[caption].includes('\n'), id + ' has one speaking turn');
    }
  }
  assert(!Object.keys(german).some(key => /narration|narrator/i.test(key)));
});
