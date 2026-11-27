import assert from 'node:assert/strict';
import {mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import test from 'node:test';
import {parseManifest, plan} from '../../deployment/client/sync-plan.mjs';

test('uploads changed and new files and deletes only previously deployed ones', () => {
  const previous = new Map([['same', '1'], ['changed', '2'], ['gone', '3']]);
  const current = new Map([['same', '1'], ['changed', 'X'], ['new', '4'], ['resources/pdf/a', '5'], ['resourcesX', '6']]);
  assert.deepEqual(plan(current, previous, ['resources']), {uploads: ['changed', 'new', 'resourcesX'], deletes: ['gone']});
  // Trust only covers files without a previous hash; recorded files are compared normally.
  assert.deepEqual(plan(new Map([['resources/a', 'new']]), new Map([['resources/a', 'old']]), ['resources']).uploads, ['resources/a']);
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
    assert.deepEqual(readdirSync(out).sort(), ['delete.curl', 'manifest.sha256', 'progress-001.sha256', 'summary.json', 'upload-001.curl']);
    const upload = readFileSync(join(out, 'upload-001.curl'), 'utf8');
    assert.match(upload, /url = "ftp:\/\/host\/Eva\/edu\/Math%20World\/Pr%C3%A4sentation%20%221%22\.pdf"/);
    assert.match(upload, /upload-file = ".*Präsentation \\"1\\"\.pdf"/);
    const manifest = parseManifest(readFileSync(join(out, 'manifest.sha256'), 'utf8'));
    assert.deepEqual([...manifest.keys()], ['Math World/Präsentation "1".pdf', 'index.html']);
    assert.equal(readFileSync(join(out, 'progress-001.sha256'), 'utf8'), readFileSync(join(out, 'manifest.sha256'), 'utf8'));
  } finally {
    rmSync(root, {recursive: true, force: true});
  }
});
