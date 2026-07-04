/*
 * Loader for the Snap! JavaScript files checked into this directory.
 *
 * The facade use case only needs the constructors from morphic.js, symbols.js
 * and blocks.js. The rest of Snap! is useful for a full IDE/runtime but is not
 * required before Scala.js code starts constructing block morphs, so optional
 * files can be loaded in the background after the required set is ready.
 */

export const requiredSnapScriptFiles = Object.freeze([
  'src/morphic.js',
  'src/symbols.js',
  'src/blocks.js'
]);

export const optionalSnapScriptFiles = Object.freeze([
  'src/threads.js',
  'src/objects.js',
  'src/scenes.js',
  'src/widgets.js',
  'src/byob.js',
  'src/tables.js',
  'src/xml.js',
  'src/store.js',
  'src/locale.js',
  'src/cloud.js',
  'src/gui.js',
  'src/paint.js',
  'src/lists.js',
  'src/sketch.js',
  'src/video.js',
  'src/maps.js',
  'src/extensions.js',
  'src/api.js',
  'src/ypr.js',
  'src/embroider.js',
  'src/santa.js'
]);

export const snapScriptFiles = Object.freeze([
  ...requiredSnapScriptFiles,
  ...optionalSnapScriptFiles
]);

const loadedScriptUrls = new Set();
const pendingScriptLoads = new Map();

function normalizeBaseUrl(baseUrl) {
  const base = baseUrl || new URL('.', import.meta.url).href;
  return base.endsWith('/') ? base : `${base}/`;
}

export function snapScriptUrls(baseUrl = new URL('.', import.meta.url).href, files = snapScriptFiles) {
  const base = normalizeBaseUrl(baseUrl);
  return files.map((file) => new URL(file, base).href);
}

function ensureDocument(documentRef) {
  if (!documentRef || !documentRef.head) {
    throw new Error('Snap! loading requires a browser-like document with a <head>.');
  }
}

function loadScript(url, documentRef) {
  if (loadedScriptUrls.has(url)) return Promise.resolve(url);
  if (pendingScriptLoads.has(url)) return pendingScriptLoads.get(url);

  const loadPromise = new Promise((resolve, reject) => {
    const script = documentRef.createElement('script');
    script.src = url;
    script.async = false;
    script.onload = () => {
      loadedScriptUrls.add(url);
      pendingScriptLoads.delete(url);
      resolve(url);
    };
    script.onerror = () => {
      pendingScriptLoads.delete(url);
      reject(new Error(`Unable to load Snap! script: ${url}`));
    };
    documentRef.head.appendChild(script);
  });

  pendingScriptLoads.set(url, loadPromise);
  return loadPromise;
}

export async function loadSnapScripts(files, baseUrl = new URL('.', import.meta.url).href, documentRef = globalThis.document) {
  ensureDocument(documentRef);
  for (const url of snapScriptUrls(baseUrl, files)) {
    await loadScript(url, documentRef);
  }
  return globalThis;
}

export async function loadRequiredSnap(baseUrl = new URL('.', import.meta.url).href, documentRef = globalThis.document) {
  if (globalThis.CommandBlockMorph && globalThis.Morph) return globalThis;
  return loadSnapScripts(requiredSnapScriptFiles, baseUrl, documentRef);
}

export async function loadOptionalSnap(baseUrl = new URL('.', import.meta.url).href, documentRef = globalThis.document) {
  await loadRequiredSnap(baseUrl, documentRef);
  return loadSnapScripts(optionalSnapScriptFiles, baseUrl, documentRef);
}

export async function loadFullSnap(baseUrl = new URL('.', import.meta.url).href, documentRef = globalThis.document) {
  await loadRequiredSnap(baseUrl, documentRef);
  return loadOptionalSnap(baseUrl, documentRef);
}

globalThis.FullSnapLoader = Object.freeze({
  requiredSnapScriptFiles,
  optionalSnapScriptFiles,
  snapScriptFiles,
  snapScriptUrls,
  loadSnapScripts,
  loadRequiredSnap,
  loadOptionalSnap,
  loadFullSnap
});
