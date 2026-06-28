package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ai

import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.distribution.commandTypes.LLMCommands.FeedbackLlmRequest
import it.evadid.util.logging.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final class CommandLlmClient(executor: ExecutionClient) extends LlmClient {

  override def complete(prompt: String, systemPrompt: Option[String] = None): Future[String] =
    LLMCommands.feedbackLlmCommandFactory
      .sendCommandTo(executor, Logger(), FeedbackLlmRequest(prompt, systemPrompt.getOrElse("")))
      .map(_.resultTyped.result)

}
