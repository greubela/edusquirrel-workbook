package it.evadid.facades

import com.raquo.airstream.state.Var
import it.evadid.facades.BrowserScripts.*
import it.evadid.util.JsDomSuite
import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class ExternalDependencySpec extends JsDomSuite {
  test("instances are created only after their dependency became available") {
    val loaded = Var(false)
    val created = Var(0)
    val dependency = new ExternalDependency[Int](() => Future { loaded.set(true) }, () => loaded.now(),
      () => { created.update(_ + 1); created.now() })
    assert(intercept[IllegalArgumentException](dependency.createLoadedInstance()).getMessage.contains("has not been preloaded"))
    dependency.createInstance().map { instance =>
      assertEquals(instance, 1)
      assertEquals(dependency.createLoadedInstance(), 2)
    }
  }

  test("synchronous loader failures become failed futures") {
    val dependency = new ExternalDependency[Int](() => throw IllegalStateException("no network"), () => false, () => 1)
    dependency.createInstance().failed.map(error => assertEquals(error.getMessage, "no network"))
  }

  test("Algebrite is loaded once from its pinned bundle and then evaluates expressions") {
    val bundle = "https://unpkg.com/algebrite@1.4.0/dist/algebrite.bundle-for-browser.js"
    restoringGlobals("Algebrite") {
      js.special.delete(js.Dynamic.global.globalThis, "Algebrite")
      assert(intercept[IllegalArgumentException](Algebrite.loaded).getMessage.contains("has not been preloaded"))
      BrowserScripts.using(src => Load(() => if src == bundle then js.Dynamic.global.globalThis.Algebrite =
        js.Dynamic.literal(run = ((expression: String) => s"simplified($expression)"): js.Function1[String, String]))) { scripts =>
        Algebrite.createInstance().flatMap { api =>
          assertEquals(api.run("x+x"), "simplified(x+x)")
          Algebrite.preload().map { _ =>
            assertEquals(scripts.scripts, Vector(bundle), "an available engine is not loaded again")
            assertEquals(Algebrite.loaded.run("1"), "simplified(1)")
          }
        }
      }
    }
  }

  test("JSXGraph boards are created and freed through the loaded library") {
    val bundle = "http://localhost/resources/programs/20261004JSXGraph/jsxgraphcore.js"
    val calls = js.Array[String]()
    val board = js.Dynamic.literal(name = "board")
    val library = js.Dynamic.literal(JSXGraph = js.Dynamic.literal(
      initBoard = ((container: dom.Element, config: js.Dynamic) => {
        calls.push(s"init ${container.id} ${config.axis}"); board
      }): js.Function2[dom.Element, js.Dynamic, js.Dynamic],
      freeBoard = ((value: js.Dynamic) => calls.push(s"free ${value.name}")): js.Function1[js.Dynamic, Unit]))
    restoringGlobals("JXG") {
      js.special.delete(js.Dynamic.global.globalThis, "JXG")
      assert(!JSXGraph.available)
      val container = dom.document.createElement("div")
      container.id = "plot"
      BrowserScripts.using(src => Load(() => if src == bundle then js.Dynamic.global.globalThis.JXG = library)) { scripts =>
        JSXGraph.board(container, js.Dynamic.literal(axis = true)).createInstance().flatMap { created =>
          assert(created.asInstanceOf[js.Any] eq board)
          JSXGraph.free(created)
          JSXGraph.preload().map { _ =>
            assertEquals(scripts.scripts, Vector(bundle))
            assertEquals(calls.toList, List("init plot true", "free board"))
          }
        }
      }
    }
  }
}
