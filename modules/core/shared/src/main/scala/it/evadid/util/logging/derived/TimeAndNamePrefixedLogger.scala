package it.evadid.util.logging.derived

import it.evadid.core.util.InfoUtil
import it.evadid.util.logging.Logger.DerivedLogger
import it.evadid.util.logging.{Logger, LoggingLevel}

case class TimeAndNamePrefixedLogger(name: Option[String], underlyingLogger: Logger) extends DerivedLogger {

  override def log(msg: String, loggingLevel: LoggingLevel): Unit = underlyingLogger.log(TimeAndNamePrefixedLogger.prefixLine(name, msg, loggingLevel), loggingLevel)
}

object TimeAndNamePrefixedLogger {

  def prefixName(name: Option[String]): String = name.getOrElse("UnnamedLogger")

  def prefixTime: String = InfoUtil.datetimeFormattedForLog()

  def prefixLevel(loggingLevel: LoggingLevel): String = loggingLevel match {
    case LoggingLevel.INFO => "INFO"
    case LoggingLevel.WARN => "WARN"
    case LoggingLevel.ERROR => "ERROR"
  }

  def prefixLine(name: Option[String], msg: String, loggingLevel: LoggingLevel): String = s"[${prefixName(name)}@${prefixTime}|${prefixLevel(loggingLevel)}] }"


}