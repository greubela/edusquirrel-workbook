package it.evadid.distribution.command

import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.sql.DatabaseTable

import scala.util.Try
import upickle.default.*

case class ExecutionCommand(name: String, json: ujson.Value) derives ReadWriter{
  lazy val toJson: String = write(this)

  def stripPrivacySensitiveInformation(): ExecutionCommand =
    ExecutionCommandFactory.forName(name)
      .fold(this)(_.stripPrivacySensitiveInformation(this))

  // Routing is server-owned metadata, never supplied in the wire payload.
  def storeLogIntoTable: Option[DatabaseTable] =
    ExecutionCommandFactory.forName(name).map(_.storeLogIntoTable).getOrElse(Some(DatabaseTable.commandLog))
}

object ExecutionCommand {

  def fromJson(json: String): ExecutionCommand = read(json)

  def tryParse(json: String): Try[ExecutionCommand] = Try{
    fromJson(json)
  }

}
