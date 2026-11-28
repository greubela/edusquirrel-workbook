package it.evadid.server

import it.evadid.util.logging.{Logger, LoggingLevel}

/** Console logger for long-lived server components: prints warnings and errors and retains nothing in memory. */
final class ServerConsoleLogger(name: String) extends Logger {
  override def log(msg: String, level: LoggingLevel): Unit =
    if level != LoggingLevel.INFO then println(s"[$name] $level: $msg")

  override def getOut(): String = ""

  override def getErr(): String = ""
}
