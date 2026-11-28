package it.evadid.server.commands

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.clients.ExecutionClientPool
import it.evadid.distribution.command.{ExecutionCommand, ExecutionCommandFactory}
import it.evadid.distribution.commandTypes.*
import it.evadid.distribution.commandTypes.SQLCommands.DbRequest
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.server.account.{AccountCommands, SignedTokens}
import it.evadid.server.database.history.{HistoryDatabase, HistorySql}
import it.evadid.server.database.schema.SchemaValidation
import it.evadid.server.events.llm.LlmCompletion
import it.evadid.server.events.mail.MailSender
import it.evadid.server.events.submission.SessionSubmission
import it.evadid.server.sqlEditor.SqlEditorExecution
import it.evadid.server.{BlockingExecution, ServerConfig}
import it.evadid.util.logging.Logger

import java.net.InetAddress
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/** Routes execution commands to their handlers under each handler's [[CommandAccess]] rule. */
final class BackendCommandHandler(config: ServerConfig, val tokens: SignedTokens, val schemas: SchemaValidation) {
  import BackendCommandHandler.*
  import CommandAccess.*

  private val mail = MailSender(config.mail)
  private val accounts = AccountCommands(config.backendDatabase, tokens, mail)
  private val llm = LlmCompletion(config.llm)
  private val submissions = SessionSubmission(config.archiveRecipient, mail)
  private val sqlEditor = SqlEditorExecution(config.sqlEditor, config.database)
  private val histories = HistoryDatabase(config.backendDatabase, schemas)
  private val commandLog = CommandLogWriter(config.database)

  private def history[I <: DbRequest, O](factory: ExecutionCommandFactory[I, O])(operation: (HistorySql, I) => O): Handler[I, O] =
    Handler(factory, OwnRecords(_.ownerUserIds), (request, _, logger) =>
      BlockingExecution(histories.run(request, logger)(operation(_, request))))

  private val handlers: Seq[Handler[?, ?]] = Seq(
    Handler(LLMCommands.completeLLMCommandFactory, Public, (request, _, _) => llm.handleLlmChatRequest(request)),
    Handler(LLMCommands.feedbackLlmCommandFactory, Public, (request, _, _) => llm.handleFeedbackLlmRequest(request)),
    Handler(UserCommands.loginCommand, Public, (request, _, logger) => accounts.handleLoginCommand(request, logger)),
    Handler(UserCommands.createAccountCommand, Public, (request, _, logger) => accounts.handleCreateAccountCommand(request, logger)),
    Handler(UserCommands.authMailCommand, Public, (request, _, logger) => accounts.requestAuthMail(request, logger)),
    Handler(SubmissionCommands.submitSessionCommand, Public, (event, context, logger) =>
      submissions.handle(event, context.remoteAddress, context.received, logger)),

    Handler(SqlEditorCommands.execute, SignedIn, (request, _, _) => BlockingExecution(sqlEditor.handle(request))),
    history(SQLCommands.StoreToDbCommand)(_.store(_)),
    history(SQLCommands.bulkStoreToDbCommand)(_.storeAll(_)),
    history(SQLCommands.fetchEventsFromDbCommand)(_.fetch(_)),
    history(SQLCommands.resetUserHistoriesDbCommand) { (storage, request) =>
      require(request.allWorkbooks, "A full reset requires the all-workbook scope.")
      storage.delete(request)
    },
    history(SQLCommands.clearValuesDbCommand)(_.delete(_)),
    Handler(MailCommands.sendMailCommand, OwnMail(_.recipientMail), (request, _, logger) => mail.handle(request, logger)),
    Handler(UserCommands.updateAccountCommand, OwnRecords(request => Seq(request.allUserInfo.user.id)),
      (request, _, logger) => accounts.handleUpdateAccountCommand(request, logger))
  )

  /** Fails, rather than throws, for an unnamed command or one that needs an account the request does not have. */
  def handleExecution(commandReceived: Instant, executionCommand: ExecutionCommand, verifiedToken: Option[SignedToken],
                      remoteAddress: InetAddress, logger: Logger): Future[ExecutionClientResponse] = Future.delegate {
    require(executionCommand.name.trim.nonEmpty, "ExecutionCommand.name must not be empty")
    logReceivedCommand(executionCommand, logger)
    val context = RequestContext(commandReceived, remoteAddress, verifiedToken.map(_.info.user))
    commandLog.record(executionCommand, context, logger)
    context.user match {
      case Some(user) => logger.logInfo(s"User is authorized as user ${user.name} (${user.id} @ ${user.mail})")
      case None => logger.logInfo("No authorized user found, only a subset of handlers are available!")
    }
    val pool = ExecutionClientPool(handlers.filter(_.availableTo(context)).map(_.client(context)).toList)
    require(pool.canExecuteCommand(executionCommand),
      s"Functionality ${executionCommand.name} either does not exist or requires an account!")
    pool.executeResponse(logger, executionCommand).map(_.fixReceived(commandReceived))(using ExecutionContext.global)
  }(using ExecutionContext.global)
}

object BackendCommandHandler {
  private[server] def logReceivedCommand(command: ExecutionCommand, logger: Logger): Unit = {
    // Diagnostic output is also returned to the client. Never serialize request
    // values here: growing histories can otherwise flood both responses and CI logs.
    val fields = command.json.objOpt.fold(0)(_.size)
    logger.logInfo(s"[server] Received command: ${command.name.take(128)}; parameter count: $fields")
  }
}
