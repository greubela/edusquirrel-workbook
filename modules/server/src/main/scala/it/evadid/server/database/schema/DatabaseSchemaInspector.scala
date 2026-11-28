package it.evadid.server.database.schema

import java.sql.Connection

/** Read-only inspection; the SQL script defines the expected schema. */
object DatabaseSchemaInspector {
  case class Report(existingTables: Set[String], missingTables: Set[String], conflicts: Seq[String],
                    created: Boolean = false, existingRoutines: Set[String] = Set.empty) {
    def valid: Boolean = missingTables.isEmpty && conflicts.isEmpty

    def describe: String = s"missing=${missingTables.toSeq.sorted.mkString(",")}; conflicts=${conflicts.mkString("; ")}"
  }

  /** Compares the `requiredTables` (all script tables when empty) with the connected database. */
  def inspect(connection: Connection, requiredTables: Set[String] = Set.empty): Report = {
    val definitions = DatabaseSchema.fromScripts(requiredTables)
    val actual = DatabaseSchemaMetadata.read(connection, definitions.map(_.name).toSet)
    Report(actual.existingTables, definitions.map(_.name).toSet -- actual.existingTables,
      definitions.flatMap(expected => actual.tables.get(expected.name).toSeq.flatMap(DatabaseSchemaComparison.compare(expected, _))),
      existingRoutines = actual.routines)
  }
}
