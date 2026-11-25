import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import test from 'node:test';

const root = path.resolve(import.meta.dirname, '../..');

// See docs/testing-and-serialization-audit.md: new declarations need a codec or a documented exception.
test('every production case class, sealed trait and enum has a codec or a documented serialization exception', () => {
  for (const script of ['tools/dev/audit-scala-serialization.py', 'tools/dev/audit-scala-serialization.test.py']) {
    const result = spawnSync('python3', [script, ...(script.endsWith('.test.py') ? [] : ['--check'])], {cwd: root, encoding: 'utf8'});
    assert.equal(result.status, 0, `${script} failed:\n${result.stdout}${result.stderr}`);
  }
});
