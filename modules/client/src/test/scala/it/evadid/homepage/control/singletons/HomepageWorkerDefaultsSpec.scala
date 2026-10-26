package it.evadid.homepage.control.singletons

import com.raquo.airstream.state.Var
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class HomepageWorkerDefaultsSpec extends EditorDomSuite {
  private val created = Var(0)
  private val terminated = Var(false)
  private class FakeWorker(url: String) extends js.Object {
    created.update(_ + 1)
    def postMessage(value: String): Unit = ()
    def terminate(): Unit = terminated.set(true)
  }
  test("defaults retain one execution client whether its worker is preheated or created on demand") {
    val root = js.Dynamic.global.globalThis
    val previousWorker = root.selectDynamic("Worker")
    val window = org.scalajs.dom.window.asInstanceOf[js.Dynamic]
    val previousConfig = window.selectDynamic("EDUSQUIRREL_CLIENT_CONFIG")
    window.updateDynamic("EDUSQUIRREL_CLIENT_CONFIG")(js.Dynamic.literal(
      EDUSQUIRREL_BACKEND_MODULE_WORKERS = "1", EDUSQUIRREL_BACKEND_MODULE_WORKER_SCRIPT = "https://example.test/worker.js"))
    root.updateDynamic("Worker")(js.constructorOf[FakeWorker])
    try {
      val defaults = HomepageDefaults(null)
      val client = defaults.workerBackendExecutor
      assert(client eq defaults.workerBackendExecutor)
      assert(created.now() <= 1, "reuse the shared preheated worker or create a single worker")
      assert(client.canExecuteCommand(ExecutionCommand(WorkerCommands.health.name, ujson.Obj())))
      client.close()
      assert(!client.canExecuteCommand(ExecutionCommand(WorkerCommands.health.name, ujson.Obj())))
      if created.now() == 1 then assert(terminated.now())
    } finally {
      window.updateDynamic("EDUSQUIRREL_CLIENT_CONFIG")(previousConfig)
      root.updateDynamic("Worker")(previousWorker)
    }
  }
}
