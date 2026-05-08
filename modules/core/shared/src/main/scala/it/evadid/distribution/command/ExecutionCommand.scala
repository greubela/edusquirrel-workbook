package it.evadid.distribution.command

import it.evadid.core.util.io.serializer.DistributionSerializer
import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.distribution.executor.*
import upickle.default.*

import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}

case class ExecutionCommand(name: String, params: Map[String, String]) {

  lazy val toJson: String = DistributionSerializer.serializeExecutionCommandJson.serialize(this)

}

object ExecutionCommand {

  def fromJson(json: String): ExecutionCommand = DistributionSerializer.serializeExecutionCommandJson.deserialize(json)

}
