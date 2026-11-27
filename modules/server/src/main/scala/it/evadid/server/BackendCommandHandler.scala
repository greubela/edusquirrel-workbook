package it.evadid.server

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.clients.*
import it.evadid.distribution.command.*
import it.evadid.distribution.commandTypes.*
import it.evadid.distribution.commandTypes.LLMCommands.{FeedbackLlmRequest, MessengerChatCompletionRequest}
import it.evadid.distribution.commandTypes.MailCommands.SendMailRequest
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.distribution.commandTypes.UserCommands.{AuthMailRequest, LoginRequest, *}
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.server.commandHandler.sql.sync.{DeleteInDatabase, FetchFromDatabase, UpsertToDatabase}
import it.evadid.server.commandHandler.sql.{SqlEditorExecution, SqlLogCommands, SqlUserCommands}
import it.evadid.util.logging.Logger

import java.net.InetAddress
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

object BackendCommandHandler {

  private given ExecutionContext = ExecutionContext.global


  private val localHandlerNoLoginRequired: ExecutionClient = ExecutionClientPool(List(
    // LLM
    LLMCommands.completeLLMCommandFactory.toLocalExecutionClient(
      (request: MessengerChatCompletionRequest, logger: Logger) => CompleteChatWithLLMCommand.handleLlmChatRequest(request, logger)
    ),
    LLMCommands.feedbackLlmCommandFactory.toLocalExecutionClient(
      (request: FeedbackLlmRequest, logger: Logger) => CompleteChatWithLLMCommand.handleFeedbackLlmRequest(request, logger)
    ),

    // User
    UserCommands.loginCommand.toLocalExecutionClient(
      (request: LoginRequest, logger: Logger) => SqlUserCommands.handleLoginCommand(request, logger)
    ),
    UserCommands.createAccountCommand.toLocalExecutionClient(
      (request: CreateAccountRequest, logger: Logger) => SqlUserCommands.handleCreateAccountCommand(request, logger)
    ),

    UserCommands.authMailCommand.toLocalExecutionClient(
      (request: AuthMailRequest, logger: Logger) => SqlUserCommands.requestAuthMail(request, logger),
    )
  ))


  private def localHandlerRequiringLogin(userToken: SignedToken): ExecutionClient = {
    val tokenUser = userToken.info.user
    // Account-bound commands may only address the signed-in user's own records.
    def mayAccessUser(userId: String): Boolean = AuthHandling.mayAccessIdBased(tokenUser.id, userId)

    ExecutionClientPool(List(
      localHandlerNoLoginRequired,

      // SQL
      SqlEditorCommands.execute.toLocalExecutionClient(
        (request, logger) => Future { SqlEditorExecution.handle(request) }
      ),
      SQLCommands.StoreToDbCommand.toLocalExecutionClient(
        (request: StoreToDbRequest, logger: Logger) => Future { UpsertToDatabase.handleRequest(request, logger) },
        (request: StoreToDbRequest, _: Logger) => mayAccessUser(request.syncContext.userId)
      ),
      SQLCommands.bulkStoreToDbCommand.toLocalExecutionClient(
        (request: BulkStoreToDbRequest, logger: Logger) => Future { UpsertToDatabase.handleBulkRequest(request, logger) },
        (request: BulkStoreToDbRequest, _: Logger) => request.syncContexts.forall(context => mayAccessUser(context.userId))
      ),
      SQLCommands.fetchEventsFromDbCommand.toLocalExecutionClient(
        (request: FetchEventsFromDbRequest, logger: Logger) => Future { FetchFromDatabase.handleRequest(request, logger) },
        (request: FetchEventsFromDbRequest, _: Logger) => mayAccessUser(request.usageContext.userId)
      ),
      SQLCommands.resetUserHistoriesDbCommand.toLocalExecutionClient(
        (request: DeleteInDbRequest, logger: Logger) => Future {
          require(request.allWorkbooks, "A full reset requires the all-workbook scope.")
          DeleteInDatabase.handleRequest(request, logger)
        },
        (request: DeleteInDbRequest, _: Logger) => mayAccessUser(request.usageContext.userId)
      ),
      SQLCommands.clearValuesDbCommand.toLocalExecutionClient(
        (request: DeleteInDbRequest, logger: Logger) => Future { DeleteInDatabase.handleRequest(request, logger) },
        (request: DeleteInDbRequest, _: Logger) => mayAccessUser(request.usageContext.userId)
      ),

      // Mail
      MailCommands.sendMailCommand.toLocalExecutionClient(
        (request: SendMailRequest, logger: Logger) => SendMailCommand.handleSendMailRequest(request, logger),
        (request: SendMailRequest, _: Logger) => AuthHandling.mayAccessMailBased(tokenUser.mail, request.recipientMail)
      ),

      // User
      UserCommands.updateAccountCommand.toLocalExecutionClient(
        (request: UpdateAccountRequest, logger: Logger) => SqlUserCommands.handleUpdateAccountCommand(request, logger),
        (request: UpdateAccountRequest, _: Logger) => mayAccessUser(request.allUserInfo.user.id)
      )
    ))
  }


  private[server] def logReceivedCommand(command: ExecutionCommand, logger: Logger): Unit = {
    // Diagnostic output is also returned to the client. Never serialize request
    // values here: growing histories can otherwise flood both responses and CI logs.
    val fields = command.json.objOpt.fold(0)(_.size)
    logger.logInfo(s"[server] Received command: ${command.name.take(128)}; parameter count: $fields")
  }

  private[server] def validateFeedbackRequest(command: ExecutionCommand): Unit =
    if command.name == LLMCommands.feedbackLlmCommandFactory.name &&
      !command.json.objOpt.exists(fields => List("prompt", "systemPrompt")
        .forall(field => fields.get(field).flatMap(_.strOpt).exists(_ != null))) then
      throw IllegalArgumentException("Invalid feedback request.")

  def handleExecution(commandReceived: LocalDateTime, executionCommand: ExecutionCommand, verifiedToken: Option[SignedToken], remoteAddress: InetAddress, logger: Logger): Future[ExecutionClientResponse] = {
    if (executionCommand.name.trim.isEmpty) {
      val message = "ExecutionCommand.name must not be empty"
      logger.logError(message)
      throw new IllegalArgumentException(message)
    }

    logReceivedCommand(executionCommand, logger)
    validateFeedbackRequest(executionCommand)
    SqlLogCommands.handleLog(commandReceived, executionCommand, verifiedToken, remoteAddress, logger)

    verifiedToken match {
      case Some(token) =>
        val user = token.info.user
        logger.logInfo(s"User is authorized as user ${user.name} (${user.id} @ ${user.mail})")
        localHandlerRequiringLogin(token).executeResponse(logger, executionCommand).map(_.fixReceived(commandReceived))
      case None if localHandlerNoLoginRequired.canExecuteCommand(executionCommand) =>
        logger.logInfo(s"No authorized user found, only a subset of handlers are available!")
        localHandlerNoLoginRequired.executeResponse(logger, executionCommand).map(_.fixReceived(commandReceived))
      case None =>
        throw new IllegalArgumentException(s"Functionality ${executionCommand.name} either does not exist or requires an account!")
    }
  }


}
