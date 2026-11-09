package it.evadid.sql

import upickle.default.*

case class DatabaseConfiguration(dbFormat: String, host: String, port: String, database: String, user: String, password: String) derives ReadWriter {
  lazy val jdbcUrl: String = s"jdbc:$dbFormat://$host:$port/$database"
  def publicInformation: DatabaseConfiguration.PartialDatabaseInformation =
    DatabaseConfiguration.PartialDatabaseInformation(Some(dbFormat), Some(host), Some(port), Some(database))
  override def toString: String = s"DatabaseConfiguration($dbFormat,$host,$port,$database,credentials=redacted)"
}

object DatabaseConfiguration {
  /** Non-secret connection information suitable for saved user preferences. */
  case class PartialDatabaseInformation(dbFormatOp: Option[String] = None, hostOp: Option[String] = None,
      portOp: Option[String] = None, databaseOp: Option[String] = None) derives ReadWriter {
    def withFallback(other: PartialDatabaseInformation): PartialDatabaseInformation =
      PartialDatabaseInformation(dbFormatOp.orElse(other.dbFormatOp), hostOp.orElse(other.hostOp),
        portOp.orElse(other.portOp), databaseOp.orElse(other.databaseOp))
    def completeWith(base: DatabaseConfiguration): DatabaseConfiguration = base.copy(
      dbFormat = dbFormatOp.getOrElse(base.dbFormat), host = hostOp.getOrElse(base.host),
      port = portOp.getOrElse(base.port), database = databaseOp.getOrElse(base.database))
    // For server-owned definitions. Untrusted request resolution additionally binds credentials to a destination.
    def completeWithEnvironment(): DatabaseConfiguration = completeWith(readFromEnv())
  }

  val defaultDb: PartialDatabaseInformation = PartialDatabaseInformation(
    Some("mysql"), Some("herrgreubel.lima-db.de"), Some("3306"), Some("db_332371_12"))

  def readFromEnv(dbFormat: Option[String] = None, dbName: Option[String] = None): DatabaseConfiguration =
    DatabaseConfiguration(dbFormat.orElse(Option(System.getenv("DB_FORMAT"))).getOrElse("mysql"),
      System.getenv("SQL_HOST"), System.getenv("SQL_PORT"), dbName.getOrElse(System.getenv("SQL_DATABASE")),
      System.getenv("SQL_USER"), System.getenv("SQL_PW"))
}
