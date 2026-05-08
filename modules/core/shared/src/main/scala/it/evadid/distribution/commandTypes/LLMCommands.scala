package it.evadid.distribution.commandTypes

import it.evadid.core.datastructures.chat.MessengerModel.*
import it.evadid.core.datastructures.chat.*
import it.evadid.distribution.*
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionCommandFactory
import upickle.ReadWriter
import upickle.default.{macroRW, readwriter}

object LLMCommands {


  case class MessengerChatCompletionResponse(newTextGenerated: String)

  case class MessengerChatCompletionRequest(systemPrompt: String, messengerModel: MessengerModel)


  val completeLLMCommandFactory: ExecutionCommandFactory[MessengerChatCompletionRequest, MessengerChatCompletionResponse, MessengerModel] = ???
  /*ExecutionCommandFactory(
    "complete-llm-request",
    TypeConv
  )*/


}
