package it.evadid.util.logging.derived

import it.evadid.core.util.InfoUtil
import it.evadid.util.logging.Logger.DerivedLogger
import it.evadid.util.logging.LoggingLevel.*
import it.evadid.util.logging.{Logger, LoggingLevel}

case class SyncLogger(underlyingLogger: Logger) extends DerivedLogger {

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

  def log(msg: String, level: LoggingLevel, isOutgoingOption: Option[Boolean]): Unit = {
    underlyingLogger.log(s"[SyncLogger@${TimeAndNamePrefixedLogger.prefixTime}] Sync ${prefixArrow(level, isOutgoingOption)} ${TimeAndNamePrefixedLogger.prefixLevel(level)}: $msg\n", level)
  }

  override def log(msg: String, level: LoggingLevel): Unit = log(msg, level, None)

}
