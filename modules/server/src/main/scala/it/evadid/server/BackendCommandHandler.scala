package it.evadid.server

import it.evadid.distribution.clients.*
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.distribution.commandTypes.LLMCommands.{MessengerChatCompletionRequest, MessengerChatCompletionResponse}
import it.evadid.executors.MathExecutor
import it.evadid.util.*
import it.evadid.distribution.command.*

import scala.concurrent.Future

object BackendCommandHandler {


  private val localHandler: ExecutionClient = ExecutionClientPool(List(

    LLMCommands.completeLLMCommandFactory.toExecutionClient(
      
      (request: MessengerChatCompletionRequest, logger: Logger) => CompleteChatWithLLMCommand.handleLlmChatRequest(request, logger)
      
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
