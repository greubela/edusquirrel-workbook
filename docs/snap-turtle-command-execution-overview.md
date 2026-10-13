# Snap turtle-command execution service

[SnapTurtleCommandExecution.scala](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/SnapEditor/execution/SnapTurtleCommandExecution.scala) provides asynchronous execution of a saved `ProgrammingStateSnapXml` snapshot. `SnapCodeEditor.getCurrentTurtleCommands()` is implemented: it calls `captureCurrentProject()`, flushes pending live-IDE edits, and delegates the resulting snapshot to the service. Callers that already hold a saved snapshot can use `commandsFor` directly. The service returns a future; consumers should show their own running/error state and preserve the captured program while awaiting it.

## Derivation and execution

`SnapTurtleCommandExecution.commandsFor` derives Python through `SnapProgramDerivation.fromState`. Unsupported XML fails before execution instead of running a partial program. `TurtleCommandRunner` is the injected execution boundary.

`PyodideTurtleCommandRunner` reuses the existing `PyodideWorkerClient`, installs all Python names/aliases from `SnapTurtleCatalog` in a synthetic `turtle` module, and runs `from turtle import *` followed by the derived program. Globals reset for each run. Runtime callbacks, including repeated loop/function calls, determine the command sequence.

Callback decoding preserves order, ignores other modules, maps aliases to canonical command names, and separates finite numbers into `args` and strings/booleans into `stringArgs`. Unknown turtle callbacks, unsupported values and non-finite numbers fail explicitly. Derivation and execution failures propagate through the returned `Future[List[TurtleCommand[Double]]]`; callers must not treat a failed run as a complete empty or partial result.

## Worker lifecycle

The worker URL resolver honors `globalThis.PYODIDE_WORKER_URL` and otherwise uses `../js/pyodide-worker.js`. The workbook pages load [config.js](../homepage/js/config.js), which sets that override. The worker owns Pyodide loading; this path does not rely on a main-thread `loadPyodide` global. It uses the existing Python worker, not the separate Scala.js backend worker.

The service does not mount an IDE or change saved XML. Current synchronous derivation on `ProgrammingStateBeExpression` is a separate operation; see [Eva programming state](eva-editor-programming-state.md).

## Verification

```sh
sbt 'client/testOnly *SnapTurtleCommandExecutionSpec'
sbt 'coreJVM/testOnly *SnapTurtleCatalogSpec *SnapTurtlePythonBridgeSpec'
sbt 'coreJS/testOnly *SnapTurtleCatalogSpec *SnapTurtlePythonBridgeSpec'
```

The client suite injects a runner/callback executor to check Python forwarding, namespace reset, decoding, ordering and failure propagation. It runs under Node without downloading or starting browser Pyodide. These injected tests do not prove a live Pyodide download/execution. Verify the actual editor Run/Stop/close path separately in a browser. The generic Scala.js worker backend is also implemented, but is a different transport/runtime; see [execution clients](execution-clients.md).
