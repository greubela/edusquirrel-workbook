package it.evadid.util

import java.time.LocalDateTime

trait Logger {
  def logInfo(msg: String): Unit

  def logWarn(msg: String): Unit

  def logError(msg: String): Unit

  def getOut(): String

  def getErr(): String
  
  def withPrintToStd(): Logger = new Logger(){

    override def logInfo(msg: String): Unit = {
      println(msg)
      Logger.this.logInfo(msg)
    }

    override def logWarn(msg: String): Unit = {
      println(msg)
      Logger.this.logWarn(msg)
    }

    override def logError(msg: String): Unit = {
      println(msg)
      Logger.this.logError(msg)
    }

    override def getOut(): String = {
      Logger.this.getOut()
    }

    override def getErr(): String = {
      Logger.this.getErr()
    }
  }
  
}

object Logger {
  private case class BasicLogger() extends Logger {
    private val out = StringBuilder()
    private val err = StringBuilder()

    def logInfo(msg: String): Unit = out.append(s"[INFO] ${LocalDateTime.now().toString}: $msg\n")

    def logWarn(msg: String): Unit = out.append(s"[WARN] ${LocalDateTime.now().toString}: $msg\n")

    def logError(msg: String): Unit = err.append(s"[ERROR] ${LocalDateTime.now().toString}: $msg\n")

    def logException(throwable: Throwable): Unit = err.append(s"[EXCEPTION] ${LocalDateTime.now().toString}: ${throwable.getMessage}\n ${throwable.getStackTrace.mkString("\n")}\n")

    def getOut(): String = out.toString()

    def getErr(): String = err.toString()
  }
  
  def apply(): Logger = BasicLogger()
}



