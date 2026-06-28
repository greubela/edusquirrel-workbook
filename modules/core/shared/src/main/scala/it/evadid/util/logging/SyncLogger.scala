package it.evadid.util.logging

import it.evadid.core.util.InfoUtil
import it.evadid.util.logging.LoggingLevel.*

case class SyncLogger() extends Logger {
  private val baseLogger = Logger().withPrintToStd(false, true, true)

  def prefixArrow(level: LoggingLevel, isOutgoingOption: Option[Boolean]): String = {
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

  def prefixTime: String = s"[${InfoUtil.datetimeFormattedForLog()}]"

  def prefixLevel(loggingLevel: LoggingLevel): String = loggingLevel match {
    case INFO => "INFO"
    case WARN => "WARN"
    case ERROR => "ERROR"
  }

  def log(msg: String, level: LoggingLevel, isOutgoingOption: Option[Boolean]): Unit = {
    baseLogger.log(s"[SyncLogger@$prefixTime] Sync ${prefixArrow(level, isOutgoingOption)} ${prefixLevel(level)}: $msg", level)
  }

  override def log(msg: String, level: LoggingLevel): Unit = log(msg, level, None)

  override def getOut(): String = baseLogger.getOut()

  override def getErr(): String = baseLogger.getErr()
}
