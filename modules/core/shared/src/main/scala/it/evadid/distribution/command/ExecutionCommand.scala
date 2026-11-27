package it.evadid.distribution.command

import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.sql.CommandLog

import scala.util.Try
import upickle.default.*

case class ExecutionCommand(name: String, json: ujson.Value) derives ReadWriter{
  lazy val toJson: String = write(this)

  def stripPrivacySensitiveInformation(): ExecutionCommand =
    ExecutionCommandFactory.forName(name)
      .fold(this)(_.stripPrivacySensitiveInformation(this))

  def requiresLogin: Boolean = ExecutionCommandFactory.forName(name).exists(_.requiresLogin)

  // Routing is server-owned metadata, never supplied in the wire payload.
  def storeLogIntoTable: Option[CommandLog] =
    ExecutionCommandFactory.forName(name).map(_.storeLogIntoTable).getOrElse(Some(CommandLog.commandLog))
}

object ExecutionCommand {

  def fromJson(json: String): ExecutionCommand = read(json)

  def tryParse(json: String): Try[ExecutionCommand] = Try{
    fromJson(json)
  }

}
