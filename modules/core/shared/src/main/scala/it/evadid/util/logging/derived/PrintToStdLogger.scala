package it.evadid.util.logging.derived

import it.evadid.util.logging.Logger.DerivedLogger
import it.evadid.util.logging.LoggingLevel.*
import it.evadid.util.logging.{Logger, LoggingLevel}

case class PrintToStdLogger(underlyingLogger: Logger, printMap: Map[LoggingLevel, Boolean] = PrintToStdLogger.printEverything) extends DerivedLogger {

  override def log(msg: String, level: LoggingLevel): Unit = {
    if (printMap(level)) print(msg)
    underlyingLogger.log(msg, level)
  }
}

case object PrintToStdLogger {

  val printError = Map(
    INFO -> false,
    WARN -> true,
    ERROR -> true
  )

  val printWarnAndError = Map(
    INFO -> false,
    WARN -> true,
    ERROR -> true
  )

  val printEverything = Map(
    INFO -> true,
    WARN -> true,
    ERROR -> true
  )

  val printNothing = Map(
    INFO -> false,
    WARN -> false,
    ERROR -> false
  )


}
