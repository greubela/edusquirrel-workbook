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
const packages = JSON.parse(await readFile(path.join(root, 'package.json'), 'utf8')).devDependencies;
// Resolve every CDN import (including the gutter module) to the same pinned local package.
await build({entryPoints: [path.join(root, 'homepage/js/CodeMirrorLoader.js')],
  outfile: bundle, bundle: true, format: 'esm', platform: 'browser',
  plugins: [{name: 'offline-codemirror', setup(build) {
    build.onResolve({filter: /^https:\/\/esm\.sh\//}, async args => {
      const match = args.path.match(/^https:\/\/esm\.sh\/(@[^?]+)@([^?]+)(?:\?.*)?$/);
      if (!match || packages[match[1]] !== match[2]) throw new Error('Unpinned CodeMirror module: ' + args.path);
      return build.resolve(match[1], {resolveDir: root, kind: args.kind});
    });
  }}]});
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
  // The experimental spelling works on both CI's Node 22 and local Node 24.
  const result = spawnSync(process.execPath, ['--test', '--experimental-test-isolation=none', file], {
    cwd: root, stdio: 'inherit', timeout: 300_000,
    env: {...process.env, CODEMIRROR_TEST_BUNDLE: bundle,
      CHROMIUM_PATH: process.env.CHROMIUM_PATH || chromium.executablePath()}
  });
  if (result.error) console.error(result.error);
  if (result.status !== 0) process.exitCode = 1;
}
