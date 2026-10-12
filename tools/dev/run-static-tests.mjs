import {readdir, readFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';

const candidates = (await readdir(new URL('.', import.meta.url)))
  .filter(name => name.endsWith('.test.mjs') && !name.endsWith('.browser.test.mjs'))
  .sort();
const files = [];
for (const name of candidates) {
  const source = await readFile(new URL(name, import.meta.url), 'utf8');
  if (!source.includes("from 'playwright'")) files.push(`tools/dev/${name}`);
}
// Node 22 exposes isolation under its experimental name; Node 24 retains this alias.
const result = spawnSync(process.execPath, ['--test', '--experimental-test-isolation=none', ...files], {stdio: 'inherit'});
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
