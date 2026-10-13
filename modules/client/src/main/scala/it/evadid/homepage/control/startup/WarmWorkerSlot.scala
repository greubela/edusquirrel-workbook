package it.evadid.homepage.control.startup

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** Warm one idle worker and transfer ownership; never share execution-session state. */
private[startup] final class WarmWorkerSlot[A](create: () => A, initialize: A => Future[Unit], close: A => Unit)(using ExecutionContext) {
  private var spare: Option[(A, Future[Unit])] = None

  def preheat(): Future[Unit] = spare match {
    case Some((_, ready)) => ready
    case None =>
      val worker = create()
      val ready = Try(initialize(worker)).fold(Future.failed, identity)
      spare = Some(worker -> ready)
      ready.failed.foreach { _ =>
        if (spare.exists(_._2 eq ready)) { spare = None; close(worker) }
      }
      ready
  }

  def acquire(): A = spare match {
    case Some((worker, _)) => spare = None; worker
    case None => create()
  }
}
