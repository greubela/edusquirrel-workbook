import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const root = path.resolve(import.meta.dirname, '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const maps = 'resources/languageMaps/eva';
const load = (directory, language) => {
  const file = `${maps}/${directory}/map-${language}.json`;
  return fs.existsSync(path.join(root, file)) ? JSON.parse(read(file)) : {};
};
const normalized = entries => new Map(Object.entries(entries).map(([key, value]) => [key.toLowerCase(), value]));

// Validate language-map contracts without snapshotting individual lessons.
test('all native workbook and shared UI maps have German and English entries', () => {
  for (const directory of ['monksworkbook', 'embroideryworkbook', 'embroiderypreviewworkbook',
    'plantworkshop', 'blockchainworkbook', 'digitalWorkbooks', 'testworkbook', 'basic',
    'login', 'workbookSelection', 'emailSimulator', 'blockeditor', 'turtlestitch']) {
    const german = normalized(load(directory, 'de'));
    const english = normalized(load(directory, 'en'));
    const universal = normalized(load(directory, 'universal'));
    assert(german.size && english.size, `${directory}: both language files must exist`);
    for (const key of new Set([...german.keys(), ...english.keys()])) {
      for (const [language, entries] of [['de', german], ['en', english]]) {
        assert((entries.get(key) ?? universal.get(key))?.trim(), `${directory}/${key}: missing ${language}`);
      }
    }
  }
});

const placeholders = value => [...value.matchAll(/\{([A-Za-z]\w*)\}/g)].map(match => match[1]).sort();
test('translations preserve interpolation fields', () => {
  for (const directory of ['monksworkbook', 'compressionworkbook']) {
    const de = load(directory, 'de'), en = load(directory, 'en');
    for (const [key, value] of Object.entries(en)) {
      assert(Object.hasOwn(de, key), `${directory}/${key}: missing German equivalent`);
      assert.deepEqual(placeholders(value), placeholders(de[key]), `${directory}/${key}: interpolation fields`);
    }
  }
});
