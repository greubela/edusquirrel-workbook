package it.evadid.homepage.control.singletons

import com.raquo.airstream.state.Var
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.facades.Workers
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.concurrent.Promise
import scala.scalajs.js.timers.setTimeout

class HomepageWorkerDefaultsSpec extends EditorDomSuite {
  private val created = Var(0)
  private val terminated = Var(false)
  private class FakeWorker(url: String) extends js.Object {
    created.update(_ + 1)
    def postMessage(value: String): Unit = ()
    def terminate(): Unit = terminated.set(true)
  }
  test("defaults claim a Scala-preheated worker once and retain the same execution client") {
    val root = js.Dynamic.global.globalThis
    val previousWorker = root.selectDynamic("Worker")
    val window = org.scalajs.dom.window.asInstanceOf[js.Dynamic]
    val previousConfig = window.selectDynamic("EDUSQUIRREL_CLIENT_CONFIG")
    window.updateDynamic("EDUSQUIRREL_CLIENT_CONFIG")(js.Dynamic.literal(
      EDUSQUIRREL_BACKEND_MODULE_WORKERS = "1", EDUSQUIRREL_BACKEND_MODULE_WORKER_SCRIPT = "https://example.test/worker.js"))
    root.updateDynamic("Worker")(js.constructorOf[FakeWorker])
    Workers.preheat()
    val ready = Promise[Unit]()
    setTimeout(0) { ready.success(()) }
    ready.future.map { _ =>
      assertEquals(created.now(), 1)
      val defaults = HomepageDefaults(null)
      val client = defaults.workerBackendExecutor
      assert(client eq defaults.workerBackendExecutor)
      assertEquals(created.now(), 1)
      assert(client.canExecuteCommand(ExecutionCommand(WorkerCommands.health.name, ujson.Obj())))
      client.close()
      assert(terminated.now())
    }.andThen { case _ =>
      window.updateDynamic("EDUSQUIRREL_CLIENT_CONFIG")(previousConfig)
      root.updateDynamic("Worker")(previousWorker)
    }
  }
}
