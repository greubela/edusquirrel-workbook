package `export`.traits

import `export`.traits.WorkerTraits.*
import `export`.traits.WorkerTraits.WorkerState.WORKER_READY
import com.raquo.airstream.state.StrictSignal
import com.raquo.laminar.api.L.Var
import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import util.web.JsHelpers

import java.time
import java.time.LocalDateTime
import java.util.UUID
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.scalajs.js

/**
 * Base worker client facade that owns a dedicated worker instance.
 *
 * Responsibilities:
 * - boot worker runtime (via exported server entrypoint)
 * - optionally auto-initialize server pre-heating
 * - enqueue commands and resolve Futures from response frames
 * - keep client-side tracking for pending tasks
 *
 * Notes:
 * - all JS/Scala type conversion should stay in `JsHelpers`.
 */
object AbstractWorkerClient {


}

/**
 * Stateful client runtime for commands sent to a worker.
 *
 * Commands are sent asynchronously, while completion/failure is mapped back using request ids.
 */
abstract class AbstractWorkerClient(
                                     val exportedName: String,
                                     val paramsForInit: Map[String, String],
                                     val canvasForInit: Option[Canvas]
                                   ) {

  private val worker = {
    val worker = new dom.Worker(
      "./js/worker-bootstrap.js",
      js.Dynamic.literal(`type` = "module").asInstanceOf[dom.WorkerOptions]
    )
    worker.postMessage(
      js.Dynamic.literal(
        kind = "init-server",
        moduleUrl = "../../target/scala-3.3.3/workbookapp-fastopt/main.js",
        //moduleUrl = "./workbookapp-fastopt/main.js",
        exportedName = exportedName
      )
    )
    worker
  }


  private val pendingTasks = mutable.Map.empty[String, PendingTask]
  private val serverStateVar: Var[WorkerState] = Var(WorkerState.WORKER_STARTING)
  def serverStateSignal: StrictSignal[WorkerState] = serverStateVar.signal
  private val pendingOutboundMessages = mutable.Queue.empty[js.Object]

  initMessageHandling()

  private def postOrQueue(message: js.Object): Unit = synchronized {
    val sendNow = serverStateVar.now() match {
      case WorkerState.WORKER_READY(initRequested: Boolean, initSuccess: Boolean) => initSuccess
      case _ => false
    }
    if (sendNow) worker.postMessage(message)
    else pendingOutboundMessages.enqueue(message)
  }

  final def enqueue(
                     commandName: String,
                     commandParams: Map[String, String],
                     commandCanvas: Option[Canvas] = None
                   ): Future[ExecutionResult] = synchronized {
    val id = UUID.randomUUID().toString
    val promise = Promise[ExecutionResult]()

    val offscreenCanvas = commandCanvas.map(_.asInstanceOf[js.Dynamic].transferControlToOffscreen().asInstanceOf[dom.OffscreenCanvas])
    val cmd = WorkerCommand(commandName, commandParams, offscreenCanvas)

    pendingTasks.put(id, PendingTask(id, WorkerCommand(commandName, commandParams, None), promise, LocalDateTime.now()))
    postOrQueue(WorkerWire.request(id, cmd))

    promise.future
  }


  private def readServerTimestamp(raw: js.Dynamic, fieldName: String, fallback: LocalDateTime): LocalDateTime = {
    JsHelpers.parseOrElse[js.UndefOr[String]](raw.selectDynamic(fieldName), js.undefined)
      .toOption
      .flatMap(value => scala.util.Try(LocalDateTime.parse(value)).toOption)
      .getOrElse(fallback)
  }

  private def receiveServerReady(msg: dom.MessageEvent): Unit = synchronized {
    val offCanvas = canvasForInit.map(_.asInstanceOf[js.Dynamic].transferControlToOffscreen().asInstanceOf[dom.OffscreenCanvas])
    worker.postMessage(WorkerWire.init(paramsForInit, offCanvas))
    serverStateVar.set(WORKER_READY(true, false))
  }

  private def receiveServerInited(msg: js.Dynamic): Unit = synchronized {
    val ok = JsHelpers.parseOrElse[Boolean](msg.ok, false)
    if (ok) {
      serverStateVar.update {
        case WORKER_READY(initRequested: Boolean, _) => WORKER_READY(true, true)
        case oldVal => oldVal
      }
      while (pendingOutboundMessages.nonEmpty) {
        worker.postMessage(pendingOutboundMessages.dequeue())
      }
    } else {
      terminate(Some(JsHelpers.parseOrElse[String](msg.error, "Worker initialization failed")))
    }
  }

  private def receiveResponse(msg: js.Dynamic): Unit = synchronized {
    val id = msg.id.asInstanceOf[String]
    pendingTasks.remove(id).foreach { p =>
      if (JsHelpers.parseOrElse[Boolean](msg.ok, true)) {
        val data = JsHelpers.readStringMap(msg.data.asInstanceOf[js.Any]) //JsHelpers.stringMapHelper.fromJsToScala(msg.data).getOrElse(Map.empty)

        val timestampReceived = readServerTimestamp(msg, "timestampReceived", LocalDateTime.now())
        val timestampStarted = readServerTimestamp(msg, "timestampStarted", LocalDateTime.now())
        val timestampFinished = readServerTimestamp(msg, "timestampFinished", LocalDateTime.now())

        val result = ExecutionResult(
          history = CommandHistory(
            command = p.command,
            timestampRequested = p.timestampRequested,
            timestampReceived = timestampReceived,
            timestampStarted = timestampStarted,
            timestampFinished = timestampFinished
          ),
          data = data,
          error = None,
          stdOut = "",
          stdErr = ""
        )
        p.promise.trySuccess(result)
      } else {
        val error = JsHelpers.parseOrElse[String](msg.error, "Unknown worker error")
        p.promise.tryFailure(new RuntimeException(error))
      }
    }
  }

  private def initMessageHandling(): Unit = {
    worker.onmessage = { (e: dom.MessageEvent) =>
      val msg = e.data.asInstanceOf[js.Dynamic]
      val kind = msg.kind.asInstanceOf[js.UndefOr[String]].getOrElse("")

      if (kind == "server-ready") {
        receiveServerReady(e)
      } else if (kind == "init-result") {
        receiveServerInited(msg)
      } else if (kind == "server-failed") {
        terminate(Some(JsHelpers.parseOrElse[String](msg.error, "Server reported failure")))
      } else if (kind == "response") {
        receiveResponse(msg)
      }
    }

    worker.onerror = { (e: dom.ErrorEvent) =>
      terminate(Some(s"Worker error: ${Option(e.message).getOrElse("unknown error")}"))
    }
  }

  def terminate(msg: Option[String]): Unit = synchronized {
    serverStateVar.set(WorkerState.WORKER_TERMINATED)
    worker.terminate()
    val ex = new RuntimeException(msg.getOrElse("Worker is now terminated for unknown reasons."))
    pendingTasks.values.foreach(_.promise.tryFailure(ex))
    pendingTasks.clear()
    pendingOutboundMessages.clear()
  }

}
