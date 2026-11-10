import {gunzipSync} from 'node:zlib';

// Decode the production transport before inspecting command models in browser fixtures.
export function readCommandRequest(request) {
  const body = request.postDataBuffer();
  return JSON.parse((request.headers()['content-encoding'] === 'gzip' ? gunzipSync(body) : body).toString('utf8'));
}
