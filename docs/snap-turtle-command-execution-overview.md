# Snap turtle-command execution: existing behaviour

This document records the repository behaviour inspected before adding command
extraction to the Snap block editor.

## State and block-to-Python path

* `SnapCodeEditor` owns a `Var[ProgrammingExerciseState]`; the durable state is
  Snap project XML. Before consumers read it, `flushPendingProjectChanges()`
  asks the retained IDE to publish its latest `getProjectXML()` value.
* `SnapProgramDerivation.fromState` is the single existing XML derivation path.
  It parses XML with `TurtleStitchToBeExpressionParser`, retains the script
  layout, builds a `BeProgram`, and renders Python through
  `SnapTurtlePythonBridge.printedPython`.
* `SnapTurtleCatalog` is the canonical mapping between Snap selectors, Python
  names and `TurtlePathBuilder.TurtleCommand` names. It includes aliases and
  input kinds for motion, pen, control and embroidery blocks.
* The Python popup already uses the derivation path for display and the inverse
  `SnapTurtlePythonBridge.applyPython` path when applying edited Python. Command
  extraction must reuse the former rather than introduce another XML parser or
  printer.

## Python execution and hooks

* `PyodideWorkerClient` talks to the integrated `pyodide-worker.js`. Its
  `addCallbacks` operation installs real JavaScript functions into a synthetic
  Python module, while `run` executes Python and returns the ordered runtime
  `CallbackOp` values. The worker functions use JavaScript rest parameters, so
  calls with any supported arity retain every argument.
* The older main-thread environment is not suitable for this feature: the
  application loads Pyodide as an ES module inside the worker, rather than as a
  main-thread `loadPyodide` global. Command extraction therefore reuses the
  worker client and registers all names from `SnapTurtleCatalog`, including
  aliases and embroidery commands.
* Pyodide loading and execution return `Future`s. Consequently a truthful
  editor API must return `Future[List[TurtleCommand[Double]]]`; a synchronous
  `List` would either block the browser or return before execution completes.

## Turtle command model

`TurtlePathBuilder.TurtleCommand[T]` is the existing model. It stores a command
name, numeric arguments and string arguments. Extraction should preserve call
order, map Python aliases to canonical turtle command names through
`SnapTurtleCatalog`, and retain non-numeric values as strings (including color
values and booleans) instead of silently dropping them.

## Lifecycle and failure behaviour

The editor retains its Snap/Morphic world across fullscreen opens. Extraction
must not create another world or mutate the project. It should flush pending
edits, derive the current Python snapshot, execute that snapshot with a fresh
Pyodide global namespace, and propagate parsing or Python execution failures in
the returned `Future`. A failed run must never return a partial command list as
if it were complete.

## Test seams needed

Browser Pyodide is unavailable in the Scala.js Node test environment. The
command-extraction orchestration therefore needs a small injected runner seam.
Unit tests can then verify Python forwarding, the actual worker callback report
decoding, alias/canonical-name conversion, argument conversion, ordering and
failure propagation without replacing the production worker transport.
