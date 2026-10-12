package it.evadid.homepage.control.singletons

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.WorkerCommands
import munit.FunSuite
import scala.scalajs.js

class HomepageWorkerDefaultsSpec extends FunSuite {
  test("defaults claim a preheated worker once and retain the same execution client") {
    val root = js.Dynamic.global.globalThis
    val previousWindow = root.selectDynamic("window")
    val window = if js.isUndefined(previousWindow) then js.Dynamic.literal() else previousWindow
    root.updateDynamic("window")(window)
    val previous = window.selectDynamic("EDUSQUIRREL_BACKEND_WORKERS")
    var terminated = false
    val fake = js.Dynamic.literal(
      postMessage = ((_: String) => ()): js.Function1[String,Unit],
      terminate = (() => terminated = true): js.Function0[Unit]
    )
    val workers = js.Array(fake)
    window.updateDynamic("EDUSQUIRREL_BACKEND_WORKERS")(workers)
    try {
      val defaults = HomepageDefaults(null)
      val client = defaults.workerBackendExecutor
      assert(client eq defaults.workerBackendExecutor)
      assertEquals(workers.length,0)
      assert(client.canExecuteCommand(ExecutionCommand(WorkerCommands.health.name,ujson.Obj())))
      client.close()
      assert(terminated)
    } finally {
      window.updateDynamic("EDUSQUIRREL_BACKEND_WORKERS")(previous)
      root.updateDynamic("window")(previousWindow)
    }
  }
}
