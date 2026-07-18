package it.evadid.util.logging.derived

import it.evadid.util.logging.Logger.DerivedLogger
import it.evadid.util.logging.LoggingLevel.*
import it.evadid.util.logging.{Logger, LoggingLevel}

case class SyncLogger(underlyingLogger: Logger, namedSyncDest: Option[String] = None) extends DerivedLogger {

  def forSyncDest(name: String): SyncLogger = SyncLogger(underlyingLogger, Some(name))

  private def prefixArrow(level: LoggingLevel, isOutgoingOption: Option[Boolean]): String = {
    if (isOutgoingOption.isEmpty) level.match {
      case INFO => "-----"
      case WARN => "--?--"
      case ERROR => "--|--"
    } else level.match {
      case INFO if isOutgoingOption.get => "--->>"
      case INFO if !isOutgoingOption.get => "<<---"
      case WARN if isOutgoingOption.get => "-?->>"
      case WARN if !isOutgoingOption.get => "<<-?-"
      case ERROR if isOutgoingOption.get => "-|->>"
      case ERROR if !isOutgoingOption.get => "<<-|-"
      case _ => "?????"
    }
  }

  private lazy val syncDestStr = namedSyncDest.map(str => s" to '$str''").getOrElse("")

  def log(msg: String, level: LoggingLevel, isOutgoingOption: Option[Boolean]): Unit = {
    underlyingLogger.log(s"[SyncLogger@${TimeAndNamePrefixedLogger.prefixTime}] Sync$syncDestStr ${prefixArrow(level, isOutgoingOption)} ${TimeAndNamePrefixedLogger.prefixLevel(level)}: $msg\n", level)
  }

  override def log(msg: String, level: LoggingLevel): Unit = log(msg, level, None)

}
