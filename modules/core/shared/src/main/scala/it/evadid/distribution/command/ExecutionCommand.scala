package it.evadid.distribution.command

import it.evadid.core.util.io.serializer.DistributionSerializer

case class ExecutionCommand(name: String, params: Map[String, String]) {

  lazy val toJson: String = DistributionSerializer.serializeExecutionCommandJson.serialize(this)

}

object ExecutionCommand {

  def fromJson(json: String): ExecutionCommand = DistributionSerializer.serializeExecutionCommandJson.deserialize(json)

}
