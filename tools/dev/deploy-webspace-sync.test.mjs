import assert from 'node:assert/strict';
import {mkdirSync, mkdtempSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {formatManifest, listFiles, parseManifest, plan, progressManifests, stageWebspace}
  from '../../deployment/client/webspace-sync.mjs';

test('uploads changed and new files and deletes only previously deployed ones', () => {
  const previous = new Map([['same', '1'], ['changed', '2'], ['gone', '3']]);
  const current = new Map([['same', '1'], ['changed', 'X'], ['new', '4'], ['resources/pdf/a', '5'], ['resourcesX', '6']]);
  assert.deepEqual(plan(current, previous, ['resources']), {uploads: ['changed', 'new', 'resourcesX'], deletes: ['gone']});
  // Trust only covers files without a previous hash; recorded files are compared normally.
  assert.deepEqual(plan(new Map([['resources/a', 'new']]), new Map([['resources/a', 'old']]), ['resources']).uploads, ['resources/a']);
});

test('manifests round-trip, including names with spaces and umlauts', () => {
  const hash = c => c.repeat(64);
  const entries = new Map([['resources/Übersicht.txt', hash('a')], ['Math World/Präsentation "1".pdf', hash('b')]]);
  const text = formatManifest(entries);
  assert.equal(text.split('\n')[0], `${hash('b')}  Math World/Präsentation "1".pdf`);
  assert.deepEqual(parseManifest(text), entries);
});

test('progress manifests keep old hashes for files not yet uploaded, so a rerun uploads them again', () => {
  const [old, now] = ['a'.repeat(64), 'b'.repeat(64)];
  const previous = new Map([['kept', old], ['zchanged', old], ['gone', old]]);
  const current = new Map([['kept', old], ...Array.from({length: 600}, (_, i) => [`n${String(i).padStart(3, '0')}`, now]),
    ['zchanged', now]]);
  const {uploads} = plan(current, previous);
  const batches = progressManifests(current, previous, uploads);
  assert.deepEqual(batches.map(batch => batch.files.length), [500, 101]);
  const first = parseManifest(batches[0].manifest);
  assert.equal(first.get('n000'), now);
  assert.ok(!first.has('n599'));  // not uploaded yet: a rerun uploads it
  assert.equal(first.get('zchanged'), old);  // not uploaded yet: a rerun still sees it as changed
  assert.equal(first.get('gone'), old);  // deleted only after all uploads
  const last = parseManifest(batches.at(-1).manifest);
  assert.equal(last.get('zchanged'), now);
  assert.equal(last.size, 603);
});

test('stages the webspace in the repository layout without history, server builds or Pages extras', () => {
  const root = mkdtempSync(join(tmpdir(), 'stage-webspace-'));
  try {
    const repo = join(root, 'repo'), out = join(root, 'out');
    const put = (file, content = 'x') => { mkdirSync(join(repo, file, '..'), {recursive: true}); writeFileSync(join(repo, file), content); };
    for (const file of ['homepage/index.html', 'homepage/workbooks/index.html', 'homepage/.env', 'resources/languageMaps/map-de.json',
      'resources/programs/app/node_modules/lib.js', 'resources/programs/app/.git/HEAD', 'favicon.ico',
      'artifacts/newest/client.js', 'artifacts/newest/worker.js', 'artifacts/newest/server.jar',
      'artifacts/stable/client.js', 'artifacts/stable/server.jar', 'artifacts/history/client/2026-client.js']) put(file);
    put('client.env', 'EDUSQUIRREL_BACKEND_MODULE_WORKERS=1\n');
    const warnings = stageWebspace(repo, out, join(repo, 'client.env'));
    assert.deepEqual(listFiles(out).sort(), ['artifacts/newest/client.js', 'artifacts/newest/worker.js',
      'artifacts/stable/client.js', 'deployment/client/.env', 'favicon.ico', 'homepage/index.html',
      'homepage/workbooks/index.html', 'resources/languageMaps/map-de.json']);
    assert.deepEqual(warnings, ['artifacts/stable/worker.js is missing and is not uploaded']);
    rmSync(join(repo, 'artifacts/newest/worker.js'));
    assert.throws(() => stageWebspace(repo, out, join(repo, 'client.env')), /Missing build output: artifacts\/newest\/worker.js/);
  } finally {
    rmSync(root, {recursive: true, force: true});
  }
});
