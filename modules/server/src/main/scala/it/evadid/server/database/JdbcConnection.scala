package it.evadid.server.database

import it.evadid.sql.DatabaseConfigWithCredentials
import java.sql.{Connection, DriverManager}

/** Opens a new JDBC connection that the caller owns and closes. */
extension (config: DatabaseConfigWithCredentials)
  def newConnection(): Connection = DriverManager.getConnection(config.jdbcUrl, config.user, config.password)
