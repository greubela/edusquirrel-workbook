package it.evadid.homepage.control

import com.raquo.laminar.api.L.Var
import scala.scalajs.js

/** Serves the repository's `resources/` directory in place of the web server: `fetch` of a URL whose path starts
  * with `/resources/` answers with that file, any other URL with HTTP 404.
  */
final class FakeStaticSite {
  val requested = Var(Vector.empty[String])
  private val global = js.Dynamic.global.globalThis
  private val previous = global.fetch
  private val fs = js.Dynamic.global.require("fs")
  private val root = js.Dynamic.global.process.cwd().asInstanceOf[String]

  private val fetch: js.Function1[js.Any, js.Promise[js.Dynamic]] = request => {
    val url = js.Dynamic.newInstance(js.Dynamic.global.URL)(request.toString)
    val path = js.Dynamic.global.decodeURIComponent(url.pathname).asInstanceOf[String]
    requested.update(_ :+ path)
    val file = root + path
    val found = path.startsWith("/resources/") && !path.contains("..") &&
      fs.existsSync(file).asInstanceOf[Boolean] && fs.statSync(file).isFile().asInstanceOf[Boolean]
    val response = if found then js.Dynamic.newInstance(js.Dynamic.global.Response)(fs.readFileSync(file))
      else js.Dynamic.newInstance(js.Dynamic.global.Response)("not found", js.Dynamic.literal(status = 404))
    js.Promise.resolve[js.Dynamic](response)
  }

  def install(): Unit = { requested.set(Vector.empty); global.updateDynamic("fetch")(fetch) }
  def uninstall(): Unit = global.updateDynamic("fetch")(previous)
}
