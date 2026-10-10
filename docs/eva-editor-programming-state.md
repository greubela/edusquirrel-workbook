# Eva Editor: state-centred programming architecture

`ProgrammingState` is the central access point for Eva Editor functionality. Consumers should ask
the current state for another representation and then invoke behavior owned by that representation:

```scala
// editor is an EvaEditor instance
editor.state.now().toBeExpressionState.deriveTurtleCommands
```

They should not reach into an editor implementation, invoke a parser, and pass the parser result to
an unrelated service. Keeping conversion and derived behavior on the state makes the same operation
available regardless of whether Snap, Python, or another editor tab is currently selected.

The shared representations are defined in [ProgrammingState.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/ProgrammingState.scala). Conversion is limited to the supported programming subset and can fail for other language features. Snap, Python, and `BeExpression` can convert through
the shared turtle-program subset, and `ProgrammingStateBeExpression` owns synchronous turtle-command
derivation. Java-like source is lowered into the shared VM AST by `JavaToBeExpressionParser`, so Java
states can use the same representation hops. Synchronous turtle-command derivation is distinct from the asynchronous [Pyodide execution service](snap-turtle-command-execution-overview.md). Browser lifecycle and worker operations remain in the client module.

Full Java classes use `ProgrammingStateJavaString.toJavaVmProgram`, which checks their structure and Java semantics before execution. They are not converted into an editable Python/Snap view. The checked Python export is an execution backend, not a reverse-conversion format.

`JavaTurtleEditorExtension` supplies Java execution; `TurtleTaskEditorExtension` supplies the drawing/task panel for every enabled language. Each Eva editor owns its Python/Snap worker. Stop, close and successful language switches cancel owned work. Snap edits are flushed before a run captures its source.

`TurtleDrawingPolicy` belongs to the task definition. `Segments` compares individual movements including pen-up routes, `Strokes` compares individual drawn lines, and `Coverage` compares geometric coverage with the existing 0.25 tolerance. Missing fields retain `Strokes` for shape-recreation tasks and `Coverage` for Java pilots. Learner-state serialization is unchanged. Unsupported drawing commands are rejected, not silently ignored.
