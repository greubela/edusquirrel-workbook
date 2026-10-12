// Offline behavior oracle: execute the retained original, never import it into the native client.
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
const root=path.resolve(import.meta.dirname,'../..');
const source=path.join(root,'resources/programs/20260907Datenkompression');
const c={window:{},document:{addEventListener(){}}};vm.createContext(c);
for(const file of ['lang/widgets_de.js','js/material/s4-scenarios.js'])vm.runInContext(fs.readFileSync(path.join(source,file),'utf8'),c);
Object.assign(c,c.window);
c.getWidgetLang=k=>c.window.WIDGETS_DE[k];
let code=fs.readFileSync(path.join(source,'js/widgets/filesystem-simulator.js'),'utf8');
code=code.replace('  mountAll();','  window.originalCompressionOps = {canApplyTool,applySingleOp,archiveBytes};');
vm.runInContext(code,c);c.setupFilesystemSimulator(c.window.WIDGETS_DE.filesystemSimulator);
const ops=c.window.originalCompressionOps;
const scenarios=c.window.buildS4Scenarios(c.window.WIDGETS_DE.s4Scenarios);
const normalize=f=>({name:f.name,bytes:f.bytes,kind:f.type,metadata:f.metadata||[],count:f.count||1,applied:Object.entries(f.applied||{}).filter(([,v])=>v).map(([k])=>k)});
const cases=[];
for(const scenario of scenarios)for(const original of scenario.files){
 const file={...original,applied:{}};
 const results=[];
 for(const tool of ['convert','lossless','lossy']){
  const next=ops.canApplyTool(file,tool)?ops.applySingleOp(file,tool):file;
  results.push([tool,normalize(next)]);
 }
 cases.push([normalize(file),results]);
 // Also verify sequential format changes, metadata handling, and repeated tools.
 for(const first of ['convert','lossless','lossy']){
  const changed=ops.canApplyTool(file,first)?ops.applySingleOp(file,first):file;
  cases.push([normalize(changed),['convert','lossless','lossy'].map(tool=>[tool,normalize(ops.canApplyTool(changed,tool)?ops.applySingleOp(changed,tool):changed)])]);
 }
}
const chunks=[];for(let i=0;i<cases.length;i+=20)chunks.push(JSON.stringify(cases.slice(i,i+20)).slice(1,-1));
const scala=`package it.evadid.workbook.model.compression\n\n/** Generated offline from the retained original by tools/dev/generate-compression-parity.mjs. */\nobject OriginalCompressionFixtures {\n  val json: String = List(\n${chunks.map(c=>'    '+JSON.stringify(c)).join(',\n')}\n  ).mkString("[", ",", "]")\n}\n`;
fs.writeFileSync(path.join(root,'modules/core/shared/src/test/scala/it/evadid/workbook/model/compression/OriginalCompressionFixtures.scala'),scala);
console.log(`${cases.length} original input states × 3 operations`);
