package it.evadid.distribution.clients

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.logging.Logger
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** A backend reached at `<backendDomain>:<port>/executeCommand`; a domain without a scheme uses HTTPS. */
case class RemoteExecutionConfig(backendDomain: String, port: Int) {

  def executor(token: Option[SignedToken]): ExecutionClient = {
    JsRemoteExecutionClient(backendDomain, port, token)
  }

}

object RemoteExecutionConfig {

  /** An `http(s)://host[:port]` origin without path, query, fragment or credentials. */
  def fromUrl(url: String): RemoteExecutionConfig = {
    val parsed = Try(new dom.URL(url.trim)).getOrElse(throw IllegalArgumentException(s"Invalid backend URL '$url'."))
    require(parsed.protocol == "http:" || parsed.protocol == "https:", s"The backend URL '$url' must use http or https.")
    require(parsed.pathname == "/" && parsed.search.isEmpty && parsed.hash.isEmpty && parsed.username.isEmpty && parsed.password.isEmpty,
      s"The backend URL '$url' must name only scheme, host and port.")
    val port = parsed.port.toIntOption.getOrElse(if parsed.protocol == "https:" then 443 else 80)
    RemoteExecutionConfig(s"${parsed.protocol}//${parsed.hostname}", port)
  }

  /** A client for a backend known only once `config` completes, for example after the page's runtime configuration
    * has loaded. Each command waits for it; a failed configuration fails every command with its reason.
    */
  def deferredExecutor(config: Future[RemoteExecutionConfig], token: Option[SignedToken]): ExecutionClient =
    DeferredExecutionClient(config.map(_.executor(token))(using ExecutionContext.global))

  private case class DeferredExecutionClient(client: Future[ExecutionClient]) extends ExecutionClient {
    override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = true

    override private[clients] def handleCommandExecution(logger: Logger, executionCommand: ExecutionCommand): Future[ExecutionClientResponse] =
      client.flatMap(_.handleCommandExecution(logger, executionCommand))(using ExecutionContext.global)
  }
}
