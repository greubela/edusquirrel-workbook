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

test('editor composition keeps changing state in Vars and has no parallel edit channel', async () => {
  const directory = 'modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/';
  const files = [
    'EvaEditor/EvaEditor.scala', 'EvaEditor/EvaEditorTab.scala',
    'EvaEditor/extensions/turtle/JavaTurtleEditorExtension.scala',
    'EvaEditor/extensions/turtle/TurtleTaskEditorExtension.scala',
    'EvaEditor/extensions/turtle/TurtleExecutionPanel.scala',
    'EvaEditor/extensions/turtle/TurtleProgramExecution.scala',
    'EvaEditor/extensions/turtle/TurtleExecutionProvider.scala',
    'codemirror/CodeMirrorEditor.scala', 'codemirror/CodeMirrorFlowchartGutter.scala',
    '../../facades/ExternalDependencies.scala', '../../code/JavaFunctionBasedEditor.scala',
    'SnapEditor/SnapCodeEditor.scala', 'SnapEditor/SnapPreviewEditor.scala',
  ];
  for (const file of files) {
    const raw = await readFile(new URL(directory + file, root), 'utf8');
    // Native facade properties describe foreign state, not Scala application state.
    const source = raw.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, '')
      .replace(/\bvar\s+\w+[^=\n]*=\s*js\.native/g, '');
    assert.doesNotMatch(source, /\bvar\b|scala\.collection\.mutable/, file);
    if (file.endsWith('EvaEditorTab.scala')) {
      assert.doesNotMatch(source, /AsyncDataCache|synchronizedValue|latestConversions|underlyingVar|setWith|\bedits\b/);
    }
  }
});

test('Eva owns no editor cache, module context or fullscreen lifecycle', async () => {
  const source = await readFile(new URL('modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/EvaEditor/EvaEditor.scala', root), 'utf8');
  assert.doesNotMatch(source, /editorMap|synchronizedValue|FullscreenLifecycle|captureSource|\.bind\(|EvaEditorModule/);
  const mirror = await readFile(new URL('modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/codemirror/CodeMirrorEditor.scala', root), 'utf8');
  assert.doesNotMatch(mirror, /mountedHandle|mountedAnalysis|pendingDiagnostics|useTextareaFallback|currentDoc|handleFunctionInput/);
  const panel = await readFile(new URL('modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/EvaEditor/extensions/turtle/TurtleExecutionPanel.scala', root), 'utf8');
  assert.doesNotMatch(panel, /def (?:activate|deactivate)|prepare:|\bContext\b|captureSource/);
});
