# Worker execution backend

`client.js` owns the GUI; `worker.js` runs `WebWorkerBackendServer` and dispatches
through `WorkerCommandHandler`. CPU work stays on the worker's event loop.
A main-thread `Future` does not provide parallelism, and simultaneous CPU jobs
require separate workers.

The shared `WorkerExecutionProtocol` correlates typed commands and responses by
request ID, preserving errors, timestamps and logs. `WebWorkerExecutionClient`
implements `ExecutionClient`; transport errors, crashes, timeouts and closure fail
pending requests. A timeout terminates the worker. Obtain a new client afterwards;
execution never silently falls back to the GUI thread.

`fullInfo.defaults.workerExecuter` claims a preheated worker or creates one
on demand. The HTTP backend remains available as `backendExecutor`. See
[startup loading](browser-startup-loading.md) for pool configuration and
[execution clients](execution-clients.md) for command definitions and backend selection.

```scala
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel
import scala.concurrent.{ExecutionContext, Future}

def parseBlocks(fullInfo: FullInfo, xml: String)
    (using ExecutionContext): Future[Seq[SnapScalaModel.Element]] =
  WorkerCommands.parseSnapXml.sendCommandTo(
    fullInfo.defaults.workerExecuter, WorkerCommands.ParseSnapXmlRequest(xml, "block")
  ).map(_.result)
```

## Python and supported jobs

Command factories live in `WorkerCommands`; registration belongs to
`WorkerCommandHandler`. Jobs include health, Snap XML parsing, Python and Java flowchart
analysis, static program analysis, QR calculation and Python execution. DOM-dependent
work stays on the client. Eva's console and static drawings use these jobs as described
in [program analysis and execution](program-analysis-and-execution.md).

Legacy feedback uses core JS’s `PythonExecutionClient` with its own interpreter lifetime. The Pyodide facade also supports direct main-thread
execution through `createInstance()`; its loader selects browser scripts or classic
worker imports for the current context. CPU-intensive work belongs in a worker.
Requests supply JSON
context and callback method names; results contain a JSON-compatible value,
stdout/stderr and recorded callback operations. Packages load before execution.
Python jobs queue per worker so globals, callbacks and output cannot mix.

```scala
def pythonExample(fullInfo: FullInfo)
    (using ExecutionContext): Future[WorkerCommands.RunPythonResult] =
  WorkerCommands.runPython.sendCommandTo(
    fullInfo.defaults.workerExecuter, WorkerCommands.RunPythonRequest("print('Hello')\n6 * 7")
  ).map(_.result) // value = 42, stdout = "Hello\n"
```

`resetGlobals` defaults to true; false retains variables between jobs. Imported
modules remain interpreter state, so resetting is not a fresh process or sandbox.
Errors preserve captured output and allow later jobs. Closing or timing out the
client terminates even an infinite Python loop. Turtle and feedback consumers use
this same protocol and replay returned callbacks.

## Verification

Module tests cover transport failures, correlation and typed dispatch. The
[worker browser test](../tools/dev/worker-execution.browser.test.mjs) exercises real
Pyodide and verifies GUI responsiveness during CPU work, using local pinned
packages. See the [test guide](core-package-unit-tests.md) for build and run commands.
