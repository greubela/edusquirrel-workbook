package it.evadid.worker

import it.evadid.distribution.*
import org.scalajs.dom
import upickle.ReadWriter
import upickle.default.*

import java.time.LocalDateTime
import scala.util.*

object WebWorkerBackendServer {


  given mapRW: ReadWriter[Map[String, String]] =
    readwriter[ujson.Value].bimap[Map[String, String]](
      m => ujson.Obj.from(m.view.mapValues(ujson.Str(_))),
      json => json.obj.view.map { case (k, v) => k -> v.str }.toMap
    )

  def onExecuteCommandReceived(rawCommand: String): ExecutionInfo = {
    val executionCommand = read[ExecutionCommand](rawCommand)
    val now = LocalDateTime.now()
    val result = ExecutionResult(
      data = executionCommand.params,
      stdOut = s"Worker executed command '${executionCommand.name}'",
      stdErr = ""
    )
    ExecutionInfo(
      command = executionCommand,
      result = Success(result),
      meta = Some(ExecutionHistory(now, now, now, now))
    )
  }

  private def handleMessage(rawPayload: String): String = {
    val payload = read[Map[String, String]](rawPayload)
    val requestId = payload.getOrElse("requestId", throw new IllegalArgumentException("Missing requestId"))
    val rawCommand = payload.getOrElse("command", throw new IllegalArgumentException("Missing command"))

    val executionInfo = Try(onExecuteCommandReceived(rawCommand)).fold(
      exception => ExecutionInfo(
        command = ExecutionCommand("invalid", Map.empty),
        result = scala.util.Failure(exception),
        meta = None
      ),
      identity
    )

    write(Map(
      "requestId" -> requestId,
      "executionInfo" -> write(executionInfo)(using ExecutionCommand.given_ReadWriter_ExecutionInfo)
    ))(using mapRW)
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
            )(using ExecutionCommand.given_ReadWriter_ExecutionInfo)))(using mapRW)
          )
      }
    }
  }
}
