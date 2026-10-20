package it.evadid.distribution.command

import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.commandTypes.LLMCommands

import scala.util.Try
import upickle.default.*

case class ExecutionCommand(name: String, json: ujson.Value) derives ReadWriter{
  lazy val toJson: String = write(this)

  def stripPrivacySensitiveInformation(): ExecutionCommand =
    ExecutionCommand.privacySensitiveFactories.find(_.name == name)
      .fold(this)(_.stripPrivacySensitiveInformation(this))
}

object ExecutionCommand {

  private lazy val privacySensitiveFactories: List[ExecutionCommandFactory[?, ?]] =
    List(LLMCommands.feedbackLlmCommandFactory)

  def fromJson(json: String): ExecutionCommand = read(json)

  def tryParse(json: String): Try[ExecutionCommand] = Try{
    fromJson(json)
  }

}
