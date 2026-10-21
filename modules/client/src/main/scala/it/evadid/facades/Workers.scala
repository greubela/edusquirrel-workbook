package it.evadid.facades

import com.raquo.airstream.state.Var
import it.evadid.homepage.control.startup.{BrowserLoading, ClientRuntimeConfig}
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.Try

object Workers {
  private def dependency(url: String, options: dom.WorkerOptions): ExternalDependency[dom.Worker] =
    new ExternalDependency(() => {
      BrowserLoading.preload(List(url)); Future.unit
    }, () => true,
      () => new dom.Worker(url, options))

  def classic(url: String): ExternalDependency[dom.Worker] = dependency(url, new dom.WorkerOptions {})

  def module(url: String): ExternalDependency[dom.Worker] =
    dependency(url, js.Dynamic.literal(`type` = "module").asInstanceOf[dom.WorkerOptions])

  private case class Pool(available: List[dom.Worker] = Nil, claimed: Int = 0)

  private val pool = Var(Pool())

  def preheat(): Unit = ClientRuntimeConfig.load().foreach { config =>
    val count = config.get("EDUSQUIRREL_BACKEND_MODULE_WORKERS").flatMap(_.toIntOption).filter(_ >= 0).getOrElse(1)
    val current = pool.now()
    val created = List.fill(math.max(0, count - current.claimed - current.available.size)) {
      Try(classic(ClientRuntimeConfig.workerUrl).createLoadedInstance()).fold(
        error => {
          dom.console.warn("Backend worker preheating failed", error.getMessage); None
        }, Some(_))
    }.flatten
    pool.update(state => state.copy(available = state.available ++ created))
  }

  def takeOrCreate(): dom.Worker = {
    // Consume workers supplied by an older embedding bootstrap at the JS boundary.
    val legacy = dom.window.asInstanceOf[js.Dynamic].selectDynamic("EDUSQUIRREL_BACKEND_WORKERS")
    val supplied = if !js.isUndefined(legacy) && legacy != null && legacy.asInstanceOf[js.Array[dom.Worker]].nonEmpty then
      Some(legacy.asInstanceOf[js.Array[dom.Worker]].shift()) else None
    val current = pool.now()
    val worker = supplied.orElse(current.available.headOption).getOrElse(classic(ClientRuntimeConfig.workerUrl).createLoadedInstance())
    pool.update(state => state.copy(
      available = if supplied.isDefined then state.available else state.available.drop(1), claimed = state.claimed + 1))
    worker
  }
}
