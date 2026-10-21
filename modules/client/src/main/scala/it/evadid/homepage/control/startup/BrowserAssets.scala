package it.evadid.homepage.control.startup

import org.scalajs.dom

/** Paths are relative to the bootstrap, so Pages prefixes and local exports agree. */
object BrowserAssets {
  def bootstrapUrl: String = {
    val scripts = dom.document.querySelectorAll("script[src]")
    (0 until scripts.length).iterator.map(i => scripts(i).asInstanceOf[dom.html.Script].src)
      .find(_.takeWhile(_ != '?').endsWith("/app-loader.js"))
      .getOrElse(new dom.URL("../js/app-loader.js", dom.document.baseURI).href)
  }
  def resource(path: String): String = new dom.URL("../../resources/" + path, bootstrapUrl).href
  def worker: String = new dom.URL("../../artifacts/newest/worker.js", bootstrapUrl).href
}
