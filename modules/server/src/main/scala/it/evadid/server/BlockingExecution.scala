package it.evadid.server

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

/** Runs blocking JDBC, SMTP and HTTP calls on virtual threads, keeping them off the CPU-sized global pool. */
object BlockingExecution {
  given context: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newVirtualThreadPerTaskExecutor())

  def apply[A](run: => A): Future[A] = Future(run)
}
