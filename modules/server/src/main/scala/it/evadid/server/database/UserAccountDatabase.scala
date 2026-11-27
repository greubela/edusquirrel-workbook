package it.evadid.server.database

import it.evadid.util.logging.Logger
import java.sql.Connection
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

/** Owns one connection for a synchronous account operation and closes it before completing the Future. */
final class UserAccountDatabase(openConnection: () => Connection) {
  private given ExecutionContext = ExecutionContext.global

  def execute[A](logger: Logger)(operation: UserAccountRows => A): Future[A] = Future {
    SqlFailure("executing user account request", logger) {
      Using.resource(openConnection()) { connection =>
        operation(UserAccountRows(connection, logger))
      }
    }
  }
}
