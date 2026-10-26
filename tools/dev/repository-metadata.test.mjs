import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
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
