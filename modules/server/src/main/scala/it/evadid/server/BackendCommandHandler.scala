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
import it.evadid.server.commandHandler.sql.{SqlLogCommands, SqlUserCommands}
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


  private def localHandlerRequiringLogin(userToken: SignedToken): ExecutionClient = ExecutionClientPool(List(
    localHandlerNoLoginRequired,

    // SQL
    SQLCommands.StoreToDbCommand.toLocalExecutionClient(
      (request: StoreToDbRequest, logger: Logger) => Future {
        UpsertToDatabase.handleRequest(request, logger)
      },
      (request: StoreToDbRequest, logger: Logger) => AuthHandling.mayAccessIdBased(userToken.info.user.id, request.syncContext.userId)

    ),

    SQLCommands.fetchFromDbCommand.toLocalExecutionClient(
      (request: FetchAllFromDbRequest, logger: Logger) => Future {
        FetchFromDatabase.handleRequest(request, logger)
      },
      (request: FetchAllFromDbRequest, logger: Logger) => AuthHandling.mayAccessIdBased(userToken.info.user.id, request.usageContext.userId)
    )
    ,
    SQLCommands.clearValuesDbCommand.toLocalExecutionClient(
      (request: DeleteInDbRequest, logger: Logger) => Future {
        DeleteInDatabase.handleRequest(request, logger)
      },
      (request: DeleteInDbRequest, logger: Logger) => AuthHandling.mayAccessIdBased(userToken.info.user.id, request.usageContext.userId)

    ),
    // Mail
    MailCommands.sendMailCommand.toLocalExecutionClient(
      (request: SendMailRequest, logger: Logger) => SendMailCommand.handleSendMailRequest(request, logger),
      (request: SendMailRequest, logger: Logger) => AuthHandling.mayAccessMailBased(userToken.info.user.mail, request.recipientMail)
    ),



    UserCommands.updateAccountCommand.toLocalExecutionClient(
      (request: UpdateAccountRequest, logger: Logger) => SqlUserCommands.handleUpdateAccountCommand(request, logger),
      (request: UpdateAccountRequest, logger: Logger) => AuthHandling.mayAccessIdBased(userToken.info.user.id, request.user.id),
    ),

  ))


  def handleExecution(commandReceived: LocalDateTime, executionCommand: ExecutionCommand, verifiedToken: Option[SignedToken], remoteAddress: InetAddress, logger: Logger): Future[ExecutionClientResponse] = {
    logger.logInfo(s"[server] Received command: ${executionCommand.name} with params keys: ${executionCommand.params.keys}")

    SqlLogCommands.handleLog(commandReceived, executionCommand, verifiedToken, remoteAddress, logger)

    if (executionCommand.name.trim.isEmpty) {
      logger.logError("ExecutionCommand.name must not be empty")
      throw new IllegalArgumentException("ExecutionCommand.name must not be empty")
    }

    if (verifiedToken.nonEmpty) {
      val user = verifiedToken.head.info.user
      logger.logInfo(s"User is authorized as user ${user.name} (${user.id} @ ${user.mail})")
      localHandlerRequiringLogin(verifiedToken.head).handleExecution(executionCommand, logger)
    } else if (localHandlerNoLoginRequired.canExecuteCommand(executionCommand)) {
      logger.logInfo(s"No authorized user found, only a subset of handlers are available!")
      localHandlerNoLoginRequired.handleExecution(executionCommand, logger)
    } else {
      throw new IllegalArgumentException(s"Functionality ${executionCommand.name} either does not exist or requires an account!")
    }

  }


}
