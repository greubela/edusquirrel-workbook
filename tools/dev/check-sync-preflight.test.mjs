import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {test} from 'node:test';
import {checkSyncPreflight} from './check-sync-preflight.mjs';

const origin = 'https://workbook.example.test';
const headers = {
  'Access-Control-Allow-Origin': origin,
  'Access-Control-Allow-Credentials': 'true',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization, Content-Encoding'
};

async function withProxy(responseHeaders, run, status = 204) {
  const received = [];
  const server = createServer((request, response) => {
    received.push(request);
    response.writeHead(status, responseHeaders).end();
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await run(`http://127.0.0.1:${server.address().port}/executeCommand`, received); }
  finally { await new Promise(resolve => server.close(resolve)); }
}

test('public proxy check requests the exact authenticated gzip headers without sending data', async () => {
  await withProxy(headers, async (endpoint, received) => {
    await checkSyncPreflight(endpoint, origin);
    assert.equal(received.length, 1);
    assert.equal(received[0].method, 'OPTIONS');
    assert.equal(received[0].headers.origin, origin);
    assert.equal(received[0].headers['access-control-request-headers'], 'authorization,content-encoding,content-type');
    assert.equal(received[0].headers.authorization, undefined);
  });
});

test('old deployed proxy headers reject gzip even though OPTIONS succeeds', async () => {
  await withProxy({...headers, 'Access-Control-Allow-Headers': 'Content-Type, Authorization'}, async endpoint => {
    await assert.rejects(checkSyncPreflight(endpoint, origin), /does not allow content-encoding/);
  });
});

test('failed preflights and missing credential permissions fail verification', async () => {
  await withProxy(headers, endpoint => assert.rejects(checkSyncPreflight(endpoint, origin), /HTTP 403/), 403);
  await withProxy({...headers, 'Access-Control-Allow-Origin': '*'}, endpoint =>
    assert.rejects(checkSyncPreflight(endpoint, origin), /credentialed requests/));
  await withProxy({...headers, 'Access-Control-Allow-Methods': 'GET'}, endpoint =>
    assert.rejects(checkSyncPreflight(endpoint, origin), /does not allow POST/));
});
