package `export`.traits

import `export`.traits.WorkerTraits.*
import org.scalajs.dom
import util.web.JsHelpers

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

  def apply(exportedName: String, autoInit: Boolean = true): AbstractWorkerClient = {
    val bootstrapSource =
      """
        |self.__workerServerStarted = false;
        |
        |self.onmessage = async (event) => {
        |  const msg = event.data || {};
        |  if (msg.kind !== 'init-server') return;
        |
        |  if (self.__workerServerStarted) {
        |    self.postMessage({ kind: 'server-ready' });
        |    return;
        |  }
        |
        |  try {
        |    const mod = await import(msg.moduleUrl);
        |    const starter = mod[msg.exportedName];
        |    if (typeof starter !== 'function') {
        |      throw new Error(`Export '${msg.exportedName}' not found in worker module.`);
        |    }
        |    starter();
        |    self.__workerServerStarted = true;
        |    self.postMessage({ kind: 'server-ready' });
        |  } catch (err) {
        |    const message = err && err.message ? err.message : String(err);
        |    self.postMessage({ kind: 'server-failed', error: message });
        |  }
        |};
        |""".stripMargin

    val blob = new dom.Blob(
      js.Array(bootstrapSource),
      dom.BlobPropertyBag(`type` = "text/javascript")
    )
    val workerUrl = dom.URL.createObjectURL(blob)

    val worker =
      new dom.Worker(
        workerUrl,
        js.Dynamic.literal(`type` = "module").asInstanceOf[dom.WorkerOptions]
      )

    dom.URL.revokeObjectURL(workerUrl)

    val client = new AbstractWorkerClient(worker, autoInit) {}
    worker.postMessage(
      js.Dynamic.literal(
        kind = "init-server",
        moduleUrl = "./workbookapp-fastopt/main.js",
        exportedName = exportedName
      )
    )

    if (autoInit) {
      client.init()
    }

    client
  }

}

/**
 * Stateful client runtime for commands sent to a worker.
 *
 * Commands are sent asynchronously, while completion/failure is mapped back using request ids.
 */
abstract class AbstractWorkerClient(
                                     protected val worker: dom.Worker,
                                     val autoInit: Boolean = true
                                   ) {

  private val pendingTasks = mutable.Map.empty[String, PendingTask]
  private val initPromise = Promise[Boolean]()
  private var initRequested = false

  initMessageHandling()

  final def init(): Future[Boolean] = synchronized {
    if (!initRequested) {
      initRequested = true
      worker.postMessage(WorkerWire.init)
    }

    initPromise.future
  }

  final def bindCanvas(
                        name: String,
                        canvas: dom.html.Canvas,
                        args: Map[String, String] = Map.empty
                      ): Unit = {
    val canvasWithOffscreen = canvas.asInstanceOf[js.Dynamic]
    val offscreen = canvasWithOffscreen.transferControlToOffscreen().asInstanceOf[dom.OffscreenCanvas]

    worker
      .asInstanceOf[js.Dynamic]
      .postMessage(
        WorkerWire.canvasBind(name, offscreen, args),
        js.Array(offscreen.asInstanceOf[js.Any])
      )
  }

  final def enqueue(
                     workerCommand: WorkerCommand
                   ): Future[ExecutionResult] = {

    val id = UUID.randomUUID().toString
    val promise = Promise[ExecutionResult]()
    pendingTasks.put(id, PendingTask(id, workerCommand, promise, LocalDateTime.now()))

    worker.postMessage(WorkerWire.request(id, workerCommand.name, workerCommand.params))

    promise.future
  }

  final def terminate(): Unit = {
    pendingTasks.values.foreach { p =>
      p.promise.tryFailure(
        new IllegalStateException("Worker terminated before reply")
      )
    }
    pendingTasks.clear()
    initPromise.tryFailure(new IllegalStateException("Worker terminated before init reply"))
    worker.terminate()
  }



  private def readServerTimestamp(raw: js.Dynamic, fieldName: String, fallback: LocalDateTime): LocalDateTime = {
    JsHelpers.parseOrElse[js.UndefOr[String]](raw.selectDynamic(fieldName), js.undefined)
      .toOption
      .flatMap(value => scala.util.Try(LocalDateTime.parse(value)).toOption)
      .getOrElse(fallback)
  }

  private def initMessageHandling(): Unit = {
    worker.onmessage = { (e: dom.MessageEvent) =>
      val msg = e.data.asInstanceOf[js.Dynamic]
      val kind = msg.kind.asInstanceOf[js.UndefOr[String]].getOrElse("")

      if (kind == "response") {
        val id = msg.id.asInstanceOf[String]
        pendingTasks.remove(id).foreach { p =>
          if (JsHelpers.parseOrElse[Boolean](msg.ok, true)) {
            val data = JsHelpers.stringMapHelper.fromJsToScala(msg.data).getOrElse(Map.empty)
            val timestampReceived = readServerTimestamp(msg, "timestampReceived", p.timestampEnqueued)
            val timestampStarted = readServerTimestamp(msg, "timestampStarted", timestampReceived)
            val timestampFinished = readServerTimestamp(msg, "timestampFinished", LocalDateTime.now())

            val result = ExecutionResult(
              history = CommandHistory(
                command = p.command,
                timestampRequested = p.command.timestampRequested,
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
      } else if (kind == "init-result") {
        val ok = JsHelpers.parseOrElse[Boolean](msg.ok, false)
        if (ok) {
          initPromise.trySuccess(true)
        } else {
          val error = JsHelpers.parseOrElse[String](msg.error, "Worker initialization failed")
          initPromise.tryFailure(new RuntimeException(error))
        }
      } else if (kind == "server-failed") {
        val ex = new RuntimeException(JsHelpers.parseOrElse[String](msg.error, "Worker initialization failed"))
        pendingTasks.values.foreach(_.promise.tryFailure(ex))
        pendingTasks.clear()
        initPromise.tryFailure(ex)
      }
    }

    worker.onerror = { (e: dom.ErrorEvent) =>
      val ex = new RuntimeException(
        s"Worker error: ${Option(e.message).getOrElse("unknown error")}"
      )
      pendingTasks.values.foreach { p =>
        p.promise.tryFailure(ex)
      }
      pendingTasks.clear()
      initPromise.tryFailure(ex)
    }
  }
}
