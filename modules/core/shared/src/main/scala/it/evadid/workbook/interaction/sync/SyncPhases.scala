package it.evadid.workbook.interaction.sync

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** Sequencing of synchronization phases: a failed phase never prevents the next one, and the result reports the
  * first failure.
  */
object SyncPhases {
  private given ExecutionContext = ExecutionContext.global

  /** Runs `operation`, turning a thrown exception into a failed future. */
  def attempt[A](operation: => Future[A]): Future[A] = Try(operation).fold(Future.failed, identity)

  /** Runs `second` once `first` has settled. */
  def inOrder(first: => Future[Unit], second: => Future[Unit]): Future[Unit] =
    attempt(first).transformWith(before => attempt(second).transform(after => before.flatMap(_ => after)))

  /** Completes once every operation has settled, with the first failure among them. */
  def settled(operations: Seq[Future[Unit]]): Future[Unit] =
    Future.sequence(operations.map(_.transform(result => scala.util.Success(result)))).flatMap(results =>
      results.collectFirst { case scala.util.Failure(error) => error }.fold(Future.unit)(Future.failed))
}
