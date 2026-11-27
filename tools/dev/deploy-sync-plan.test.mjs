import assert from 'node:assert/strict';
import {mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import test from 'node:test';
import {listFiles, parseManifest, parseRemoteSizes, plan, sizeMarker, stageWebspace} from '../../deployment/client/sync-plan.mjs';

test('uploads changed and new files and deletes only previously deployed ones', () => {
  const previous = new Map([['same', '1'], ['changed', '2'], ['gone', '3']]);
  const current = new Map([['same', '1'], ['changed', 'X'], ['new', '4'], ['resources/pdf/a', '5'], ['resourcesX', '6']]);
  assert.deepEqual(plan(current, previous, ['resources']), {uploads: ['changed', 'new', 'resourcesX'], deletes: ['gone']});
  // Trust only covers files without a previous hash; recorded files are compared normally.
  assert.deepEqual(plan(new Map([['resources/a', 'new']]), new Map([['resources/a', 'old']]), ['resources']).uploads, ['resources/a']);
});

test('reads remote sizes and treats failed queries as missing', () => {
  const sizes = parseRemoteSizes(`Last-Modified: x\r\nContent-Length: 12\r\n${sizeMarker}\tftp://h/a\t0\n` +
    `curl: (78) The file does not exist\n${sizeMarker}\tftp://h/b\t78\n${sizeMarker}\tftp://h/c\t0\n`);
  assert.deepEqual([...sizes], [['ftp://h/a', 12]]);
});

test('writes URL-encoded upload batches, progress manifests and a final manifest', () => {
  const root = mkdtempSync(join(tmpdir(), 'sync-plan-'));
  try {
    const site = join(root, 'site'), out = join(root, 'out');
    mkdirSync(join(site, 'Math World'), {recursive: true});
    mkdirSync(out);
    writeFileSync(join(site, 'Math World', 'Präsentation "1".pdf'), 'pdf');
    writeFileSync(join(site, 'index.html'), 'page');
    writeFileSync(join(site, '.deploy-manifest.sha256'), 'ignored');
    const run = spawnSync(process.execPath, ['deployment/client/sync-plan.mjs', site, '-', out, 'ftp://host', '/Eva/edu/'], {encoding: 'utf8'});
    assert.equal(run.status, 0, run.stderr);
    assert.deepEqual(readdirSync(out).sort(), ['batch-001.tsv', 'delete.curl', 'manifest.sha256', 'progress-001.sha256',
      'summary.json', 'upload-001.curl', 'verify-001.curl']);
    const upload = readFileSync(join(out, 'upload-001.curl'), 'utf8');
    assert.match(upload, /url = "ftp:\/\/host\/Eva\/edu\/Math%20World\/Pr%C3%A4sentation%20%221%22\.pdf"/);
    assert.match(upload, /upload-file = ".*Präsentation \\"1\\"\.pdf"/);
    const manifest = parseManifest(readFileSync(join(out, 'manifest.sha256'), 'utf8'));
    assert.deepEqual([...manifest.keys()], ['Math World/Präsentation "1".pdf', 'index.html']);
    assert.equal(readFileSync(join(out, 'progress-001.sha256'), 'utf8'), readFileSync(join(out, 'manifest.sha256'), 'utf8'));

    // A truncated file is uploaded again; the intact one is not.
    const [pdf, page] = readFileSync(join(out, 'batch-001.tsv'), 'utf8').trim().split('\n').map(line => line.split('\t'));
    assert.equal(page[1], '4');
    writeFileSync(join(out, 'sizes.txt'), `Content-Length: 1\n${sizeMarker}\t${pdf[0]}\t0\nContent-Length: 4\n${sizeMarker}\t${page[0]}\t0\n`);
    const check = spawnSync(process.execPath, ['deployment/client/sync-plan.mjs', 'verify', join(out, 'batch-001.tsv'),
      join(out, 'sizes.txt'), join(out, 'retry.curl')], {encoding: 'utf8'});
    assert.equal(check.stdout.trim(), '1', check.stderr);
    assert.match(readFileSync(join(out, 'retry.curl'), 'utf8'), /Pr%C3%A4sentation/);
  } finally {
    rmSync(root, {recursive: true, force: true});
  }
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
