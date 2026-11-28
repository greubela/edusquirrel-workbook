package it.evadid.server.commands

import it.evadid.core.datastructures.user.User
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.util.logging.Logger
import java.net.InetAddress
import java.time.Instant
import scala.concurrent.Future

/** Who may run a command. Every backend handler states its rule; there is no permissive default. */
sealed trait CommandAccess[-I] {
  /** Whether `user` (absent without a verified token) may run `request`. */
  def allows(request: I, user: Option[User]): Boolean
}

object CommandAccess {
  /** Runs without an account, e.g. login, registration and submissions. */
  case object Public extends CommandAccess[Any] {
    def allows(request: Any, user: Option[User]): Boolean = true
  }

  /** Any verified account, e.g. the SQL editor on shared teaching databases. */
  case object SignedIn extends CommandAccess[Any] {
    def allows(request: Any, user: Option[User]): Boolean = user.nonEmpty
  }

  /** Only the account whose ID owns every addressed record. */
  final case class OwnRecords[I](owners: I => Seq[String]) extends CommandAccess[I] {
    def allows(request: I, user: Option[User]): Boolean = user.exists(signedIn => owners(request).forall(_ == signedIn.id))
  }

  /** Only the account whose mail address is the request's. */
  final case class OwnMail[I](mail: I => String) extends CommandAccess[I] {
    def allows(request: I, user: Option[User]): Boolean = user.exists(_.mail == mail(request))
  }

  /** The request's arrival: when and from where it was received, and the verified account, if any. */
  final case class RequestContext(received: Instant, remoteAddress: InetAddress, user: Option[User])

  /** A command, its access rule and its handler. A handler without login is offered to anonymous requests. */
  final case class Handler[I, O](factory: ExecutionCommandFactory[I, O], access: CommandAccess[I],
                                 handle: (I, RequestContext, Logger) => Future[O]) {
    require(factory.requiresLogin == (access != Public),
      s"${factory.name}: the access rule must match the command's requiresLogin declaration")

    def availableTo(context: RequestContext): Boolean = access == Public || context.user.nonEmpty

    def client(context: RequestContext): ExecutionClient = factory.toLocalExecutionClient(
      (request, logger) => handle(request, context, logger), (request, _) => access.allows(request, context.user))
  }
}
