import assert from 'node:assert/strict';
import {spawn, spawnSync} from 'node:child_process';
import {createServer} from 'node:net';
import {resolve} from 'node:path';
import {setTimeout as delay} from 'node:timers/promises';

// Verify the deployable artifact itself, including its runtime-only dependencies.
const jar = resolve(process.argv[2] || 'artifacts/newest/server.jar');
const listed = spawnSync('java', ['-m', 'jdk.jartool/sun.tools.jar.Main', '--list', '--file', jar], {encoding: 'utf8', maxBuffer: 10 * 1024 * 1024});
assert.equal(listed.status, 0, listed.stderr || listed.error?.message);
const entries = listed.stdout.split('\n');
assert(entries.includes('it/evadid/server/BackendServer.class'), 'fat JAR contains the server');
assert(entries.includes('org/slf4j/simple/SimpleServiceProvider.class'), 'fat JAR contains its logging provider');
assert(entries.includes('META-INF/services/org.slf4j.spi.SLF4JServiceProvider'), 'SLF4J can discover the provider');
assert(!entries.some(name => /^(javafx|scalafx|com\/sun\/javafx)\//.test(name) || /\/(AppFxCanvas|EvaFxCanvas)[.$]/.test(name)),
  'desktop classes and JavaFX libraries must stay outside the server');

const reservation = createServer();
await new Promise((done, reject) => { reservation.once('error', reject); reservation.listen(0, '127.0.0.1', done); });
const port = reservation.address().port;
await new Promise(done => reservation.close(done));
const child = spawn('java', ['-jar', jar], {
  env: {...process.env, SERVER_PORT: String(port), SERVER_HOSTNAME: '127.0.0.1'}, stdio: ['ignore', 'pipe', 'pipe']
});
let output = '', startupError;
child.stdout.on('data', data => { output += data; });
child.stderr.on('data', data => { output += data; });
child.on('error', error => { startupError = error; });
try {
  const deadline = Date.now() + 30000;
  let health;
  while (Date.now() < deadline) {
    if (startupError) throw startupError;
    assert.equal(child.exitCode, null, output);
    try {
      const response = await fetch(`http://127.0.0.1:${port}/health`, {signal: AbortSignal.timeout(1000)});
      if (response.ok) { health = await response.json(); break; }
    } catch { /* The JVM may still be starting. */ }
    await delay(100);
  }
  assert.equal(health?.status, 'ok', output || 'Server did not become healthy');
  assert.equal(health.service, 'edusquirrel-server');
  assert(!/No SLF4J providers|NOP logger|Failed to load class.*StaticLoggerBinder/.test(output), output);
  console.log('Server fat JAR: standalone startup, health, logging and desktop isolation passed.');
} finally {
  if (child.pid && child.exitCode === null) {
    await new Promise(done => {
      child.once('close', done);
      const timer = setTimeout(() => child.kill('SIGKILL'), 5000);
      timer.unref();
      child.once('close', () => clearTimeout(timer));
      child.kill('SIGTERM');
    });
  }
}
