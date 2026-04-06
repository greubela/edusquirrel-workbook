package `export`.traits

import `export`.traits.WorkerTraits.*
import org.scalajs.dom
import org.scalajs.dom.OffscreenCanvas
import util.web.JsHelpers

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

/**
 * Base server runtime that executes inside a dedicated WebWorker.
 *
 * Responsibilities:
 * - parse incoming worker messages
 * - enforce worker pre-heating (`init`) before commands are executed
 * - translate command results into worker response frames (`WorkerWire`)
 *
 * Notes:
 * - all JS/Scala type conversion should stay in `JsHelpers`.
 * - subclasses should only focus on task behavior (`handleTask`) and optional custom scheduling.
 */
abstract class AbstractWorkerServer(
                                     workerName: String,
                                     debug: Boolean = true
                                   ) {

  implicit protected val ec: ExecutionContext = ExecutionContext.global

  protected val self: js.Dynamic = js.Dynamic.global.self

  def logInfo(msg: String): Unit = if (debug) dom.console.log(s"[INFO] from Worker::$workerName (${java.time.LocalDateTime.now().toString}): $msg")

  /**
   * Completion signal for worker initialization.
   *
   * `true` means command processing can proceed, `false` means initialization completed but worker should
   * reject execution requests.
   */
  val isInited: Promise[Boolean] = Promise[Boolean]()

  private var initStarted = false

  /**
   * Pre-heats worker state before any command execution starts.
   */
  protected def init(params: Map[String, String], canvas: Option[OffscreenCanvas]): Future[Boolean]

  protected def handleTask(
                            workerCommand: WorkerCommand
                          ): Future[Map[String, String]]


  private def receivedInit(msg: js.Dynamic): Unit = {

    val paramsRaw = msg.params.asInstanceOf[js.UndefOr[js.Any]].map(_.asInstanceOf[js.Any]).getOrElse(js.Dictionary.empty[String])
    val params = util.web.JsHelpers.readStringMap(paramsRaw)
    val canvas: Option[OffscreenCanvas] = JsHelpers.parseOrEmpty[OffscreenCanvas](msg.canvas)

    triggerInit(params, canvas).onComplete {
      case scala.util.Success(value) =>
        self.postMessage(
          js.Dynamic.literal(
            kind = "init-result",
            ok = value
          )
        )
      case scala.util.Failure(ex) =>
        self.postMessage(
          js.Dynamic.literal(
            kind = "init-result",
            ok = false,
            error = Option(ex.getMessage).getOrElse("Unknown init error")
          )
        )
    }
  }

  private def receivedRequest(msg: js.Dynamic): Unit = {
    val requestId = msg.id.asInstanceOf[String]
    val commandName = msg.name.asInstanceOf[String]
    val paramsRaw = msg.params.asInstanceOf[js.UndefOr[js.Any]].map(_.asInstanceOf[js.Any]).getOrElse(js.Dictionary.empty[String])
    val params = util.web.JsHelpers.readStringMap(paramsRaw)

    val canvas: Option[OffscreenCanvas] = JsHelpers.parseOrEmpty[OffscreenCanvas](msg.canvas)
    val timestampReceived = java.time.LocalDateTime.now()

    val command = WorkerCommand(
      name = commandName,
      params = params,
      canvas = canvas
    )

    executeWhenReady(command).onComplete {
      case scala.util.Success((data, timestampStarted)) =>
        val timestampFinished = java.time.LocalDateTime.now()
        self.postMessage(
          WorkerWire.response(
            requestId,
            data,
            timestampReceived = timestampReceived.toString,
            timestampStarted = timestampStarted.toString,
            timestampFinished = timestampFinished.toString
          )
        )
      case scala.util.Failure(ex) =>
        val timestampFinished = java.time.LocalDateTime.now()
        self.postMessage(
          WorkerWire.error(
            requestId,
            Option(ex.getMessage).getOrElse("Unknown worker failure"),
            timestampReceived = timestampReceived.toString,
            timestampStarted = LocalDateTime.of(0, 0, 0, 0, 0, 0).toString,
            timestampFinished = timestampFinished.toString
          )
        )
    }
  }


  final def start(): Unit = {
    self.onmessage = { (e: dom.MessageEvent) =>
      val msg = e.data.asInstanceOf[js.Dynamic]
      val kind = msg.kind.asInstanceOf[String]
      logInfo(s"Received message: ${kind}")

      kind match {
        case "init" =>
          receivedInit(msg)
        case "request" => receivedRequest(msg)
        case other =>
          val now = java.time.LocalDateTime.now().toString
          self.postMessage(WorkerWire.error("unknown", s"Unknown worker message kind '$other'", now, now, now))
      }
    }
  }

  private def triggerInit(params: Map[String, String], canvas: Option[OffscreenCanvas]): Future[Boolean] = synchronized {
    if (!initStarted) {
      initStarted = true
      init(params, canvas).onComplete {
        case scala.util.Success(value) => isInited.trySuccess(value)
        case scala.util.Failure(ex) => isInited.tryFailure(ex)
      }
    }
    isInited.future
  }

  protected def executeWhenReady(workerCommand: WorkerCommand): Future[(Map[String, String], LocalDateTime)] = {
    isInited.future.flatMap {
      case true => {
        val started = LocalDateTime.now()
        execute(workerCommand).map(data => (data, started))
      }
      case false => Future.failed(new IllegalStateException("Worker initialization was not successful."))
    }
  }

  /**
   * Scheduling hook.
   *
   * Default behavior: execute immediately.
   * Subclasses (e.g. synchronized server) can override to add queueing/serialization behavior.
   */
  protected def execute(workerCommand: WorkerCommand): Future[Map[String, String]] =
    handleTask(workerCommand)


}
