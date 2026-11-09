package it.evadid.server.database

import it.evadid.server.commandHandler.sql.SqlUserCommands
import it.evadid.util.logging.Logger
import java.sql.Connection
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

/** Owns one connection for a synchronous account operation and closes it before completing the Future. */
final class UserAccountDatabase(openConnection: () => Connection) {
  private given ExecutionContext = ExecutionContext.global

  def execute[A](logger: Logger)(operation: SqlUserCommands => A): Future[A] = Future {
    Using.resource(openConnection()) { connection =>
      operation(SqlUserCommands(connection, logger))
    }
  }
}
