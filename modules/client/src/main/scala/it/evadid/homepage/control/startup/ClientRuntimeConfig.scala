package it.evadid.homepage.control.startup

import com.raquo.airstream.state.Var
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.util.Try

object ClientRuntimeConfig {
  private val pending = Var(Option.empty[Future[Map[String, String]]])
  private val defaults = Map("EDUSQUIRREL_BACKEND_MODULE_WORKERS" -> "1")

  private def supplied: Map[String, String] = {
    val value = dom.window.asInstanceOf[js.Dynamic].selectDynamic("EDUSQUIRREL_CLIENT_CONFIG")
    if js.isUndefined(value) || value == null then Map.empty
    else js.Object.keys(value.asInstanceOf[js.Object]).iterator.map(key => key -> value.selectDynamic(key).toString).toMap
  }
  def current: Map[String, String] = defaults ++ supplied

  private[startup] def parseEnv(text: String): Map[String, String] = text.linesIterator.flatMap { line =>
    val trimmed = line.trim
    val separator = trimmed.indexOf('=')
    Option.when(!trimmed.startsWith("#") && separator > 0) {
      val raw = trimmed.substring(separator + 1).trim
      val quoted = raw.length >= 2 && ((raw.head == '"' && raw.last == '"') || (raw.head == '\'' && raw.last == '\''))
      trimmed.substring(0, separator).trim -> (if quoted then raw.substring(1, raw.length - 1) else raw)
    }
  }.toMap

  def load(): Future[Map[String, String]] = pending.now().getOrElse {
    val result = Promise[Map[String, String]]()
    pending.set(Some(result.future))
    val explicit = supplied
    if explicit.nonEmpty then result.success(defaults ++ explicit)
    else {
      val url = new dom.URL("../../deployment/client/.env", BrowserLoading.bootstrapUrl).href
      val timeout = setTimeout(1500) { result.trySuccess(defaults) }
      Try(dom.fetch(url).toFuture.flatMap(response =>
        if response.ok then response.text().toFuture.map(text => defaults ++ parseEnv(text))
        else Future.successful(defaults))).fold(Future.failed, identity)
        .recover { case _ => defaults }.onComplete(result.tryComplete)
      result.future.onComplete(_ => clearTimeout(timeout))
    }
    result.future.map { config =>
      dom.window.asInstanceOf[js.Dynamic].updateDynamic("EDUSQUIRREL_CLIENT_CONFIG")(js.Dictionary(config.toSeq*))
      config
    }
  }

  def workerUrl: String = current.get("EDUSQUIRREL_BACKEND_MODULE_WORKER_SCRIPT")
    .filter(_.nonEmpty).map(path => new dom.URL(path, dom.document.baseURI).href).getOrElse(BrowserLoading.worker)
}
