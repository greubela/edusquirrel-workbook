package it.evadid.distribution.executor

import com.sun.source.doctree.AuthorTree
import it.evadid.distribution.ExecutionCommand
import it.evadid.distribution.*
import it.evadid.distribution.executor.Executor.*

import java.time.LocalDateTime
import scala.util.Try

trait Executor {

  def canExecute(executionCommand: ExecutionCommand): Boolean

  def execute(executionCommand: ExecutionCommand): Try[(ExecutionResult, ExecutorHistory, Logger)] = Try {
    val timeExecutionStarted = LocalDateTime.now()
    val logger = BasicLogger()
    val result = handleExecution(executionCommand, logger)
    val timeExecutionFinished = LocalDateTime.now()
    (result, ExecutorHistory(timeExecutionStarted, timeExecutionFinished), logger)
  }

  protected def resultFromValue[T](value: T, toStringFunc: T => String = (str: T) => str.toString): ExecutionResult = ExecutionResult(Map("result" -> toStringFunc(value)), "", "")

  protected def handleExecution(executionCommand: ExecutionCommand, logger: Logger): ExecutionResult

}

object Executor {

  case class ExecutorHistory(timeExecutionStarted: LocalDateTime, timeExecutionFinished: LocalDateTime)

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

  trait Logger {
    def logInfo(msg: String): Unit

    def logWarn(msg: String): Unit

    def logError(msg: String): Unit

    def getOut(): String

    def getErr(): String
  }

}