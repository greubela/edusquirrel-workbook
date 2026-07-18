package it.evadid.distribution.command

import it.evadid.core.util.io.serializer.DefaultSerializer

import scala.util.Try

case class ExecutionCommand(name: String, params: Map[String, String]) {

  lazy val toJson: String = DefaultSerializer.serializeExecutionCommandJson.serialize(this)

}

object ExecutionCommand {

  def fromJson(json: String): ExecutionCommand = DefaultSerializer.serializeExecutionCommandJson.deserialize(json)

  def tryParse(json: String): Try[ExecutionCommand] = Try{
    fromJson(json)
  }

}
