# Shared feedback data models

Portable feedback data lives in `it.evadid.workbook.model.feedback` under
`modules/core/shared`. JVM server and Scala.js client/worker code can use the same
types and default uPickle codecs:

```scala
import it.evadid.workbook.model.feedback.{BlockFeedbackConfig, BlockFeedbackMeta, BlockFeedbackRequest}
import it.evadid.workbook.model.feedback.runtime.{PythonRunRequest, PythonRunResult}
```

The shared model includes requests/metadata, exercise configuration, feedback-owned
tests/fixtures, routing and feedback statuses, prepared expected/actual display
data, aggregate feedback/debug results, and Python runtime outcomes. The `runtime`
subpackage contains the Python execution request, test/fixture and result contracts;
it does not start Pyodide or a browser worker.

`FeedbackResult` is an abstract data interface. Concrete feedback records store
their timestamp explicitly and derive `ReadWriter`; transporting the open interface
would require an explicit registered codec. `BlockFeedbackResult` remains the alias
for `UltrichsNewCoolFeedback`. Existing field names, enum spellings and constructor
defaults are preserved. `BlockFeedbackConfig()` and `BlockFeedbackConfig.default`
retain their distinct behavior.

Client code still owns `PythonRuntimeService`, the feedback pipeline, exercise
registry/configuration provider, browser/network adapters and DOM presentation.
`BlockFeedbackTestResultFormatter` consumes the shared `ExpectedActual` record; its
former nested name forwards to that record for source compatibility.

`SharedFeedbackModelsSpec` runs on JVM and Scala.js. Its fixtures were captured
from the original client-owned JSON codecs before relocation. Tests verify legacy
decoding/re-encoding, MessagePack round trips, defaults, timestamps, Unicode,
runtime result contracts and source derivation:

```sh
sbt 'coreJVM/testOnly *SharedFeedbackModelsSpec'
sbt 'coreJS/testOnly *SharedFeedbackModelsSpec'
sbt 'client/test' 'server/test' 'worker/test'
```

Moving these contracts does not move the feedback execution service into the
generic worker backend or attach it automatically to interaction scaffolding.
Those integrations use the shared contracts as their data boundary.
