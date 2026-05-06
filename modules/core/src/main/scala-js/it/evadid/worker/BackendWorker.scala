package it.evadid.worker

import it.evadid.distribution.ExecutionCommand.ExecutionInfo
import it.evadid.distribution.*
import it.evadid.distribution.executor.Executor
import org.scalajs.dom
import upickle.default.{read, write}

import java.time.LocalDateTime
import scala.util.{Success, Try}
/*
object BackendWorker extends ExecutionServer {

  private val dummyExecutor: Executor = new Executor {
    override def canExecute(executionCommand: ExecutionCommand): Boolean = true

    override def execute(executionCommand: ExecutionCommand): Option[ExecutionInfo] = {
      val now = LocalDateTime.now()
      val result = ExecutionCommand.ExecutionResult(
        data = executionCommand.params,
        stdOut = s"Worker executed command '${executionCommand.name}'",
        stdErr = ""
      )
      Some(ExecutionInfo(
        command = executionCommand,
        result = Success(result),
        meta = Some(ExecutionCommand.ExecutionHistory(now, now, now, now))
      ))
    }
  }

  var executors: List[Executor] = List(dummyExecutor)

  override def getExecutor: Executor = new Executor {
    override def canExecute(executionCommand: ExecutionCommand): Boolean =
      executors.exists(_.canExecute(executionCommand))

    override def execute(executionCommand: ExecutionCommand): Option[ExecutionInfo] =
      executors.find(_.canExecute(executionCommand)).flatMap(_.execute(executionCommand))
  }

  def onExecuteCommandReceived(rawCommand: String): ExecutionInfo = {
    val executionCommand = read[ExecutionCommand](rawCommand)
    if (executionCommand.name.trim.isEmpty) {
      throw new IllegalArgumentException("ExecutionCommand.name must not be empty")
    }
    getExecutor.execute(executionCommand).getOrElse {
      throw new IllegalStateException(s"No executor available for command '${executionCommand.name}'")
    }
  }

  private def handleMessage(rawPayload: String): String = {
    val payload = read[Map[String, String]](rawPayload)
    val requestId = payload.getOrElse("requestId", throw new IllegalArgumentException("Missing requestId"))
    val rawCommand = payload.getOrElse("command", throw new IllegalArgumentException("Missing command"))

    val executionInfo = Try(onExecuteCommandReceived(rawCommand)) match {
      case Success(info) => info
      case scala.util.Failure(exception) =>
        ExecutionInfo(
          command = ExecutionCommand("invalid", Map.empty),
          result = scala.util.Failure(exception),
          meta = None
        )
    }

    write(Map(
      "requestId" -> requestId,
      "executionInfo" -> write(executionInfo)
    ))
  }

  def main(args: Array[String]): Unit = {
    dom.DedicatedWorkerGlobalScope.self.onmessage = (event: dom.MessageEvent) => {
      event.data match {
        case text: String =>
          dom.DedicatedWorkerGlobalScope.self.postMessage(handleMessage(text))
        case _ =>
          dom.DedicatedWorkerGlobalScope.self.postMessage(
            write(Map("requestId" -> "unknown", "executionInfo" -> write(
              ExecutionInfo(
                command = ExecutionCommand("invalid", Map.empty),
                result = scala.util.Failure(new IllegalArgumentException("Worker expects string messages")),
                meta = None
              )
            )))
          )
      }
    }
  }
}*/
