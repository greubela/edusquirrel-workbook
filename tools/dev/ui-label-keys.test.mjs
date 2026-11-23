import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

// Scala unit tests render only placeholders for missing labels, so check here that every label
// key the feedback panels, step-by-step panels, deferred renderings, data previews and 2026GdP1 use is translated.
const root = path.resolve(import.meta.dirname, '../..');
const client = 'modules/client/src/main/scala/it/evadid/homepage/workbook';
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const sources = dir => fs.readdirSync(path.join(root, dir)).filter(name => name.endsWith('.scala')).map(name => read(`${dir}/${name}`));
const languages = ['de', 'en', 'dk'];
const map = (group, language) => new Map(Object.entries(JSON.parse(read(`resources/languageMaps/eva/${group}/map-${language}.json`)))
  .map(([key, value]) => [key.toLowerCase(), value]));
const matches = (text, pattern) => [...text.matchAll(pattern)].map(match => match[1]);

function assertTranslated(group, keys) {
  assert(keys.size > 0, `${group}: no keys found; the extraction patterns are out of date`);
  for (const language of languages) {
    const entries = map(group, language);
    for (const key of keys) assert(entries.get(key.toLowerCase())?.trim(), `${group}/${key}: missing ${language}`);
  }
}

test('feedback, step and data-preview labels exist in German, English and Danish', () => {
  const files = [
    ...sources(`${client}/htmlRenderer/controlElements/interactionInfo`),
    ...sources(`${client}/htmlRenderer/interactionRenderer/steps`),
    read(`${client}/htmlRenderer/interactionRenderer/basic/ProgramDataPreview.scala`),
    read(`${client}/htmlRenderer/interactionRenderer/basic/HtmlProgrammingExerciseFullJavaRenderer.scala`),
    read(`${client}/htmlRenderer/interactionRenderer/turtleStitch/HtmlTurtleRecreateShapeRenderer.scala`),
    read('modules/client/src/main/scala/it/evadid/homepage/webElements/basic/DeferredRendering.scala'),
  ].join('\n');
  const keys = new Set([
    ...matches(files, /"basic\/([A-Za-z]+)"/g),
    // Keys passed to label helpers or returned as status/tone keys.
    ...matches(files, /(?:text|label)\("([A-Za-z]+)"\)/g),
    ...matches(files, /=> "((?:grading|scaffolding|requestScaffolding|textGrading)[A-Za-z]*)"/g),
    ...matches(files, /Tone\("[a-z]+", FeedbackIcon\.[A-Za-z]+, "([A-Za-z]+)"\)/g),
    ...matches(files, /\("(sortStep[A-Za-z]+)"/g),
    // Every key named by the Scala code, including those the step panels build captions from.
    ...['stepNavigation', 'stepPosition', 'stepCommand', 'programDataTitle', 'programDataFailed', 'programDataLimit'],
  ]);
  assertTranslated('basic', keys);
});

test('2026GdP1 lesson texts and test descriptions exist in German, English and Danish', () => {
  const factory = read(`${client}/content/CreateGdp1Workbook.scala`);
  const keys = new Set([
    ...matches(factory, /key\("([A-Za-z0-9]+)"\)/g),
    ...matches(factory, /content\("([A-Za-z0-9]+)"\)/g),
    ...matches(factory, /DataTest\("([A-Za-z0-9]+)"/g),
    ...matches(factory, /sortedTest\("([A-Za-z0-9]+)"/g),
    ...matches(factory, /-> "(test[A-Za-z0-9]+)"/g),
    // Container titles and instructions built from the task name.
    ...['swap', 'bubble', 'sudoku'].flatMap(name => [`${name}Title`, `${name}Instruction`]),
    ...[1, 2, 3, 4, 5, 6, 7].flatMap(number => [`task${number}Title`, `task${number}Instruction`]),
  ]);
  assertTranslated('2026gdp1', keys);
});
