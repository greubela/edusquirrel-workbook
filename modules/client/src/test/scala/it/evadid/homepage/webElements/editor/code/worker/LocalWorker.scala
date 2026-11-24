package it.evadid.homepage.webElements.editor.code.worker

import it.evadid.distribution.clients.{ExecutionClient, WebWorkerExecutionClient}
import it.evadid.distribution.formats.WorkerExecutionProtocol
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import org.scalajs.dom
import upickle.default.*

import scala.collection.mutable.ListBuffer
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** A stand-in for worker.js in specs: requests are answered by local execution clients (e.g. the core analysis),
  * through the same message protocol and [[WebWorkerExecutionClient]] the page uses.
  */
final class LocalWorker(executor: ExecutionClient) {
  private val logger = Logger.withNameAndPrefixes(Some("local-worker"), PrintToStdLogger.printNothing)
  val received = ListBuffer.empty[String]
  var terminated = false
  private lazy val raw: js.Dynamic = js.Dynamic.literal(
    postMessage = ((message: String) => {
      received += received.size.toString
      val request = read[WorkerExecutionProtocol.Request](message)
      executor.executeResponse(logger, request.command).foreach { response =>
        if !terminated then raw.selectDynamic("onmessage").asInstanceOf[js.Function1[dom.MessageEvent, Unit]](
          js.Dynamic.literal(data = write(WorkerExecutionProtocol.Response(request.requestId, response))).asInstanceOf[dom.MessageEvent])
      }
    }): js.Function1[String, Unit],
    terminate = (() => { terminated = true }): js.Function0[Unit])
  lazy val client: WebWorkerExecutionClient = WebWorkerExecutionClient.fromWorker(raw.asInstanceOf[dom.Worker], timeoutMilliseconds = 5000)
}

object LocalWorker {
  /** A page analyzer whose worker runs the core [[it.evadid.vm.analysis.ProgramAnalysis]] locally. */
  def analyzer(): it.evadid.homepage.webElements.editor.code.program.ProgramAnalyzer = {
    val executor = ExecutionClient(Seq(it.evadid.distribution.commandTypes.WorkerCommands.programAnalysis
      .toLocalExecutionClient((state, _) => scala.concurrent.Future(it.evadid.vm.analysis.ProgramAnalysis.of(state)))))
    val workers = Factory(executor)
    it.evadid.homepage.webElements.editor.code.program.ProgramAnalyzer(SharedWorker(() => workers()))
  }

  /** Creates a fresh worker per call, as a superseded or failed request discards its worker. */
  final class Factory(executor: ExecutionClient) {
    val created = ListBuffer.empty[LocalWorker]
    def apply(): WebWorkerExecutionClient = {
      val worker = LocalWorker(executor)
      created += worker
      worker.client
    }
  }
}
