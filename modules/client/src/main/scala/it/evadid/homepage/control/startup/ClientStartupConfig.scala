package it.evadid.homepage.control.startup

import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Runtime deployment configuration is optional background work, not a bundle gate. */
object ClientStartupConfig {
  private[startup] def parse(text: String): js.Dynamic = {
    val result = js.Dynamic.literal()
    text.linesIterator.map(_.trim).filter(line => line.nonEmpty && !line.startsWith("#")).foreach { line =>
      val separator = line.indexOf('=')
      if (separator >= 0) {
        val raw = line.substring(separator + 1).trim
        val value = if (raw.length >= 2 && ((raw.head == '"' && raw.last == '"') || (raw.head == '\'' && raw.last == '\''))) raw.substring(1, raw.length - 1) else raw
        result.updateDynamic(line.substring(0, separator).trim)(value)
      }
    }
    result
  }

  private[startup] def workerCount(config: js.Dynamic): Int = {
    val value = config.selectDynamic("EDUSQUIRREL_BACKEND_MODULE_WORKERS")
    if (js.isUndefined(value)) 1
    else scala.util.Try(value.asInstanceOf[String].toInt).toOption.filter(_ >= 0).getOrElse(1)
  }

  def load(): Future[js.Dynamic] = {
    val paths = List(new dom.URL("/deployment/client/.env", dom.document.baseURI).href,
      BrowserDependencyLoading.siteUrl("deployment/client/.env")).distinct
    def next(remaining: List[String]): Future[js.Dynamic] = remaining match {
      case Nil => Future.successful(js.Dynamic.literal())
      case path :: rest => dom.fetch(path).toFuture.flatMap { response =>
        if (response.ok) response.text().toFuture.map(parse)
        else next(rest)
      }.recoverWith { case _ => next(rest) }
    }
    next(paths)
  }
}
