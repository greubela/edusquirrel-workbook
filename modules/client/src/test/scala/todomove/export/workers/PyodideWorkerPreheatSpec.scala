package todomove.`export`.workers

import munit.FunSuite
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class PyodideWorkerPreheatSpec extends FunSuite {
  test("warmup initializes once and transfers an isolated worker to its first consumer") {
    val globals = js.Dynamic.global.globalThis
    val previous = js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, "Worker")
    globals.updateDynamic("__pyodideTestWorkers")(new js.Array[js.Dynamic]())
    val constructor = js.Dynamic.global.eval("""(class {
      constructor(url) { this.url = url; this.requests = []; globalThis.__pyodideTestWorkers.push(this); }
      postMessage(value) { this.requests.push(value); }
      terminate() { this.terminated = true; }
    })""")
    globals.updateDynamic("Worker")(constructor)
    val workers = globals.selectDynamic("__pyodideTestWorkers").asInstanceOf[js.Array[js.Dynamic]]
    def reply(worker: js.Dynamic): Unit = {
      val request = worker.requests.asInstanceOf[js.Array[js.Dynamic]](0)
      assertEquals(request.kind.asInstanceOf[String], "init")
      worker.onmessage(js.Dynamic.literal(data = js.Dynamic.literal(id = request.id, ok = true, payload = js.Dynamic.literal())))
    }
    try {
      PyodideWorkerClient.preheat()
      PyodideWorkerClient.preheat()
      assertEquals(workers.length, 1)
      reply(workers(0))
      val first = PyodideWorkerClient.acquire()
      assertEquals(workers.length, 1, "execution reuses the initialized worker")
      val second = PyodideWorkerClient.acquire()
      assertEquals(workers.length, 2, "separate sessions must not share mutable Python globals")
      reply(workers(1))
      Future.sequence(List(first.ready, second.ready)).map { _ =>
        first.terminate()
        second.terminate()
        assert(workers.forall(_.terminated.asInstanceOf[Boolean]))
      }
    } finally {
      if js.isUndefined(previous) then js.special.delete(globals, "Worker")
      else js.Dynamic.global.Object.defineProperty(globals, "Worker", previous)
      js.special.delete(globals, "__pyodideTestWorkers")
    }
  }
}
