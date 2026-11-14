# Snap turtle-command execution service

[SnapTurtleCommandExecution.scala](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/SnapEditor/execution/SnapTurtleCommandExecution.scala) provides asynchronous execution of a saved `ProgrammingStateSnapXml` snapshot. `SnapCodeEditor.getCurrentTurtleCommands()` is implemented: it calls `captureCurrentProject()`, flushes pending live-IDE edits, and delegates the resulting snapshot to the service. Callers that already hold a saved snapshot can use `commandsFor` directly. The service returns a future; consumers should show their own running/error state and preserve the captured program while awaiting it.

## Derivation and execution

`SnapTurtleCommandExecution.commandsFor` derives Python through `SnapProgramDerivation.fromState`. Unsupported XML fails before execution instead of running a partial program. `TurtleCommandRunner` is the injected execution boundary.

`PyodideTurtleCommandRunner` installs all Python names/aliases from `SnapTurtleCatalog` in a synthetic `turtle` module, and runs `from turtle import *` followed by the derived program. Globals reset for each run. Runtime callbacks, including repeated loop/function calls, determine the command sequence.

Callback decoding preserves order, ignores other modules, checks finite numbers, and resolves text aliases through `TurtleCommand.fromCall` / `fromText`. Drawing operations become typed cases such as `TurtleCommand.Forward(distance)` and `Goto(x, y)` with `BeEntityName` names. `args` and `stringArgs` are derived views for observation/interchange, not parallel stored command state. Embroidery calls retain their currently uninterpreted numeric/text payload alongside a typed stitch mode. Unknown turtle callbacks, unsupported values and non-finite numbers fail explicitly. Derivation and execution failures propagate through the returned `Future[List[TurtleCommand[Double]]]`; callers must not treat a failed run as a complete empty or partial result.

For authored programs, construct typed operations directly:

```scala
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand

val commands: List[TurtleCommand[Double]] = List(
  TurtleCommand.Forward(40),
  TurtleCommand.Right(90),
  TurtleCommand.PenUp()
)
```

`TurtlePathBuilder.handleCommand` and geometric tracing dispatch on these cases.
Text decoding belongs at language/runtime boundaries. Unsupported or malformed
calls remain explicit `Unsupported` values; grading validates them separately.
The sealed hierarchy and individual cases provide default uPickle codecs with
named fields. Saved tuple commands from the former model are not supported.

## Worker lifecycle

The default runner uses the [Scala.js worker command backend](worker-execution-backend.md)
with a dedicated worker and lazy Pyodide initialization. Startup and execution
have deadlines; cancellation or timeout discards the worker so late results
cannot affect a newer run. The consuming execution service owns the worker lifetime.

The service does not mount an IDE or change saved XML. Synchronous analysis on `ProgrammingStateBeExpression` is a separate operation: `turtleCommandExecutionInfo` exposes source call sites, executed commands and completion status. See [turtle program analysis](programming-conversions.md#turtle-source-occurrences-and-execution) and [Eva programming state](eva-editor-programming-state.md).

## Verification

```sh
sbt 'client/testOnly *SnapTurtleCommandExecutionSpec'
sbt 'coreJVM/testOnly *SnapTurtleCatalogSpec *SnapTurtlePythonBridgeSpec'
sbt 'coreJS/testOnly *SnapTurtleCatalogSpec *SnapTurtlePythonBridgeSpec'
```

The client suite injects a runner/callback executor to check Python forwarding, namespace reset, decoding, ordering and failure propagation. It runs under Node without downloading or starting browser Pyodide. These injected tests do not prove a live Pyodide download/execution. Verify the actual editor Run/Stop/close path separately in a browser. See [execution clients](execution-clients.md) for backend selection.
