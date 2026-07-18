package it.evadid.worker

import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoUntyped
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
/*
  def onExecuteCommandReceived(rawCommand: String): ExecutionInfo = {
    val executionCommand = ExecutionCommand.fromJson(rawCommand)
    val now = LocalDateTime.now()
    val result = ExecutionResult(
      data = executionCommand.params,
      stdOut = s"Worker executed command '${executionCommand.name}'",
      stdErr = ""
    )
    ExecutionInfoUntyped(executionCommand,result,ExecutionHistory(now, now, now, now)    )
  }

  private def handleMessage(rawPayload: String): String = {
    val payload = read[Map[String, String]](rawPayload)
    val requestId = payload.getOrElse("requestId", throw new IllegalArgumentException("Missing requestId"))
    val rawCommand = payload.getOrElse("command", throw new IllegalArgumentException("Missing command"))

    val executionInfoStr: Option[String] = try{
      Some(onExecuteCommandReceived(rawCommand).toJson)
    } catch {
      case e: Exception => None
    }

    write(Map(
      "requestId" -> requestId,
      "executionInfo" -> executionInfoStr.getOrElse("[error!]")
    ))(using mapRW)
  }
*/
  def main(args: Array[String]): Unit = {
  /*
    dom.DedicatedWorkerGlobalScope.self.onmessage = (event: dom.MessageEvent) => {
      event.data match {
        case text: String =>
          dom.DedicatedWorkerGlobalScope.self.postMessage(handleMessage(text))
        case _ =>
          dom.DedicatedWorkerGlobalScope.self.postMessage(
            write(Map("requestId" -> "unknown", "executionInfo" ->
              ExecutionInfo(
                command = ExecutionCommand("invalid", Map.empty),
                result = scala.util.Failure(new IllegalArgumentException("Worker expects string messages")),
                meta = None
              ).toJson
            )))
      }
    }
	*/
  }
  
}
