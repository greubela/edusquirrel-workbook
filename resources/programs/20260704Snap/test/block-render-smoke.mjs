import {
  optionalSnapScriptFiles,
  requiredSnapScriptFiles,
  snapScriptFiles,
  snapScriptUrls
} from '../full-snap-loader.js';

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

assert(requiredSnapScriptFiles.length === 3, 'only the morphic/block facade prerequisites are required');
assert(requiredSnapScriptFiles[0] === 'src/morphic.js', 'morphic.js must be loaded first');
assert(requiredSnapScriptFiles[1] === 'src/symbols.js', 'symbols.js must be loaded before blocks.js');
assert(requiredSnapScriptFiles[2] === 'src/blocks.js', 'blocks.js is the last required facade script');
assert(optionalSnapScriptFiles.includes('src/gui.js'), 'the full Snap! loader can still include GUI classes');
assert(snapScriptFiles.length === requiredSnapScriptFiles.length + optionalSnapScriptFiles.length, 'full list combines required and optional scripts');

const requiredUrls = snapScriptUrls('https://example.invalid/snap/', requiredSnapScriptFiles);
assert(requiredUrls[0] === 'https://example.invalid/snap/src/morphic.js', 'required URLs are resolved relative to the Snap! root');
assert(requiredUrls.at(-1).endsWith('/src/blocks.js'), 'required URL order stops once block facades are ready');

const fullUrls = snapScriptUrls('https://example.invalid/snap/');
assert(fullUrls.at(-1).endsWith('/src/santa.js'), 'full script URLs are preserved in order');

console.log(`Snap! loader smoke test covered ${requiredUrls.length} required scripts and ${optionalSnapScriptFiles.length} optional scripts.`);
