package it.evadid.sql

import upickle.default.*

sealed trait DatabaseConfiguration derives ReadWriter {
  def withoutCredentials: DatabaseConfiguration
}

/** Resolve the database exclusively from the receiving backend's environment. */
@upickle.implicits.key("BackendDatabase")
case object BackendDatabase extends DatabaseConfiguration {
  def withoutCredentials: DatabaseConfiguration = this
}

sealed trait DatabaseConnection extends DatabaseConfiguration derives ReadWriter {
  def dbFormat: String
  def host: String
  def port: String
  def database: String
  final def jdbcUrl: String = s"jdbc:$dbFormat://$host:$port/$database"
  final def withoutCredentials: DatabaseConfig = DatabaseConfig(dbFormat, host, port, database)
}

/** A complete public destination whose credentials are resolved only by the server. */
@upickle.implicits.key("DatabaseConfig")
case class DatabaseConfig(dbFormat: String, host: String, port: String, database: String)
  extends DatabaseConnection derives ReadWriter

/** Transient user-supplied connection; never persist it or include it in logs. */
@upickle.implicits.key("DatabaseConfigWithCredentials")
case class DatabaseConfigWithCredentials(dbFormat: String, host: String, port: String, database: String,
    user: String, password: String) extends DatabaseConnection derives ReadWriter {
  override def toString: String = s"DatabaseConfigWithCredentials($dbFormat,$host,$port,$database,credentials=redacted)"
}

object DatabaseConfigWithCredentials {
  def readFromEnv(dbFormat: Option[String] = None, dbName: Option[String] = None): DatabaseConfigWithCredentials =
    DatabaseConfigWithCredentials(dbFormat.orElse(Option(System.getenv("DB_FORMAT"))).getOrElse("mysql"),
      System.getenv("SQL_HOST"), System.getenv("SQL_PORT"), dbName.getOrElse(System.getenv("SQL_DATABASE")),
      System.getenv("SQL_USER"), System.getenv("SQL_PW"))
}
