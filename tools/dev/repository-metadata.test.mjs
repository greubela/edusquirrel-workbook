import assert from 'node:assert/strict';
import {execFileSync, spawnSync} from 'node:child_process';
import path from 'node:path';
import test from 'node:test';

test('every tracked submodule has a path and source URL for checkout cleanup', () => {
  const cwd = path.resolve(import.meta.dirname, '../..');
  const git = (...args) => execFileSync('git', args, {cwd, encoding: 'utf8'}).trim();
  // Git rejects tracked gitlinks without a matching .gitmodules path.
  if (!git('submodule', 'status')) return;
  const paths = git('config', '--file', '.gitmodules', '--get-regexp', '^submodule\\..*\\.path$')
    .split('\n').map(line => line.split(/\s+(.+)/s).slice(0, 2));
  for (const [key, name] of paths) {
    const url = git('config', '--file', '.gitmodules', '--get', key.replace(/\.path$/, '.url'));
    assert(URL.canParse(url), `Missing source URL for ${name}`);
  }
});

test('workbook elements have only the canonical graph codec, never a derived one', () => {
  const cwd = path.resolve(import.meta.dirname, '../..');
  const pattern = 'extends (WorkbookDisplayElement|WorkbookImageElement|WorkbookInteractionElement\\[[^]]*\\]|WorkbookStructureElement\\[[^]]*\\])[^{]*derives';
  const found = spawnSync('git', ['grep', '-lE', pattern, '--', 'modules/*.scala'], {cwd, encoding: 'utf8'});
  assert.ok(found.status === 0 || found.status === 1, found.stderr);
  assert.deepEqual(found.stdout.trim().split('\n').filter(Boolean), []);
});

test('browser code resolves no named time zones, so no time zone database is bundled', () => {
  const cwd = path.resolve(import.meta.dirname, '../..');
  const found = spawnSync('git', ['grep', '-lE', 'ZoneId\\.(systemDefault|of)\\(|ZonedDateTime', '--',
    'modules/core/shared/src/main', 'modules/core/js/src/main', 'modules/client/src/main', 'modules/worker/src/main'], {cwd, encoding: 'utf8'});
  assert.ok(found.status === 0 || found.status === 1, found.stderr);
  assert.deepEqual(found.stdout.trim().split('\n').filter(Boolean), []);
});

test('points in time are Instants, never device-zone local date-times', () => {
  const cwd = path.resolve(import.meta.dirname, '../..');
  // git grep exits with 1 when nothing matches.
  const found = spawnSync('git', ['grep', '-lE', 'LocalDateTime\\.now\\(\\)', '--', 'modules/*.scala'], {cwd, encoding: 'utf8'});
  assert.ok(found.status === 0 || found.status === 1, found.stderr);
  assert.deepEqual(found.stdout.trim().split('\n').filter(Boolean), []);
});
