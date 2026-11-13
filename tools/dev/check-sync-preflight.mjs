import {pathToFileURL} from 'node:url';

// Check the public proxy, not the backend's internal port. No account or data is sent.
export async function checkSyncPreflight(endpoint, origin) {
  const response = await fetch(endpoint, {
    method: 'OPTIONS', redirect: 'error', signal: AbortSignal.timeout(10000),
    headers: {
      Origin: origin,
      'Access-Control-Request-Method': 'POST',
      'Access-Control-Request-Headers': 'authorization,content-encoding,content-type'
    }
  });
  if (!response.ok) throw new Error(`Sync preflight returned HTTP ${response.status}.`);
  if (response.headers.get('access-control-allow-origin') !== origin ||
      response.headers.get('access-control-allow-credentials') !== 'true') {
    throw new Error('Proxy must allow the workbook origin and credentialed requests.');
  }
  const allowed = name => (response.headers.get(name) || '').toLowerCase().split(',').map(value => value.trim());
  if (!allowed('access-control-allow-methods').includes('post')) throw new Error('Proxy does not allow POST.');
  for (const header of ['authorization', 'content-encoding', 'content-type']) {
    if (!allowed('access-control-allow-headers').includes(header)) {
      throw new Error(`Proxy does not allow ${header}. Recreate the proxy with the current deployment/server/docker-compose.yml.`);
    }
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [, , endpoint, origin] = process.argv;
  if (!endpoint || !origin) throw new Error('Usage: node tools/dev/check-sync-preflight.mjs <executeCommand URL> <workbook origin>');
  await checkSyncPreflight(endpoint, origin);
  console.log('Sync preflight accepts authenticated gzip POSTs.');
}
