package it.evadid.util.logging

import it.evadid.core.util.InfoUtil

trait Logger {

  def log(msg: String, level: LoggingLevel): Unit

  def logException(throwable: Throwable): Unit = logError(s"Exception thrown: ${throwable.getMessage}\n ${throwable.getStackTrace.mkString("\n")}\n")

  def logInfo(msg: String): Unit = log(msg, LoggingLevel.INFO)

  def logWarn(msg: String): Unit = log(msg, LoggingLevel.WARN)

  def logError(msg: String): Unit = log(msg, LoggingLevel.ERROR)

  def getOut(): String

  def getErr(): String

  def withPrintToStd(printOnInfo: Boolean = true, printOnWarn: Boolean = true, printOnError: Boolean = true): Logger = new Logger() {

    override def log(msg: String, level: LoggingLevel): Unit = {
      if (level == LoggingLevel.INFO && printOnInfo) println(msg)
      if (level == LoggingLevel.WARN && printOnWarn) println(msg)
      if (level == LoggingLevel.ERROR && printOnError) println(msg)
      Logger.this.log(msg, level)
    }

    override def getOut(): String = Logger.this.getOut()

    override def getErr(): String = Logger.this.getErr()
  }

  def withTimeAndLevelPrefixed(name: Option[String]): Logger = {
    lazy val prefixName: String = name.getOrElse("UnnamedLogger")

    def prefixTime: String = InfoUtil.datetimeFormattedForLog()

    def prefixLevel(loggingLevel: LoggingLevel): String = loggingLevel match {
      case LoggingLevel.INFO => "INFO"
      case LoggingLevel.WARN => "WARN"
      case LoggingLevel.ERROR => "ERROR"
    }

    withUpdated( (msg, level) => s"[$prefixName@$prefixTime|${prefixLevel(level)}]: $msg")
  }

  def withUpdated(updater: (String, LoggingLevel) => String): Logger = new Logger() {

    override def log(msg: String, level: LoggingLevel): Unit = Logger.this.log(updater(msg, level), level)

    override def getOut(): String = Logger.this.getOut()

    override def getErr(): String = Logger.this.getErr()
  }

}

object Logger {




  def apply(name: Option[String] = None): Logger = BasicLogger().withTimeAndLevelPrefixed(name)


}



