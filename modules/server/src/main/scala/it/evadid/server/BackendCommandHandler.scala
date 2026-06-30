package it.evadid.server

import it.evadid.core.datastructures.state.async.AsyncData
import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataStateFinished
import it.evadid.distribution.clients.*
import it.evadid.distribution.command.*
import it.evadid.distribution.commandTypes.LLMCommands.{FeedbackLlmRequest, MessengerChatCompletionRequest}
import it.evadid.distribution.commandTypes.MailCommands.SendMailRequest
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.distribution.commandTypes.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.server.commandHandler.sql.{DeleteInDatabase, FetchFromDatabase, UpsertToDatabase}
import it.evadid.util.*
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
    ),
    // SQL
    SQLCommands.StoreToDbCommand.toLocalExecutionClient(
      (request: StoreToDbRequest, logger: Logger) => Future{
        UpsertToDatabase.handleRequest(request, logger)
      }
    ),
    SQLCommands.fetchFromDbCommand.toLocalExecutionClient(
      (request: FetchAllFromDbRequest, logger: Logger) => Future {
        FetchFromDatabase.handleRequest(request, logger)
      })
    ,
    SQLCommands.clearValuesDbCommand.toLocalExecutionClient(
      (request: DeleteInDbRequest, logger: Logger) => Future{
        DeleteInDatabase.handleRequest(request, logger)
      }
    ),
    // Mail
    MailCommands.sendMailCommand.toLocalExecutionClient(
      (request: SendMailRequest, logger: Logger) => SendMailCommand.handleSendMailRequest(request, logger)
    )
  )
  )

  def handleExecution(commandReceived: LocalDateTime, executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionClientResponse] = {
    logger.logInfo(s"[server] Received command: ${executionCommand.name} with params keys: ${executionCommand.params.keys}")
    if (executionCommand.name.trim.isEmpty) {
      logger.logError("ExecutionCommand.name must not be empty")
      throw new IllegalArgumentException("ExecutionCommand.name must not be empty")
    }
    localHandler.handleExecution(executionCommand, logger)
  }


}
