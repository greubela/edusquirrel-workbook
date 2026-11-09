package it.evadid.server.database

import it.evadid.sql.DatabaseConfiguration
import it.evadid.distribution.commandTypes.SQLCommands.DbRequest
import it.evadid.server.commandHandler.sql.RichDatabaseConfig
import java.sql.Connection
import scala.util.{Try, Using}

object DatabaseRequestConnection {
  private def sameTarget(first: DatabaseConfiguration, second: DatabaseConfiguration): Boolean =
    first.dbFormat == second.dbFormat && first.host.equalsIgnoreCase(second.host) &&
      first.port == second.port && first.database == second.database

  def resolve(request: DbRequest, defaults: DatabaseConfiguration): DatabaseConfiguration = {
    val base = request.databaseCredentials.getOrElse(defaults)
    val configured = request.databaseConfig.completeWith(base)
    require(configured.dbFormat == "mysql", "Only MySQL database connections are supported")
    require(Option(configured.host).exists(host => host.nonEmpty && !host.exists(c => c.isWhitespace || "/?@#&;\\".contains(c))), "Invalid database host")
    require(Try(configured.port.toInt).toOption.exists(port => port > 0 && port <= 65535), "Invalid database port")
    require(Option(configured.database).exists(_.matches("[A-Za-z0-9_$-]+")), "Invalid database name")
    require(sameTarget(configured, base), if request.databaseCredentials.isDefined then
      "Database target does not match the supplied session credentials" else "Custom databases require session credentials")
    configured
  }

  def use[A](request: DbRequest)(run: Connection => A): A = {
    val configured = resolve(request, DatabaseConfiguration.readFromEnv())
    Using.resource(configured.newConnection()) { connection =>
      val report = EnsureDatabaseProperlySetup.inspect(connection, request.databaseFormat.tables.toSet)
      require(report.valid, s"History database schema is incompatible: missing=${report.missingTables.mkString(",")}; conflicts=${report.conflicts.mkString("; ")}")
      run(connection)
    }
  }
}
