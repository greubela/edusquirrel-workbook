import {access, readFile, mkdir, readdir} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import {build} from 'esbuild';
import {chromium} from 'playwright';

const root = path.resolve(import.meta.dirname, '../..');
await access(path.join(root, 'artifacts/newest/client.js')).catch(() => {
  throw new Error('Build the current client first: sbt -batch buildClientDev');
});
// Catch runner sandbox restrictions before starting the long workbook suite.
const probe = await chromium.launch({
  executablePath: process.env.CHROMIUM_PATH || chromium.executablePath(), chromiumSandbox: true
});
try { await probe.newPage(); }
finally { await probe.close(); }
console.log('Chromium sandbox startup verified.');
const scratch = path.join(root, 'tmp/work/browser-tests');
await mkdir(scratch, {recursive: true});
const bundle = path.join(scratch, 'codemirror-native.mjs');
const packages = JSON.parse(await readFile(path.join(root, 'package.json'), 'utf8')).devDependencies;
const exports = {
  '@codemirror/state': 'Compartment EditorState MapMode StateEffect StateField Text Transaction',
  '@codemirror/view': 'EditorView Decoration keymap drawSelection highlightActiveLine lineNumbers highlightActiveLineGutter ViewPlugin GutterMarker gutter',
  '@codemirror/commands': 'defaultKeymap history historyKeymap indentLess indentMore invertedEffects',
  '@codemirror/language': 'bracketMatching foldGutter foldKeymap indentUnit indentOnInput syntaxHighlighting defaultHighlightStyle syntaxTree',
  '@codemirror/search': 'highlightSelectionMatches searchKeymap', '@codemirror/lang-python': 'python',
  '@codemirror/lang-sql': 'sql MySQL', '@codemirror/lang-cpp': 'cpp', '@codemirror/lang-java': 'java',
  '@codemirror/theme-one-dark': 'oneDark', '@replit/codemirror-indentation-markers': 'indentationMarkers'
};
await build({stdin: {contents: Object.entries(exports).map(([name, names]) =>
  `export {${names.split(' ').join(',')}} from '${name}';`).join('\n'), resolveDir: root},
  outfile: bundle, bundle: true, format: 'esm', platform: 'browser'});
const files = [];
const selected = process.argv.slice(2);
for (const name of (await readdir(path.join(root, 'tools/dev'))).filter(name => name.endsWith('.test.mjs')).sort()) {
  const source = await readFile(path.join(root, 'tools/dev', name), 'utf8');
  if ((name.endsWith('.browser.test.mjs') || source.includes("from 'playwright'")) &&
      (selected.length === 0 || selected.some(pattern => name.includes(pattern)))) files.push(`tools/dev/${name}`);
}
if (files.length === 0) throw new Error('No browser tests matched the selection');
const failures = [];
for (const file of files) {
  console.log(`\n[workbook] ${file}`);
  // Separate processes keep fixtures isolated and report failures immediately; run every selected file.
  // The experimental spelling works on both CI's Node 22 and local Node 24.
  const result = spawnSync(process.execPath, ['--test', '--experimental-test-isolation=none', file], {
    cwd: root, stdio: 'inherit', timeout: 300_000,
    env: {...process.env, CODEMIRROR_TEST_BUNDLE: bundle,
      CHROMIUM_PATH: process.env.CHROMIUM_PATH || chromium.executablePath()}
  });
  if (result.error) console.error(result.error);
  if (result.error || result.status !== 0) {
    const reason = result.error?.code || result.signal || `exit ${result.status}`;
    failures.push(`${file} (${reason})`);
  }
}
if (failures.length) {
  console.error(`\nWorkbook browser checks failed (${failures.length}/${files.length} files):\n${failures.join('\n')}`);
  process.exitCode = 1;
} else {
  console.log(`\nWorkbook browser checks passed (${files.length} file${files.length === 1 ? '' : 's'}).`);
}
