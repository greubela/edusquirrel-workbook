package it.evadid.server.database.schema

import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.server.{BlockingExecution, ServerConsoleLogger}
import it.evadid.server.database.Database
import it.evadid.sql.DatabaseConfigWithCredentials
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

/** Validates each database's schema once and remembers the successful result. A failed or incompatible check is
  * not kept: it is repeated by the next request, so a database that comes up later or is repaired becomes usable.
  * Schemas changed while the server runs are therefore detected only after a restart.
  */
final class SchemaValidation extends AsyncDataCache[SchemaValidation.Requirement, DatabaseSchemaInspector.Report](
  ServerConsoleLogger("SchemaValidation")) {
  import SchemaValidation.*

  /** Fails with the inspection's diagnostics unless the database holds the `requirement`'s tables as scripted. */
  def requireValid(requirement: Requirement): Unit = Await.result(validate(requirement), checkTimeout)

  def validate(requirement: Requirement): Future[DatabaseSchemaInspector.Report] =
    loadAsFuture(requirement)(using BlockingExecution.context)

  override protected def executeLoading(requirement: Requirement)(ec: ExecutionContext): Future[DatabaseSchemaInspector.Report] =
    BlockingExecution {
      val report = Database.withConnection(requirement.database, "inspecting the database schema", baseLogger) { connection =>
        DatabaseSchemaInspector.inspect(connection, requirement.tables)
      }
      require(report.valid, s"Database schema is incompatible: ${report.describe}")
      report
    }

  override protected def formatInputForLogging(requirement: Requirement): String = requirement.toString

  override protected def formatOutputForLogging(report: DatabaseSchemaInspector.Report): String = "valid"
}

object SchemaValidation {
  private val checkTimeout = 30.seconds

  /** The `tables` of the script that `database` must hold; empty requires every table. */
  final case class Requirement(database: DatabaseConfigWithCredentials, tables: Set[String]) {
    override def toString: String = s"Requirement(${database.withoutCredentials}, ${tables.toSeq.sorted.mkString(",")})"
  }
}
