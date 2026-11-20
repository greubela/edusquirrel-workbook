package it.evadid.facades

import it.evadid.util.JsDomSuite
import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class WorkersSpec extends JsDomSuite {
  private def created = FakeWorker.created.toList

  test("classic and module workers start with their script URL and worker type") {
    FakeWorker.installed {
      val url = "http://localhost/artifacts/newest/worker.js"
      Workers.classic(url).createInstance().zip(Workers.module("http://localhost/module.js").createInstance()).map { (classic, module) =>
        assertEquals(created.map(_.url), List(url, "http://localhost/module.js"))
        assert(js.isUndefined(created.head.options.get.selectDynamic("type")))
        assertEquals(created(1).options.get.selectDynamic("type").asInstanceOf[String], "module")
        assert(classic.asInstanceOf[js.Any] eq created.head)
        assert(module.asInstanceOf[js.Any] eq created(1))
        assertEquals(dom.document.head.querySelectorAll(s"link[rel='preload'][href='$url']").length, 1)
      }
    }
  }

  test("preheated workers are handed out once and the pool only refills unclaimed capacity with workers it could create") {
    FakeWorker.installed {
      val url = "http://localhost/pool/worker.js"
      Workers.preheat(2, url)
      Workers.preheat(2, url)
      assertEquals(created.size, 2, "preheating is idempotent for the same capacity")
      val first = Workers.takeOrCreate(url)
      val second = Workers.takeOrCreate(url)
      assertEquals(List(first, second).map(_.asInstanceOf[js.Any]), created.take(2).map(_.asInstanceOf[js.Any]))
      val third = Workers.takeOrCreate(url)
      assertEquals(created.size, 3, "an empty pool creates a worker on demand")
      assert(third.asInstanceOf[js.Any] eq created(2))
      Workers.preheat(4, url)
      assertEquals(created.size, 4, "three claimed workers leave room for one more")
      Workers.preheat(3, url)
      assertEquals(created.size, 4)
      FakeWorker.failConstruction.set(true)
      Workers.preheat(6, url)
      FakeWorker.failConstruction.set(false)
      assertEquals(created.size, 6, "two refused workers were attempted")
      assert(Workers.takeOrCreate(url).asInstanceOf[js.Any] eq created(3), "the pooled worker is still handed out")
      assert(Workers.takeOrCreate(url).asInstanceOf[js.Any] eq created(6), "refused workers were not pooled")
      Future.unit
    }
  }
}
