import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

const root = new URL('../../', import.meta.url);
test('Eva navigation depends on tab and module contracts, without concrete editor or runtime knowledge', async () => {
  const raw = await readFile(new URL('modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/EvaEditor/EvaEditor.scala', root), 'utf8');
  const source = raw.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, '');
  assert.doesNotMatch(source, /SnapCodeEditor|CodeMirror|JavaFunctionBasedEditor|Pyodide|TurtleCommand|TurtleProgramExecution|toSnapXml|toPython|toJava|toBeExpression|ProgrammingState(?:Snap|Python|Java)/);
  assert.doesNotMatch(source, /case\s+(?:SnapLanguage|Python|Java)\s*=>/);
});
