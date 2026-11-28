package it.evadid.workbook.sync

import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
import it.evadid.workbook.abstractions.ExecutionContextInformation

/** How a binding reaches its destinations and computes feedback. Credentials belong here: replacing the connection
  * of a binding keeps its sync context and history.
  */
final case class SyncConnection(destinations: Set[SyncInformationWithContext], execution: ExecutionContextInformation,
    logger: SyncLogger) {
  /** Sends the buffered requests of every destination. */
  def flush(): Unit = destinations.foreach(_.handler.flush(true))
}

object SyncConnection {
  /** No destinations: the history stays in memory. */
  val offline: SyncConnection = SyncConnection(Set.empty, ExecutionContextInformation.default,
    SyncLogger(Logger.withNameAndPrefixes(None, PrintToStdLogger.printNothing)))
}
