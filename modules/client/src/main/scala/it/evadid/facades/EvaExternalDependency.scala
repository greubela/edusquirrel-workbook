package it.evadid.facades

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try

/** Loading shares code only; each creation returns a consumer-owned native handle. */
trait EvaExternalDependency[T] {
  def preload(): Future[Unit]

  def createInstance(): Future[T]
}

/** The synchronous entry point is for renderers that have already awaited readiness. */
final class ExternalDependency[T](ready: () => Future[Unit], available: () => Boolean, create: () => T)
  extends EvaExternalDependency[T] {
  override def preload(): Future[Unit] = Future.fromTry(Try(ready())).flatten

  def createLoadedInstance(): T = {
    require(available(), "External dependency has not been preloaded")
    create()
  }

  override def createInstance(): Future[T] = preload().map(_ => createLoadedInstance())
}
