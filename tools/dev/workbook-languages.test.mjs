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

function germanLanguageMaps(directory) {
  return fs.readdirSync(directory, {withFileTypes:true}).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? germanLanguageMaps(file) : entry.name.endsWith('-de.json') ? [file] : [];
  });
}

test('every German language-map entry has an explicit nonempty Danish translation', () => {
  const files = germanLanguageMaps(path.join(root, 'resources/languageMaps'));
  assert(files.length > 0);
  for (const germanFile of files) {
    const danishFile = germanFile.replace(/-de\.json$/, '-dk.json');
    assert(fs.existsSync(danishFile), `Missing Danish map: ${path.relative(root, danishFile)}`);
    const german = JSON.parse(fs.readFileSync(germanFile, 'utf8'));
    const danish = JSON.parse(fs.readFileSync(danishFile, 'utf8'));
    for (const [key, value] of Object.entries(german)) {
      assert.equal(typeof danish[key], 'string', `${danishFile}/${key}: missing Danish entry`);
      if (value.trim()) assert(danish[key].trim().length > 0, `${danishFile}/${key}: empty Danish entry`);
      const fields = text => [...text.matchAll(/\{[A-Za-z]\w*\}|%[a-zA-Z][a-zA-Z0-9_]*|(?<!\w)_(?!\w)/g)].map(match => match[0]).sort();
      assert.deepEqual(fields(danish[key]), fields(value), `${danishFile}/${key}: interpolation fields differ`);
      const markdownLinks = text => [...text.matchAll(/\[[^\]]*\]\(([^)]*)\)/g)].map(match => match[1]).sort();
      assert.deepEqual(markdownLinks(danish[key]), markdownLinks(value), `${danishFile}/${key}: Markdown link targets differ`);
      for (const [literal] of value.matchAll(/[A-Za-z0-9+/=]{60,}/g)) {
        assert(danish[key].includes(literal), `${danishFile}/${key}: encoded example was changed`);
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
