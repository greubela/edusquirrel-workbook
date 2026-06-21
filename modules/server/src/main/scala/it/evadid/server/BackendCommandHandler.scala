package it.evadid.server

import it.evadid.distribution.clients.*
import it.evadid.distribution.commandTypes.{LLMCommands, MailCommands, SQLCommands}
import it.evadid.distribution.commandTypes.LLMCommands.{FeedbackLlmRequest, MessengerChatCompletionRequest}
import it.evadid.distribution.commandTypes.SQLCommands.SyncToDbRequest
import it.evadid.distribution.commandTypes.MailCommands.SendMailRequest
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
    ),
    MailCommands.sendMailCommand.toExecutionClient(
      (request: SendMailRequest, logger: Logger) => SendMailCommand.handleSendMailRequest(request, logger)
    )
  ))

  def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionInfo] = {
    logger.logInfo(s"[server] Received command: ${executionCommand.name} with params keys: ${executionCommand.params.keys}")
    if (executionCommand.name.trim.isEmpty) {
      logger.logError("ExecutionCommand.name must not be empty")
      throw new IllegalArgumentException("ExecutionCommand.name must not be empty")
    }
    localHandler.handleExecution(executionCommand, logger)
  }


}
