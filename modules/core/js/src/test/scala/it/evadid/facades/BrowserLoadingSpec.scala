package it.evadid.facades

import it.evadid.facades.BrowserScripts.*
import it.evadid.util.JsDomSuite
import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class BrowserLoadingSpec extends JsDomSuite {
  private def headScripts(src: String) = dom.document.head.querySelectorAll(s"script[src='$src']").length

  test("preloading inserts one low-priority link per script URL") {
    val urls = List("http://localhost/preload/a.js", "http://localhost/preload/b.js")
    BrowserLoading.preload(urls)
    BrowserLoading.preload(urls.reverse)
    val links = urls.map(url => dom.document.head.querySelectorAll(s"link[href='$url']"))
    assertEquals(links.map(_.length), List(1, 1))
    val link = links.head(0).asInstanceOf[dom.html.Link]
    assertEquals(link.rel, "preload")
    assertEquals(link.getAttribute("as"), "script")
    assertEquals(link.getAttribute("fetchpriority"), "low")
  }

  test("a script load is shared by concurrent callers and inserted once") {
    val url = "http://localhost/load/shared.js"
    BrowserScripts.using(_ => Pending) { scripts =>
      val first = BrowserLoading.load(url)
      val second = BrowserLoading.load(url)
      assert(first eq second)
      val script = dom.document.head.querySelector(s"script[src='$url']").asInstanceOf[dom.html.Script]
      assert(!script.async)
      assertEquals(script.getAttribute("fetchpriority"), "low")
      tick().flatMap { _ =>
        assert(!first.isCompleted)
        scripts.complete(url)
        first
      }.map { _ =>
        assertEquals(headScripts(url), 1)
        assert(BrowserLoading.load(url) eq first, "successful loads stay cached")
      }
    }
  }

  test("a failed script is removed so a later load can retry it") {
    val url = "http://localhost/load/flaky.js"
    val attempts = js.Array[String]()
    BrowserScripts.using { src => attempts.push(src); if attempts.length == 1 then Fail else Load() } { _ =>
      BrowserLoading.load(url).failed.flatMap(error => tick().map(_ => error)).flatMap { error =>
        assertEquals(error.getMessage, s"Failed to load $url")
        assertEquals(headScripts(url), 0)
        BrowserLoading.load(url)
      }.map { _ =>
        assertEquals(attempts.toList, List(url, url))
        assertEquals(headScripts(url), 1)
      }
    }
  }

  test("ordered loading executes dependencies one after another and stops after a failure") {
    val base = "http://localhost/ordered/"
    val executed = js.Array[String]()
    BrowserScripts.using { src =>
      if src.endsWith("broken.js") then Fail else Load(() => executed.push(src.stripPrefix(base)))
    } { scripts =>
      BrowserLoading.ordered(List("one.js", "two.js").map(base + _)).flatMap { _ =>
        assertEquals(executed.toList, List("one.js", "two.js"))
        assertEquals(dom.document.head.querySelectorAll(s"link[href='${base}two.js']").length, 1)
        BrowserLoading.ordered(List("one.js", "broken.js", "three.js").map(base + _)).failed
      }.map { error =>
        assert(error.getMessage.contains("broken.js"))
        assert(!scripts.scripts.contains(base + "three.js"), "a script after a failed dependency must not run")
        assertEquals(executed.toList, List("one.js", "two.js"))
      }
    }
  }

  test("site resources resolve against the client script or the page location") {
    assertEquals(BrowserLoading.siteRoot, "http://localhost/")
    assertEquals(BrowserLoading.resource("programs/x.js"), "http://localhost/resources/programs/x.js")
    assertEquals(BrowserLoading.worker, "http://localhost/artifacts/newest/worker.js")
    val client = dom.document.createElement("script").asInstanceOf[dom.html.Script]
    client.setAttribute("data-eva-client", "")
    client.src = "https://cdn.example.org/site/artifacts/newest/client.js"
    dom.document.body.appendChild(client)
    try {
      assertEquals(BrowserLoading.siteRoot, "https://cdn.example.org/site/")
      assertEquals(BrowserLoading.resource("a b.js"), "https://cdn.example.org/site/resources/a%20b.js")
    } finally client.remove()
  }
}
