package it.evadid.server.database

import it.evadid.sql.DatabaseConfigWithCredentials
import java.sql.{Connection, DriverManager}
import scala.util.{Try, Using}

/** Read-only inspection; the SQL script defines the expected schema. */
object DatabaseSchemaInspector {
  case class Report(reachable: Boolean, existingTables: Set[String], missingTables: Set[String], conflicts: Seq[String],
                    created: Boolean = false, existingRoutines: Set[String] = Set.empty) {
    def valid: Boolean = reachable && missingTables.isEmpty && conflicts.isEmpty
  }

  def inspect(connection: Connection, requiredTables: Set[String] = Set.empty): Report = {
    val definitions = DatabaseSchema.fromScripts(requiredTables)
    val actual = DatabaseSchemaMetadata.read(connection, definitions.map(_.name).toSet)
    Report(true, actual.existingTables, definitions.map(_.name).toSet -- actual.existingTables,
      definitions.flatMap(expected => actual.tables.get(expected.name).toSeq.flatMap(DatabaseSchemaComparison.compare(expected, _))),
      existingRoutines = actual.routines)
  }

  def check(config: DatabaseConfigWithCredentials): Report = withConnection(config)(inspect(_))

  private[database] def withConnection(config: DatabaseConfigWithCredentials)(run: Connection => Report): Report =
    Try(DriverManager.getConnection(config.jdbcUrl + "?connectTimeout=3000&socketTimeout=3000", config.user, config.password)).fold(
      error => Report(false, Set.empty, Set.empty, List(Option(error.getMessage).getOrElse(error.getClass.getSimpleName))),
      connection => Using.resource(connection) { connection =>
        Try(run(connection)).fold(
          error => Report(true, Set.empty, Set.empty, List(Option(error.getMessage).getOrElse(error.getClass.getSimpleName))), identity)
      })

}
