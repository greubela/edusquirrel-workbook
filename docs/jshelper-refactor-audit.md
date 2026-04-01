# Scala JS helper/refactor audit (web workers + type conversion)

Date: 2026-04-01

## Implemented

- Moved shared JS interop helpers (dynamic/object casts, object builder, required field and typed field readers) into `util.web.JsHelpers`.
- Updated worker clients to consume these helpers so the duplicated helper suggestion was removed from the pending audit list.
- Extracted shared worker request tracking (`id` allocation, pending callback storage, message completion/failure fan-out) into `util.web.WorkerRequestTracker` and applied it to both worker clients.
- Centralized TurtleStitch editor option-object construction into `TurtleStitchEditor.editorOptions(...)`.
- Reused shared generic helpers in `PyodideMainThreadEnvironment` for JS argument normalization and JS exception message extraction.
- Refactored `DownloadHelper.fetchUrl` to use `JsHelpers.promiseToFuture` instead of nested manual Promise wiring.

## Scope reviewed

- `src/main/scala/interactionPlugins/programmingExercise/pythonExercise/pyodide/PyodideWorkerClient.scala`
- `src/main/scala/interactionPlugins/programmingExercise/pythonExercise/pyodide/PyodideMainThreadEnvironment.scala`
- `src/main/scala/export/workers/TurtleStitchWorker.scala`
- `src/main/scala/util/web/JsHelpers.scala`
- `src/main/scala/interactionPlugins/turtleStitchPlugin/TurtleStitchEditor.scala`
- `src/main/scala/util/web/DownloadHelper.scala`

## Remaining extraction targets

No pending items from this audit remain.

## Why this matters

- Better separation of concerns: transport vs decoding vs domain mapping.
- Lower bug surface for protocol/error handling by consolidating id/pending/onerror logic.
- Easier testing of decoders independent from worker runtime.
- More consistent and safer dynamic access patterns in Scala.js interop.
