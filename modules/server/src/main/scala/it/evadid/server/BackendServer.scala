package it.evadid.server

import it.evadid.server.account.SignedTokens
import it.evadid.server.commands.BackendCommandHandler
import it.evadid.server.database.schema.SchemaValidation
import it.evadid.server.http.HttpApi
import it.evadid.sql.{DatabaseConfigWithCredentials, DatabaseHistoryFormat}
import play.api.mvc.*
import play.api.routing.Router
import play.api.{BuiltInComponents, NoHttpFiltersComponents}
import play.core.server.{NettyServerComponents, Server, ServerConfig as PlayServerConfig}

import java.sql.SQLException
import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

/** Entry point: reads the [[ServerConfig]], validates the backend database and serves the [[HttpApi]]. */
object BackendServer {

  /** The command handler and routes of one server; tests build them from their own configuration. */
  private[server] def routes(config: ServerConfig, schemas: SchemaValidation = SchemaValidation())
      : (DefaultActionBuilder, PlayBodyParsers) => PartialFunction[RequestHeader, Handler] =
    HttpApi(config, BackendCommandHandler(config, SignedTokens(config.tokenSecret), schemas)).routes

  private[server] def createServer(config: PlayServerConfig)(routes: (DefaultActionBuilder, PlayBodyParsers) => PartialFunction[RequestHeader, Handler]): Server =
    new NettyServerComponents with BuiltInComponents with NoHttpFiltersComponents {
      override lazy val serverConfig: PlayServerConfig = config
      override lazy val configuration: play.api.Configuration = config.configuration

      override def router: Router = Router.from(routes(defaultActionBuilder, playBodyParsers))
    }.server

  /** Refuses to start on an incompatible backend schema; an unreachable or unconfigured database only warns, since
    * it may become available later and every request validates its database again until it succeeds.
    */
  private[server] def checkBackendDatabase(database: Option[DatabaseConfigWithCredentials], schemas: SchemaValidation): Unit =
    database match {
      case None => println("[server] WARNING: no backend database is configured; accounts, histories and command logs are unavailable.")
      case Some(configured) =>
        val requirements = (Set.empty[String] +: DatabaseHistoryFormat.all.map(_.tables.map(_.name).toSet))
          .map(SchemaValidation.Requirement(configured, _))
        requirements.foldLeft[Try[Unit]](Success(()))((checked, requirement) =>
          checked.flatMap(_ => Try(Await.result(schemas.validate(requirement), 60.seconds)).map(_ => ()))) match {
          case Success(_) => println(s"[server] Backend database ${configured.withoutCredentials} has the expected schema.")
          case Failure(unreachable: SQLException) if Option(unreachable.getSQLState).exists(_.startsWith("08")) =>
            println(s"[server] WARNING: the backend database is unreachable at startup: ${unreachable.getMessage}")
          case Failure(error) => throw IllegalStateException(s"Backend database check failed: ${error.getMessage}", error)
        }
    }

  def main(args: Array[String]): Unit = {
    val config = ServerConfig.fromEnvironment()
    println(s"[server] Booting Play HTTP server on ${config.hostname}:${config.port} ...")
    val schemas = SchemaValidation()
    checkBackendDatabase(config.database, schemas)
    val server = createServer(PlayServerConfig(port = Some(config.port), address = "0.0.0.0"))(routes(config, schemas))
    println(s"[server] Ready and serving requests at http://${config.hostname}:${config.port}")

    Runtime.getRuntime.addShutdownHook(Thread(() => {
      println("[server] Shutdown requested. Stopping Play HTTP server...")
      server.stop()
      println("[server] Server stopped.")
    }))

    Thread.currentThread().join()
  }
}
