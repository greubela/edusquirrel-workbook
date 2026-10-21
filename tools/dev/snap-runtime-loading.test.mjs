import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';

const source=await fs.readFile(new URL('../../resources/programs/20260212TurtleStitch/fullturtle.js',import.meta.url),'utf8');
test('the Snap compatibility bridge publishes its API without scheduling downloads or allocating an editor',async()=>{
  const window={};
  vm.runInNewContext(source,{window,console,document:{
    createElement(){throw new Error('the bridge must not create scripts or editors');},
    addEventListener(){throw new Error('startup scheduling belongs to Scala.js');}},
    setTimeout(){throw new Error('no delayed JavaScript warmup');}});
  await window.TurtleStitchPoCReady;
  assert(window.TurtleStitchPoC._impl);
  await assert.rejects(window.TurtleStitchPoC.calcProgramPng(''),/not supported as a static/);
});
