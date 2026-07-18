package todomove.`export`.workers.client

import todomove.`export`.traits.WorkerTraits.WorkerState
import todomove.`export`.traits.WorkerTraits.WorkerState.WORKER_READY
import com.raquo.airstream.state.StrictSignal
import com.raquo.laminar.api.L.Var
import org.scalajs.dom.html.Canvas
import todomove.`export`.workers.TurtleStitchWorker

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.Promise as JsPromise
import scala.scalajs.js.JSConverters.*

case class TurtleStitchWorkerClient(canvas: Canvas) {

  private val worker = TurtleStitchWorker()
  private val serverStateVar: Var[WorkerState] = Var(WorkerState.WORKER_STARTING)

  def serverStateSignal: StrictSignal[WorkerState] = serverStateVar.signal

  private val preheat: Future[Unit] =
    worker.init().toFuture.map { _ =>
      serverStateVar.set(WORKER_READY(true, true))
    }.recoverWith { case ex: Throwable =>
      serverStateVar.set(WorkerState.WORKER_TERMINATED)
      Future.failed(ex)
    }

  private def afterPreheat[A](op: => JsPromise[A]): Future[A] =
    preheat.flatMap(_ => op.toFuture)

  def snapshotGreenFlagProgramsPngDataUrl(xml_content: String, language: String = "en"): Future[String] =
    afterPreheat(worker.calcProgramPng(xml_content, language))

  def getGreenFlagAsLispCode(xml_content: String, language: String): Future[String] =
    afterPreheat(worker.getGreenFlagAsLispCode(xml_content, language))

}
