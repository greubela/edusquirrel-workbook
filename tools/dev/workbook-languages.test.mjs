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

// Native exercises share these maps; imported standalone dictionaries are audited
// through their actual native lesson and editor references below.
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

const compression = load('compressionworkbook', 'de');
const englishCompression = load('compressionworkbook', 'en');
const sourceKey = key => 'src_' + key.replaceAll('.', '_');
const quoted = source => [...source.matchAll(/"([^"\n]+)"/g)].flatMap(match => match[1].split('|'));
function nativeCompressionKeys() {
  const required = new Set(['workbookTitle', 'materialsDownloads', 'inspectorFormattedLetter', 'screenshotFormatNote']);
  const files = ['CreateCompressionWorkbook.scala', 'CompressionSourceContent.scala'].map(file =>
    `modules/client/src/main/scala/it/evadid/homepage/workbook/content/${file}`);
  const editor = 'modules/client/src/main/scala/it/evadid/homepage/webElements/editor/compression';
  files.push(...fs.readdirSync(path.join(root, editor)).filter(file => file.endsWith('.scala')).map(file => `${editor}/${file}`));
  for (const file of files) for (const value of quoted(read(file))) {
    for (const key of [value, sourceKey(value)]) if (Object.hasOwn(compression, key)) required.add(key);
  }
  const dynamic = [
    /^src_intro_ethics/, /^src_widget_fileInspector_/, /^src_widget_zipArchive_faq_/,
    /^src_widget_filesystemSimulator_(tools|tutorialSteps|scenarios)_/,
    /^src_widget_filesystem_fileTypesOverview_items_/, /^src_widget_rle_challenges_/,
    /^src_lossy_task3_challenge/, /^jpegImage[0-5]$/, /^src_lossy_task2_slides_\d_caption$/,
    /^src_widget_(efficiency|lossyClosing)_(option\w+|reasonPrompt)$/,
    /^src_widget_(efficiency|lossyClosing)_files_\d_(nativeLabel|feedbackWrong)$/
  ];
  for (const key of Object.keys(compression)) if (dynamic.some(pattern => pattern.test(key))) required.add(key);
  return required;
}

test('every native Compression lesson, slideshow, choice, hint and editor label is bilingual', () => {
  const required = nativeCompressionKeys();
  assert(required.size > 350, 'audit covers the complete native reconstruction');
  for (const key of required) {
    assert(compression[key]?.trim(), `${key}: missing German`);
    assert(englishCompression[key]?.trim(), `${key}: missing English`);
  }
});

const placeholders = value => [...value.matchAll(/\{([A-Za-z]\w*)\}/g)].map(match => match[1]).sort();
const links = value => [...value.matchAll(/href=["']([^"']+)["']/g)].map(match => match[1]).sort();
test('translations preserve interpolation fields and original download/source links', () => {
  for (const directory of ['monksworkbook', 'compressionworkbook']) {
    const de = load(directory, 'de'), en = load(directory, 'en');
    for (const [key, value] of Object.entries(en)) {
      assert(Object.hasOwn(de, key), `${directory}/${key}: missing German equivalent`);
      assert.deepEqual(placeholders(value), placeholders(de[key]), `${directory}/${key}: interpolation fields`);
      assert.deepEqual(links(value), links(de[key]), `${directory}/${key}: links`);
    }
  }
});

test('English encoded examples correspond to their translated plaintext and keep the technical correction', () => {
  for (const prefix of ['src_lossless_task4', 'src_losslessOptional_task2']) {
    const value = englishCompression[prefix + '_quoteEncrypted'];
    assert.equal(Buffer.from(value.split('<')[0], 'base64').toString('utf8'), englishCompression[prefix + '_quotePlain']);
    assert.match(value, /not encrypt|not encryption/);
  }
  const encoded = englishCompression.src_losslessOptional_task2_quoteCaesar;
  const decoded = encoded.replace(/[a-z]/gi, char => {
    const base = char <= 'Z' ? 65 : 97;
    return String.fromCharCode((char.charCodeAt(0) - base + 23) % 26 + base);
  });
  assert.equal(decoded, englishCompression.src_losslessOptional_task2_quotePlain);
});


test('the file-inspection specimen has the same source text in both language editions', () => {
  assert.equal(englishCompression.inspectorFormattedLetter, compression.inspectorFormattedLetter);
  assert.equal(englishCompression.src_widget_fileInspector_txtContent, compression.src_widget_fileInspector_txtContent);
});
