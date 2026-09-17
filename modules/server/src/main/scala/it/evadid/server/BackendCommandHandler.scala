package it.evadid.server

import it.evadid.core.datastructures.user.User.UserToken
import it.evadid.distribution.clients.*
import it.evadid.distribution.command.*
import it.evadid.distribution.commandTypes
import it.evadid.distribution.commandTypes.*
import it.evadid.distribution.commandTypes.LLMCommands.{FeedbackLlmRequest, MessengerChatCompletionRequest}
import it.evadid.distribution.commandTypes.MailCommands.SendMailRequest
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.distribution.commandTypes.UserCommands.{AuthMailRequest, LoginRequest, LoginResponse, UpsertAccountRequest}
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.server.commandHandler.sql.sync.{DeleteInDatabase, FetchFromDatabase, UpsertToDatabase}
import it.evadid.server.commandHandler.sql.{DatabaseConfig, SqlLogCommands, SqlUserCommands}
import it.evadid.util.logging.Logger

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

object BackendCommandHandler {

  private given ExecutionContext = ExecutionContext.global

  private val localHandler: ExecutionClient = ExecutionClientPool(List(
    // LLM
    LLMCommands.completeLLMCommandFactory.toLocalExecutionClient(
      (request: MessengerChatCompletionRequest, logger: Logger) => CompleteChatWithLLMCommand.handleLlmChatRequest(request, logger)
    ),
    LLMCommands.feedbackLlmCommandFactory.toLocalExecutionClient(
      (request: FeedbackLlmRequest, logger: Logger) => CompleteChatWithLLMCommand.handleFeedbackLlmRequest(request, logger)
    //  (request: FeedbackLlmRequest, logger: Logger) =>         isProvidedUserTokenValid(request.user.id, request.userToken, logger)

    ),

    // SQL
    SQLCommands.StoreToDbCommand.toLocalExecutionClient(
      (request: StoreToDbRequest, logger: Logger) => Future {
        UpsertToDatabase.handleRequest(request, logger)
      },
      (request: StoreToDbRequest, logger: Logger) => {
        isProvidedUserTokenValid(request.usageContext.userId, request.userToken, logger)
      }
    ),

    SQLCommands.fetchFromDbCommand.toLocalExecutionClient(
      (request: FetchAllFromDbRequest, logger: Logger) => Future {
        FetchFromDatabase.handleRequest(request, logger)
      },
      (request: FetchAllFromDbRequest, logger: Logger) => {
        isProvidedUserTokenValid(request.usageContext.userId, request.userToken, logger)
      }

    )
    ,
    SQLCommands.clearValuesDbCommand.toLocalExecutionClient(
      (request: DeleteInDbRequest, logger: Logger) => Future {
        DeleteInDatabase.handleRequest(request, logger)
      },
      (request: DeleteInDbRequest, logger: Logger) => {
        isProvidedUserTokenValid(request.usageContext.userId, request.userToken, logger)
      }
    ),
    // Mail
    MailCommands.sendMailCommand.toLocalExecutionClient(
      (request: SendMailRequest, logger: Logger) => SendMailCommand.handleSendMailRequest(request, logger)
    ),


    // User
    UserCommands.loginCommand.toLocalExecutionClient(
      (request: LoginRequest, logger: Logger) => SqlUserCommands.handleLoginCommand(request, logger)
    ),

    UserCommands.upsertAccountCommand.toLocalExecutionClient(
      (request: UpsertAccountRequest, logger: Logger) => SqlUserCommands.handleUpsertAccountCommand(request, logger)
    ),

    UserCommands.authMailCommand.toLocalExecutionClient(
      (request: AuthMailRequest, logger: Logger) => SqlUserCommands.requestAuthMail(request, logger)
    )

  ))


  def isProvidedUserTokenValid(userId: String, providedToken: Option[UserToken], logger: Logger): Boolean = {
    if(providedToken.isEmpty) false
    else {
      val connection = DatabaseConfig.readFromEnv().newConnection()
      SqlUserCommands(connection, logger).requestLogin(LoginRequest(userId, providedToken.get.token)).loginSucceeded
    }
  }

  def handleExecution(commandReceived: LocalDateTime, executionCommand: ExecutionCommand, remoteAddress: String, logger: Logger): Future[ExecutionClientResponse] = {
    logger.logInfo(s"[server] Received command: ${executionCommand.name} with params keys: ${executionCommand.params.keys}")
    SqlLogCommands.handleLog(commandReceived, executionCommand, remoteAddress, logger)

    if (executionCommand.name.trim.isEmpty) {
      logger.logError("ExecutionCommand.name must not be empty")
      throw new IllegalArgumentException("ExecutionCommand.name must not be empty")
    }

    localHandler.handleExecution(executionCommand, logger)

  }


}
