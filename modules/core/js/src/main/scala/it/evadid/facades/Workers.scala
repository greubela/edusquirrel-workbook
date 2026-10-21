package it.evadid.facades

import com.raquo.airstream.state.Var
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

  def preheat(count: Int, url: String): Unit = {
    val current = pool.now()
    val created = List.fill(math.max(0, count - current.claimed - current.available.size)) {
      Try(classic(url).createLoadedInstance()).fold(
        error => {
          dom.console.warn("Backend worker preheating failed", error.getMessage); None
        }, Some(_))
    }.flatten
    pool.update(state => state.copy(available = state.available ++ created))
  }

  def takeOrCreate(url: String): dom.Worker = {
    val current = pool.now()
    val worker = current.available.headOption.getOrElse(classic(url).createLoadedInstance())
    pool.update(state => state.copy(
      available = state.available.drop(1), claimed = state.claimed + 1))
    worker
  }
}
