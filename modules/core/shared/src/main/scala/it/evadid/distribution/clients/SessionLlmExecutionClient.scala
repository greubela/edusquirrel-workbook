package it.evadid.distribution.clients

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.llm.{LlmConfiguration, LlmPartialConfiguration}
import it.evadid.util.logging.Logger
import scala.concurrent.Future
import upickle.default.*

/** Adds session credentials immediately before transport, never to saved preferences. */
case class SessionLlmExecutionClient(underlying: ExecutionClient, session: () => Option[LlmConfiguration]) extends ExecutionClient {
  override def canExecuteCommand(command: ExecutionCommand): Boolean = underlying.canExecuteCommand(command)
  override private[clients] def handleCommandExecution(logger: Logger, command: ExecutionCommand): Future[ExecutionClientResponse] = {
    val names = Set(LLMCommands.completeLLMCommandFactory.name, LLMCommands.feedbackLlmCommandFactory.name)
    val current = session()
    val outgoing = if !names(command.name) || current.isEmpty then command else {
      val defaults = current.get
      val provided = command.json.obj.get("llmConfiguration").map(read[LlmPartialConfiguration](_)).getOrElse(LlmPartialConfiguration())
      val endpoint = provided.endpoint.getOrElse(defaults.endpoint)
      val configuration = LlmPartialConfiguration(Some(endpoint), provided.model.orElse(Some(defaults.model)),
        provided.apiKey.orElse(Option.when(endpoint == defaults.endpoint)(defaults.apiKey).flatten), provided.maxTokens.orElse(defaults.maxTokens))
      command.copy(json = ujson.Obj.from(command.json.obj.toList.filterNot(_._1 == "llmConfiguration") :+ ("llmConfiguration" -> writeJs(configuration))))
    }
    underlying.executeResponse(logger, outgoing)
  }
}
