package it.evadid.util.logging.derived

import it.evadid.core.util.InfoUtil
import it.evadid.util.logging.Logger.DerivedLogger
import it.evadid.util.logging.{Logger, LoggingLevel}

import java.util

case class TimeAndNamePrefixedLogger(name: Option[String], underlyingLogger: Logger) extends DerivedLogger {

  override def log(msg: String, loggingLevel: LoggingLevel): Unit = {

    val changed: String = msg.split("\n").filter(_.nonEmpty).map(TimeAndNamePrefixedLogger.prefixLine(name, _, loggingLevel).trim).toList.mkString("\n").trim
    underlyingLogger.log(changed + "\n", loggingLevel)
  }

}

object TimeAndNamePrefixedLogger {

  def prefixName(name: Option[String]): String = name.getOrElse("UnnamedLogger")

  def prefixTime: String = InfoUtil.datetimeFormattedForLog()

  def prefixLevel(loggingLevel: LoggingLevel): String = loggingLevel match {
    case LoggingLevel.INFO => "INFO"
    case LoggingLevel.WARN => "WARN"
    case LoggingLevel.ERROR => "ERROR"
  }

  def prefixLine(name: Option[String], msg: String, loggingLevel: LoggingLevel): String = {
    s"[${prefixTime}|${prefixLevel(loggingLevel)}] ${prefixName(name).trim}:    ${msg}"
    //"[PREFIX] " + msg + "\n"
  }


}