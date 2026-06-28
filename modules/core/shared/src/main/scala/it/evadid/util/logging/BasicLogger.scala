package it.evadid.util.logging

import it.evadid.util.logging.LoggingLevel.*

case class BasicLogger() extends Logger {
  private val out = StringBuilder()
  private val err = StringBuilder()

  def getOut(): String = out.toString()

  def getErr(): String = err.toString()

  override def log(msg: String, level: LoggingLevel): Unit = level.match{
    case INFO => out.append(msg).append("\n")
    case WARN => out.append(msg).append("\n")
    case ERROR => out.append(msg).append("\n")
  }

}

