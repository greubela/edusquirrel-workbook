package it.evadid.server

import it.evadid.distribution.clients.*
import it.evadid.distribution.commandTypes.{LLMCommands, SQLCommands}
import it.evadid.distribution.commandTypes.LLMCommands.{FeedbackLlmRequest, MessengerChatCompletionRequest}
import it.evadid.distribution.commandTypes.SQLCommands.SyncToDbRequest
import it.evadid.util.*
import it.evadid.distribution.command.*

import scala.concurrent.Future

object BackendCommandHandler {


  private val localHandler: ExecutionClient = ExecutionClientPool(List(
    LLMCommands.completeLLMCommandFactory.toExecutionClient(
      (request: MessengerChatCompletionRequest, logger: Logger) => CompleteChatWithLLMCommand.handleLlmChatRequest(request, logger)
    ),
    LLMCommands.feedbackLlmCommandFactory.toExecutionClient(
      (request: FeedbackLlmRequest, logger: Logger) => CompleteChatWithLLMCommand.handleFeedbackLlmRequest(request, logger)
    ),
    SQLCommands.syncToDbCommand.toExecutionClient(
      (request: SyncToDbRequest, logger: Logger) => HandleSQLCommand.handleSyncToDbRequest(request, logger)
    )
  ))

  def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionInfo] = {
    logger.logInfo(s"[server] Received command: ${executionCommand.name} with params: ${executionCommand.params}")
    if (executionCommand.name.trim.isEmpty) {
      logger.logError("ExecutionCommand.name must not be empty")
      throw new IllegalArgumentException("ExecutionCommand.name must not be empty")
    }
    localHandler.handleExecution(executionCommand, logger)
  }


}
