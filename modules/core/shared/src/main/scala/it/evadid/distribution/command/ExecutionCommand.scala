package it.evadid.distribution.command

import it.evadid.core.util.io.serializer.DefaultSerializer

import scala.util.Try
import upickle.default.*

case class ExecutionCommand(name: String, json: ujson.Value) derives ReadWriter{
  lazy val toJson: String = write(this)
}

object ExecutionCommand {

  def fromJson(json: String): ExecutionCommand = read(json)

  def tryParse(json: String): Try[ExecutionCommand] = Try{
    fromJson(json)
  }

}
