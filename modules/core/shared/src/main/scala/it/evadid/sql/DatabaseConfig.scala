package it.evadid.sql

import it.evadid.sql.DatabaseConfig.DatabaseConfigPartialInformation
import upickle.default.*


case class DatabaseConfig(
                           dbFormat: String, //mysql
                           host: String,
                           port: String,
                           database: String,
                           user: String,
                           password: String
                         ) extends DatabaseConfigPartialInformation derives ReadWriter {
  lazy val jdbcUrl: String = s"jdbc:${dbFormat}://$host:$port/$database"
  /*
    def newConnection(): java.sql.Connection = {
      DriverManager.getConnection(jdbcUrl, user, password)
    }

    def prepareStatement(sql: String): PreparedStatement = {
      newConnection().prepareStatement(sql)
    }*/

  override def dbFormatOp: Option[String] = Some(dbFormat)

  override def hostOp: Option[String] = Some(host)

  override def portOp: Option[String] = Some(port)

  override def databaseOp: Option[String] = Some(database)

  override def userOp: Option[String] = Some(user)

  override def passwordOp: Option[String] = Some(password)
}

object DatabaseConfig {

  case class DatabaseConfigPartialImpl(dbFormatOp: Option[String], hostOp: Option[String], portOp: Option[String], databaseOp: Option[String], userOp: Option[String], passwordOp: Option[String]) extends DatabaseConfigPartialInformation

  object DatabaseConfigPartialInformation {

    def apply(
               dbFormatOp: Option[String] = None,
               hostOp: Option[String] = None,
               portOp: Option[String] = None,
               databaseOp: Option[String] = None,
               userOp: Option[String] = None,
               passwordOp: Option[String] = None
             ): DatabaseConfigPartialInformation = DatabaseConfigPartialImpl(dbFormatOp, hostOp, portOp, databaseOp, userOp, passwordOp)
  }

  sealed trait DatabaseConfigPartialInformation derives ReadWriter {
    def dbFormatOp: Option[String]

    def hostOp: Option[String]

    def portOp: Option[String]

    def databaseOp: Option[String]

    def userOp: Option[String]

    def passwordOp: Option[String]

    def completeWithEnvironment(): DatabaseConfig = DatabaseConfig(
      dbFormatOp.getOrElse(Option(System.getenv("DB_FORMAT")).getOrElse("mysql")),
      hostOp.getOrElse(System.getenv("SQL_HOST")),
      portOp.getOrElse(System.getenv("SQL_PORT")),
      databaseOp.getOrElse(System.getenv("SQL_DATABASE")),
      userOp.getOrElse(System.getenv("SQL_USER")),
      passwordOp.getOrElse(System.getenv("SQL_PW"))
    )

  }


  def readFromEnv(dbFormat: Option[String] = Some("mysql"), dbName: Option[String] = None): DatabaseConfig = {
    DatabaseConfig(
      dbFormat = dbFormat.getOrElse(System.getenv("DB_FORMAT")),
      host = System.getenv("SQL_HOST"),
      port = System.getenv("SQL_PORT"),
      database = dbName.getOrElse(System.getenv("SQL_DATABASE")),
      user = System.getenv("SQL_USER"),
      password = System.getenv("SQL_PW")
    )
  }


}
