# Execution clients and backend selection

`ExecutionClient` is the common command interface for local, HTTP server and
browser-worker execution. Shared input/output types and `ExecutionCommandFactory`
live in `modules/core/shared`; handlers live where their dependencies are available.
The [generic worker backend](worker-execution-backend.md) is implemented, including
request correlation, typed decoding, pooling and failure cleanup.

## Choose an executor explicitly

| Executor | Where code runs | Suitable work |
| --- | --- | --- |
| `LocalExecutionClient` | Calling process/event loop | Small shared calculations; handlers already running on the JVM server |
| `RemoteExecutionClient` via `fullInfo.defaults.backendExecutor` | HTTP/JVM backend | Database, accounts and other server-only services |
| `WebWorkerExecutionClient` via `fullInfo.defaults.workerExecuter` | Dedicated browser worker | Registered DOM-independent CPU jobs without occupying the GUI event loop |
| `ExecutionClientPool` | Delegates to its member clients | Combining supported command handlers or executors |

A Scala.js `Future` on the main execution context is asynchronous scheduling, not
another CPU thread. Large parsing/calculation work can still block the GUI if run
locally. A worker has a separate event loop and cannot access the document, Laminar
or client UI services. A JVM backend can access only the services installed there.
Choosing an executor does not automatically make an arbitrary command supported.

`backendExecutor` captures the current signed token when the client is created.
Use `backendExecutorWithCredentials(token)` to bind a specific session; outstanding
requests keep that token after a user switch. Credentials are not transferred to
the generic worker. Do not
substitute worker/local execution for commands requiring server authorization.

## Define a typed command

This example is complete for local execution. Put its request/response and factory
in shared core when browser, worker or server consumers need them:

```scala
import it.evadid.distribution.command.ExecutionCommandFactory
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import upickle.default.ReadWriter

case class SquareRequest(value: Int) derives ReadWriter
case class SquareResponse(value: Long) derives ReadWriter
val square = ExecutionCommandFactory[SquareRequest, SquareResponse]("example.square")
val local = square.toLocalExecutionClient((request, _) =>
  Future.successful(SquareResponse(request.value.toLong * request.value.toLong)))
val result: Future[SquareResponse] =
  square.sendCommandTo(local, SquareRequest(7)).map(_.result)
```

`sendCommandTo` returns `Future[ExecutionInfo.ExecutionSuccess[O]]`; command errors
fail that future. The lower-level `handleCommand[O]` returns `ExecutionInfo[O]`,
which can be `ExecutionSuccess` or `ExecutionFailure`. The wire response is
`ExecutionClientResponse`, with serialized result/exception, timestamps and logs.
Keep request and response codecs available on both sides of the transport.

Register transport factories in `ExecutionCommandFactory.knownCommands`. For HTTP
execution, register the handler in the appropriate authenticated or
public server command pool. For generic worker execution, register it in
`WorkerCommandHandler` and advertise the name in `WorkerCommands.supportedCommands`.
Declaring a factory alone does not register a remote handler.

## Call a backend command

Server-only services use `fullInfo.defaults.backendExecutor`. Example: mail the
logged-in user a generated file and an uploaded one (`SendMailRequest` in
[`MailCommands`](../modules/core/shared/src/main/scala/it/evadid/distribution/commandTypes/MailCommands.scala)):

```scala
import it.evadid.core.datastructures.file.{CopyrightInfo, NamedDataFileDescription}
import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse, sendMailCommand}
import it.evadid.homepage.control.model.FullInfo
import org.scalajs.dom
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

def mailAnswersToMe(fullInfo: FullInfo, answersCsv: String, picked: dom.File): Future[SendMailResponse] =
  fullInfo.current.userInfo.map(_.user.mail) match {
    case None => Future.failed(IllegalStateException("Log in to receive the mail."))
    case Some(ownMail) =>
      val answers = NamedDataFileDescription("answers.csv", answersCsv.getBytes("UTF-8"),
        CopyrightInfo.unknownCopyrightInfo, Some("text/csv;charset=utf-8"))
      val request = fullInfo.contentControl.fileFactory.fromFile(picked).loadData()
        .map(upload => SendMailRequest(ownMail, "Meine Antworten", "Im Anhang meine Antworten.", List(answers, upload)))
      sendMailCommand.waitAndSendCommandTo(fullInfo.defaults.backendExecutor, request).map(_.result)
  }
```

Mail requests are delivered only to the requester's own address, and the command
body is limited to 16 MB. Each attachment travels as file name, declared media type,
copyright and Base64 bytes, never its source location. Requests fail with an
explicit message instead of falling back: nameless attachments (name `data:` URL
content explicitly), names with control characters and invalid declared media
types are rejected. Without a declared type, the type follows from the file name.

## Call an existing worker job

Inside a client component that is supplied a `FullInfo` context:

```scala
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.homepage.control.model.FullInfo
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

def workerHealth(fullInfo: FullInfo): Future[WorkerCommands.HealthResult] =
  WorkerCommands.health
    .sendCommandTo(fullInfo.defaults.workerExecuter, WorkerCommands.HealthRequest())
    .map(_.result)
```

See the [worker guide](worker-execution-backend.md) for supported jobs, Python
runtime semantics and termination. DOM-dependent rendering stays in the client;
choosing worker execution does not move an editor or grader there automatically.

## Build and verify

```sh
sbt buildClientDev buildWorkerDev
sbt 'coreJVM/testOnly *ExecutionInfoIntegrationTest'
sbt 'coreJS/testOnly *ExecutionInfoIntegrationTest *WorkerExecutionTest'
sbt 'worker/testOnly *BackendWorkerTest'
npm run test:workbooks -- worker-execution
```

Serve both generated bundles through the repository's normal preview/deployment
layout. The client reuses a Scala.js-startup-preheated worker or starts the generated
worker bundle on first use. No silent fallback to main-thread execution occurs
when a generic worker crashes or times out.
