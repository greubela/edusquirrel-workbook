package it.evadid.server

import it.evadid.core.datastructures.state.async.AsyncData
import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataStateFinished
import it.evadid.distribution.clients.*
import it.evadid.distribution.command.*
import it.evadid.distribution.commandTypes.LLMCommands.{FeedbackLlmRequest, MessengerChatCompletionRequest}
import it.evadid.distribution.commandTypes.MailCommands.SendMailRequest
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.distribution.commandTypes.*
import it.evadid.util.*

import scala.concurrent.{ExecutionContext, Future}

object BackendCommandHandler {

  private given ExecutionContext = ExecutionContext.global

  private val localHandler: ExecutionClient = ExecutionClientPool(List(
    LLMCommands.completeLLMCommandFactory.toExecutionClient(
      (request: MessengerChatCompletionRequest, logger: Logger) => CompleteChatWithLLMCommand.handleLlmChatRequest(request, logger)
    ),
    LLMCommands.feedbackLlmCommandFactory.toExecutionClient(
      (request: FeedbackLlmRequest, logger: Logger) => CompleteChatWithLLMCommand.handleFeedbackLlmRequest(request, logger)
    ),
    SQLCommands.syncToDbCommand.toExecutionClient(
      (request: StoreToDbRequest, logger: Logger) => Future{
        HandleSQLCommand.handleStoreToDbRequest(request, logger)
      }
    ),
    SQLCommands.fetchFromDbCommand.toExecutionClient(
      (request: FetchFromDbRequest, logger: Logger) => Future {
        HandleSQLCommand.fetchAll(request, logger)
      })
    ,
    SQLCommands.clearValuesDbCommand.toExecutionClient(
      (request: ClearUsageInDbRequest, logger: Logger) => Future{
        HandleSQLCommand.clearUsage(request, logger)
      }
    ),
    MailCommands.sendMailCommand.toExecutionClient(
      (request: SendMailRequest, logger: Logger) => SendMailCommand.handleSendMailRequest(request, logger)
    )
  )
  )

  def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[AsyncDataStateFinished[Nothing, ExecutionResult]] = {
    logger.logInfo(s"[server] Received command: ${executionCommand.name} with params keys: ${executionCommand.params.keys}")
    if (executionCommand.name.trim.isEmpty) {
      logger.logError("ExecutionCommand.name must not be empty")
      throw new IllegalArgumentException("ExecutionCommand.name must not be empty")
    }
    localHandler.handleExecution(executionCommand, logger)
  }


}
