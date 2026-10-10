import {access, readFile, mkdir, readdir} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import {build} from 'esbuild';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
await access(path.join(root, 'artifacts/newest/client.js')).catch(() => {
  throw new Error('Build the current client first: sbt -batch buildClientDev');
});
const scratch = path.join(root, 'tmp/work/browser-tests');
await mkdir(scratch, {recursive: true});
const bundle = path.join(scratch, 'CodeMirrorLoader.bundle.js');
const source = await readFile(path.join(root, 'homepage/js/CodeMirrorLoader.js'), 'utf8');
const packages = JSON.parse(await readFile(path.join(root, 'package.json'), 'utf8')).devDependencies;
// Use the production loader with the exact CDN package versions, without network access in Chromium.
const offlineSource = source.replace(/https:\/\/esm\.sh\/(@[^"?]+)@([^"?]+)(?:\?[^" ]*)?/g, (_, name, version) => {
  if (packages[name] !== version) throw new Error(`Pin ${name}@${version} in package.json for the browser tests`);
  return name;
});
await build({stdin: {contents: offlineSource, resolveDir: root, sourcefile: 'CodeMirrorLoader.js'},
  outfile: bundle, bundle: true, format: 'esm', platform: 'browser'});
const files = [];
const selected = process.argv.slice(2);
for (const name of (await readdir(path.join(root, 'tools/dev'))).filter(name => name.endsWith('.test.mjs')).sort()) {
  const source = await readFile(path.join(root, 'tools/dev', name), 'utf8');
  if ((name.endsWith('.browser.test.mjs') || source.includes("from 'playwright'")) &&
      (selected.length === 0 || selected.some(pattern => name.includes(pattern)))) files.push(`tools/dev/${name}`);
}
if (files.length === 0) throw new Error('No browser tests matched the selection');
for (const file of files) {
  // Separate processes keep fixtures isolated and report failures immediately; run every selected file.
  const result = spawnSync(process.execPath, ['--test', '--test-isolation=none', file], {
    cwd: root, stdio: 'inherit', timeout: 300_000,
    env: {...process.env, CODEMIRROR_TEST_BUNDLE: bundle,
      CHROMIUM_PATH: process.env.CHROMIUM_PATH || chromium.executablePath()}
  });
  if (result.error) console.error(result.error);
  if (result.status !== 0) process.exitCode = 1;
}
