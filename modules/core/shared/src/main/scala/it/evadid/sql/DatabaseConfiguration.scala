package it.evadid.sql

import upickle.default.*

sealed trait DatabaseConfiguration derives ReadWriter {
  def dbFormat: String
  def host: String
  def port: String
  def database: String
  final def jdbcUrl: String = s"jdbc:$dbFormat://$host:$port/$database"
  final def withoutCredentials: DatabaseConfig = DatabaseConfig(dbFormat, host, port, database)
}

/** A complete public destination whose credentials are resolved only by the server. */
case class DatabaseConfig(dbFormat: String, host: String, port: String, database: String)
  extends DatabaseConfiguration derives ReadWriter

object DatabaseConfig {
  val defaultDb: DatabaseConfig = DatabaseConfig("mysql", "herrgreubel.lima-db.de", "3306", "db_332371_12")
}

/** Transient user-supplied connection; never persist it or include it in logs. */
case class DatabaseConfigWithCredentials(dbFormat: String, host: String, port: String, database: String,
    user: String, password: String) extends DatabaseConfiguration derives ReadWriter {
  override def toString: String = s"DatabaseConfigWithCredentials($dbFormat,$host,$port,$database,credentials=redacted)"
}

object DatabaseConfigWithCredentials {
  def readFromEnv(dbFormat: Option[String] = None, dbName: Option[String] = None): DatabaseConfigWithCredentials =
    DatabaseConfigWithCredentials(dbFormat.orElse(Option(System.getenv("DB_FORMAT"))).getOrElse("mysql"),
      System.getenv("SQL_HOST"), System.getenv("SQL_PORT"), dbName.getOrElse(System.getenv("SQL_DATABASE")),
      System.getenv("SQL_USER"), System.getenv("SQL_PW"))
}
