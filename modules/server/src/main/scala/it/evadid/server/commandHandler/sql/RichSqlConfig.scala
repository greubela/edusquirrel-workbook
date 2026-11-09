package it.evadid.server.commandHandler.sql

import it.evadid.sql.DatabaseConfigWithCredentials

import java.sql.DriverManager

implicit class RichDatabaseConfig(config: DatabaseConfigWithCredentials) {
  def newConnection(): java.sql.Connection = {
    DriverManager.getConnection(config.jdbcUrl, config.user, config.password)
  }

}
