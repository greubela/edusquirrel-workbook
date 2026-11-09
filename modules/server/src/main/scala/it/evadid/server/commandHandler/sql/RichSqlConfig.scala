package it.evadid.server.commandHandler.sql

import it.evadid.sql.DatabaseConfiguration

import java.sql.{DriverManager, PreparedStatement}

implicit class RichDatabaseConfig(config: DatabaseConfiguration) {
  def newConnection(): java.sql.Connection = {
    DriverManager.getConnection(config.jdbcUrl, config.user, config.password)
  }

  def prepareStatement(sql: String): PreparedStatement = {
    newConnection().prepareStatement(sql)
  }
}