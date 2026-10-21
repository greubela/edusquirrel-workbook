package it.evadid.homepage.control.startup

import com.raquo.airstream.state.Var
import org.scalajs.dom
import it.evadid.homepage.webElements.facades.ExternalDependencies.Workers
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.Try

object WorkerPreloading {
  private case class Pool(available: List[dom.Worker] = Nil, claimed: Int = 0)
  private val pool = Var(Pool())

  def preheat(): Unit = ClientRuntimeConfig.load().foreach { config =>
    val count = config.get("EDUSQUIRREL_BACKEND_MODULE_WORKERS").flatMap(_.toIntOption).filter(_ >= 0).getOrElse(1)
    val current = pool.now()
    val created = List.fill(math.max(0, count - current.claimed - current.available.size)) {
      Try(Workers.classic(ClientRuntimeConfig.workerUrl).createLoadedInstance()).fold(
        error => { dom.console.warn("Backend worker preheating failed", error.getMessage); None }, Some(_))
    }.flatten
    pool.update(state => state.copy(available = state.available ++ created))
  }

  def takeOrCreate(): dom.Worker = {
    // Consume workers supplied by an older embedding bootstrap at the JS boundary.
    val legacy = dom.window.asInstanceOf[js.Dynamic].selectDynamic("EDUSQUIRREL_BACKEND_WORKERS")
    val supplied = if !js.isUndefined(legacy) && legacy != null && legacy.asInstanceOf[js.Array[dom.Worker]].nonEmpty then
      Some(legacy.asInstanceOf[js.Array[dom.Worker]].shift()) else None
    val current = pool.now()
    val worker = supplied.orElse(current.available.headOption).getOrElse(Workers.classic(ClientRuntimeConfig.workerUrl).createLoadedInstance())
    pool.update(state => state.copy(
      available = if supplied.isDefined then state.available else state.available.drop(1), claimed = state.claimed + 1))
    worker
  }
}
