package it.evadid.homepage.control.startup

import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.collection.mutable

/** Shared, retryable loads for versioned browser programs. Consumers alone await them. */
object BrowserDependencyLoading {
  private val pending = mutable.Map.empty[String, Future[Unit]]

  def bootstrapUrl: String = {
    val scripts = dom.document.querySelectorAll("script[src]")
    (0 until scripts.length).iterator.map(i => scripts(i).asInstanceOf[dom.html.Script].src)
      .find(_.takeWhile(_ != '?').endsWith("/app-loader.js"))
      .getOrElse(new dom.URL("../js/app-loader.js", dom.document.baseURI).href)
  }

  def siteUrl(path: String): String = {
    val bootstrap = new dom.URL(bootstrapUrl)
    val root = new dom.URL(if (bootstrap.pathname.endsWith("/homepage/js/app-loader.js")) "../../" else "../", bootstrap.href)
    new dom.URL(path, root.href).href
  }

  def script(url: String): Future[Unit] = pending.getOrElseUpdate(url, {
    val result = Promise[Unit]()
    val script = dom.document.createElement("script").asInstanceOf[dom.html.Script]
    script.src = url
    script.async = false
    script.setAttribute("fetchpriority", "low")
    val timeout = setTimeout(30000) {
      script.remove()
      result.tryFailure(new RuntimeException(s"Dependency load timed out: $url"))
    }
    script.onload = _ => { clearTimeout(timeout); result.trySuccess(()); () }
    script.addEventListener("error", (_: dom.Event) => {
      clearTimeout(timeout)
      script.remove()
      result.tryFailure(new RuntimeException(s"Dependency failed to load: $url"))
      ()
    })
    dom.document.head.appendChild(script)
    result.future.failed.foreach(_ => pending.remove(url))
    result.future
  })
}
