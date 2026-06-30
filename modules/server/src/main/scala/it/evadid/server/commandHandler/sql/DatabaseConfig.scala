package it.evadid.server.commandHandler.sql

import it.evadid.distribution.commandTypes.SQLCommands.DbRequest

import java.sql.{DriverManager, PreparedStatement}

private[server] final case class DatabaseConfig(
                                                 host: String,
                                                 port: String,
                                                 database: String,
                                                 user: String,
                                                 password: String
                                               ) {
  val jdbcUrl: String = s"jdbc:mysql://$host:$port/$database"

  def newConnection(): java.sql.Connection = {
    DriverManager.getConnection(jdbcUrl, user, password)
  }

  def prepareStatement(sql: String): PreparedStatement = {
    newConnection().prepareStatement(sql)
  }
}

object DatabaseConfig {

  def readFromEnv(dbName: String): DatabaseConfig = {
    DatabaseConfig(
      host = System.getenv("SQL_HOST"),
      port = System.getenv("SQL_PORT"),
      database = dbName,
      user = System.getenv("SQL_USER"),
      password = System.getenv("SQL_PW")
    )
  }


}
