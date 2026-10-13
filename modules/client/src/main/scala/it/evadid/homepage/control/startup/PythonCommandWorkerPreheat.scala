package it.evadid.homepage.control.startup

import it.evadid.distribution.clients.WebWorkerExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Warm the interpreter used by Snap/Python execution, without owning an editor. */
object PythonCommandWorkerPreheat {
  private val slot = new WarmWorkerSlot[WebWorkerExecutionClient](
    () => WebWorkerExecutionClient(HtmlFullWorkbookApp.fullInfo.contentControl.fileFactory.relativeToArtifactsFolder("newest/worker.js").asUrlString),
    worker => WorkerCommands.runPython.sendCommandTo(worker, WorkerCommands.RunPythonRequest("")).map(_ => ()),
    _.close())

  def preheat(): Future[Unit] = slot.preheat()
  def acquire(): WebWorkerExecutionClient = slot.acquire()
}
