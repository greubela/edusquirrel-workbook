package `export`.traits

import `export`.traits.WorkerTraits.*
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

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
                                     using ec: ExecutionContext
                                   ) {

  protected val self: js.Dynamic = js.Dynamic.global.self

  protected var boundCanvas: Option[dom.OffscreenCanvas] = None

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
  def init(): Future[Boolean]

  final def start(): Unit = {
    self.onmessage = { (e: dom.MessageEvent) =>
      val msg = e.data.asInstanceOf[js.Dynamic]
      val kind = msg.kind.asInstanceOf[String]

      kind match {
        case "init" =>
          triggerInit().onComplete {
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

        case "request" =>
          val requestId = msg.id.asInstanceOf[String]
          val commandName = msg.name.asInstanceOf[String]
          val paramsRaw = msg.params.asInstanceOf[js.UndefOr[js.Any]].map(_.asInstanceOf[js.Any]).getOrElse(js.Dictionary.empty[String])
          val params = util.web.JsHelpers.readStringMap(paramsRaw)
          val timestampReceived = java.time.LocalDateTime.now()

          val command = WorkerCommand(
            name = commandName,
            params = params,
            timestampRequested = timestampReceived
          )

          val timestampStarted = java.time.LocalDateTime.now()
          executeWhenReady(command).onComplete {
            case scala.util.Success(data) =>
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
                  timestampStarted = timestampStarted.toString,
                  timestampFinished = timestampFinished.toString
                )
              )
          }

        case "bind-canvas" =>
          val name = msg.name.asInstanceOf[String]
          val canvas = msg.canvas.asInstanceOf[dom.OffscreenCanvas]
          val argsRaw = msg.args.asInstanceOf[js.UndefOr[js.Any]].map(_.asInstanceOf[js.Any]).getOrElse(js.Dictionary.empty[String])
          val args = util.web.JsHelpers.readStringMap(argsRaw)

          boundCanvas = Some(canvas)
          onCanvasBound(name, canvas, args)

        case other =>
          val now = java.time.LocalDateTime.now().toString
          self.postMessage(WorkerWire.error("unknown", s"Unknown worker message kind '$other'", now, now, now))
      }
    }
  }

  private def triggerInit(): Future[Boolean] = synchronized {
    if (!initStarted) {
      initStarted = true
      init().onComplete {
        case scala.util.Success(value) => isInited.trySuccess(value)
        case scala.util.Failure(ex) => isInited.tryFailure(ex)
      }
    }

    isInited.future
  }

  protected def executeWhenReady(workerCommand: WorkerCommand): Future[Map[String, String]] = {
    isInited.future.flatMap {
      case true => execute(workerCommand)
      case false => Future.failed(new IllegalStateException("Worker initialization was not successful."))
    }
  }

  protected def onCanvasBound(name: String, canvas: dom.OffscreenCanvas, args: Map[String, String]): Unit = ()

  /**
   * Scheduling hook.
   *
   * Default behavior: execute immediately.
   * Subclasses (e.g. synchronized server) can override to add queueing/serialization behavior.
   */
  protected def execute(workerCommand: WorkerCommand): Future[Map[String, String]] =
    handleTask(workerCommand)

  protected def handleTask(
                            workerCommand: WorkerCommand
                          ): Future[Map[String, String]]

}
