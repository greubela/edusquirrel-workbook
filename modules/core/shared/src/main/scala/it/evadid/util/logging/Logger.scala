package it.evadid.util.logging

import it.evadid.util.logging.LoggingLevel.*
import it.evadid.util.logging.derived.{PrintToStdLogger, TimeAndNamePrefixedLogger}

trait Logger {


  private def prefixOther: String = " ~~~[Inserted Messages from external Logger]~~~\n\n "

  private def afterOther: String = "\n ~~~[End of externally Inserted Messages]~~~\n\n"

  def logFromExternalInfo(other: String): Unit = if (other.replace("\\s+", "").trim.nonEmpty) log(prefixOther + other.trim + afterOther, INFO)

  def logFromExternalError(other: String): Unit = if (other.replace("\\s+", "").trim.nonEmpty) log(prefixOther + other.trim + afterOther, ERROR)

  def logOutputFrom(other: Logger): Unit = if (other.getOut().trim.nonEmpty) logFromExternalInfo(other.getOut())

  def logErrorFrom(other: Logger): Unit = if (other.getErr().trim.nonEmpty) log(other.getErr(), ERROR)

  def logAllFrom(other: Logger): Unit = {
    logOutputFrom(other)
    logErrorFrom(other)
  }

  def log(msg: String, level: LoggingLevel): Unit

  def logException(exception: Throwable): Unit = logError(
    s"""
       |Logging Exception: ${exception.getMessage}
       |    Exception StackTrace:
       |    ${exception.getStackTrace.mkString("\n    ")}
       |
       | """.stripMargin
  )

  def logExceptionInfo(msgRecover: String, expectedBecause: String, exception: Throwable): Unit = logInfo(
    s"""
       |Recovered from expected exception by: $msgRecover
       |    Exception was expected because: ${expectedBecause}
       |    Exception message: ${exception.getMessage}
       |    Exception StackTrace:
       |    ${exception.getStackTrace.mkString("\n    ")}
       |
       | """.stripMargin
  )

  def logExceptionWarn(msgRecover: String, exception: Throwable): Unit = logWarn(
    s"""
       |Recovered from exception by: $msgRecover
       |    Exception message: ${exception.getMessage}
       |    Exception StackTrace:
       |    ${exception.getStackTrace.mkString("\n    ")}
       |
       | """.stripMargin
  )

  def logInfo(msg: String): Unit = log(msg, LoggingLevel.INFO)

  def logWarn(msg: String): Unit = log(msg, LoggingLevel.WARN)

  def logError(msg: String): Unit = log(msg, LoggingLevel.ERROR)

  def getOut(): String

  def getErr(): String

}

object Logger {

  def formatPerformance(performanceName: String, countA: Long, countB: Long, nameA: String, nameB: String): String = {
    val successRatio: String = s"%.${2}f".format((countA * 1.0 / countB)) + "%"
    val ratioPerformance = s"$performanceName performance ($nameA / $nameB): $countA / $countB (= $nameB succeeded so far (=$successRatio)"
    ratioPerformance
  }

  def withNameAndPrefixes(name: Option[String] = None, printMap: Map[LoggingLevel, Boolean] = PrintToStdLogger.printEverything): Logger = {
    new TimeAndNamePrefixedLogger(name, new PrintToStdLogger(new BasicLogger(), printMap))
  }

  trait DerivedLogger extends Logger {
    def underlyingLogger: Logger

    override def getOut(): String = underlyingLogger.getOut()

    override def getErr(): String = underlyingLogger.getErr()
  }

  def deriveFrom(underlying: Logger, deriveFunction: (String, LoggingLevel) => String): DerivedLogger = new DerivedLogger() {
    override def underlyingLogger: Logger = underlying

    override def log(msg: String, level: LoggingLevel): Unit = underlying.log(deriveFunction(msg, level), level)
  }


}



