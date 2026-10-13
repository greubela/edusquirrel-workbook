import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';

const source = await fs.readFile(new URL('../../resources/programs/20260212TurtleStitch/fullturtle.js', import.meta.url), 'utf8');

test('the Snap adapter exposes readiness without loading scripts or creating an editor', async () => {
  const window = {};
  const fail = () => { throw new Error('runtime orchestration belongs to Scala.js'); };
  vm.runInNewContext(source, {window,document:{addEventListener:fail,createElement:fail},
    Promise,console:{log(){},warn(){},error(){}},setTimeout:fail});
  await window.TurtleStitchPoCReady;
  assert(window.TurtleStitchPoC._impl);
  assert.equal(typeof window.TurtleStitchPoC.createEditor,'function');
  assert.equal(window.TurtleStitchPoCError,null);
});

test('the loaded adapter retains the serializer compatibility patch', async () => {
  function SnapSerializer() {}
  let received;
  SnapSerializer.prototype.loadProjectModel = function(node) { received = node.attributes.app; };
  const window = {SnapSerializer};
  vm.runInNewContext(source, {window,Promise,console:{log(){},warn(){},error(){}}});
  const serializer = new SnapSerializer();
  serializer.app = 'TurtleStitch 1.0';
  serializer.loadProjectModel({attributes:{app:'Snap! 1.0'}});
  assert.equal(received,'TurtleStitch 1.0');
});
