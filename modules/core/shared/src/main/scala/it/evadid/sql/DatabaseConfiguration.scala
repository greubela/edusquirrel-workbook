package it.evadid.sql

import upickle.default.*

sealed trait DatabaseConfiguration derives ReadWriter {
  def withoutCredentials: DatabaseConfiguration
}

/** Resolve the database exclusively from the receiving backend's environment. */
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
case class DatabaseConfig(dbFormat: String, host: String, port: String, database: String)
  extends DatabaseConnection derives ReadWriter

/** Transient user-supplied connection; never persist it or include it in logs. */
case class DatabaseConfigWithCredentials(dbFormat: String, host: String, port: String, database: String,
    user: String, password: String) extends DatabaseConnection derives ReadWriter {
  override def toString: String = s"DatabaseConfigWithCredentials($dbFormat,$host,$port,$database,credentials=redacted)"
}
