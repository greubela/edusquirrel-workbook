package it.evadid.homepage.control.startup

import com.raquo.airstream.state.Var
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.{clearTimeout, setTimeout}

/** Fetch ahead in parallel, execute in dependency order, and share each script load.
  * Inserting only the next script also stops execution cleanly after a failed dependency.
  */
object BrowserLoading {
  private val loads = Var(Map.empty[String, Future[Unit]])
  private val preloads = Var(Set.empty[String])

  def preload(urls: List[String]): Unit = urls.foreach { url =>
    if !preloads.now().contains(url) then {
      preloads.update(_ + url)
      val link = dom.document.createElement("link").asInstanceOf[dom.html.Link]
      link.rel = "preload"
      link.setAttribute("as", "script")
      link.setAttribute("fetchpriority", "low")
      link.href = url
      dom.document.head.appendChild(link)
    }
  }

  def load(url: String): Future[Unit] = loads.now().getOrElse(url, {
    val result = Promise[Unit]()
    loads.update(_ + (url -> result.future))
    val script = dom.document.createElement("script").asInstanceOf[dom.html.Script]
    script.async = false
    script.src = url
    script.setAttribute("fetchpriority", "low")
    val timeout = setTimeout(30000) {
      result.tryFailure(new RuntimeException(s"Script load timed out: $url"))
    }
    script.onload = _ => { result.trySuccess(()); () }
    script.addEventListener("error", (_: dom.Event) => { result.tryFailure(new RuntimeException(s"Failed to load $url")); () })
    result.future.onComplete { outcome =>
      clearTimeout(timeout)
      if outcome.isFailure then {
        script.remove()
        loads.update(_ - url)
      }
    }
    dom.document.head.appendChild(script)
    result.future
  })

  def ordered(urls: List[String]): Future[Unit] = {
    preload(urls)
    urls.foldLeft(Future.unit)((ready, url) => ready.flatMap(_ => load(url)))
  }

  def siteRoot: String = {
    val script = Option(dom.document.querySelector("script[data-eva-client]"))
      .map(_.asInstanceOf[dom.html.Script].src)
      .getOrElse(new dom.URL("../../artifacts/newest/client.js", dom.document.baseURI).href)
    new dom.URL("../../", script).href
  }
  def resource(path: String): String = new dom.URL("resources/" + path, siteRoot).href
  def worker: String = new dom.URL("artifacts/newest/worker.js", siteRoot).href
}
