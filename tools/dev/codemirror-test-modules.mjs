import fs from 'node:fs/promises';

// Redirect every native package to one ESM identity, preserving CodeMirror's
// cross-package class identities while serving the pinned npm packages offline.
export async function installCodeMirrorModules(page, {gate, fail = false, onRequest = () => {}} = {}) {
  await page.route('https://esm.sh/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname !== '/edusquirrel-codemirror.mjs') {
      if (url.pathname.startsWith('/@codemirror/state@')) {
        onRequest();
        if (gate) await gate;
        if (fail) return route.fulfill({status:503, body:'Unavailable', headers:{'access-control-allow-origin':'*'}});
      }
      return route.fulfill({contentType:'text/javascript', body:"export * from 'https://esm.sh/edusquirrel-codemirror.mjs';", headers:{'access-control-allow-origin':'*'}});
    }
    if (!process.env.CODEMIRROR_TEST_BUNDLE) throw Error('Run through npm run test:workbooks');
    return route.fulfill({contentType:'text/javascript', body:await fs.readFile(process.env.CODEMIRROR_TEST_BUNDLE),
      headers:{'access-control-allow-origin':'*'}});
  });
}
