package interactionPlugins.gpt

import com.raquo.airstream.core.Observer
import com.raquo.laminar.api.L.{Var, unsafeWindowOwner}
import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.chat.MessengerModel.{BasicPerson, Message, Person, SenderRole}
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.distribution.ExecutionCommand
import it.evadid.distribution.clients.ExecuteOnRemoteServer
import upickle.default.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

case class AccessLLM(serverAccess: String) {
  import AccessLLM.*

  def sendRequest(chatRequest: ChatRequest): LlmResponse = {
    val responseVar = Var(
      LlmResponseContent(
        generatedText = "",
        errorMsg = None,
        finishedGeneration = false,
        lastChangeTimestampMillis = System.currentTimeMillis()
      )
    )

    val response = LlmResponse(
      chatRequest = chatRequest,
      responseVar = responseVar
    )

    sendRequestOverExecutionServer(chatRequest, responseVar)
    response
  }

  private def sendRequestOverExecutionServer(chatRequest: ChatRequest, responseVar: Var[LlmResponseContent]): Unit = {
    try {
      val parsedUrl = new java.net.URI(serverAccess)
      val host = parsedUrl.getHost
      val port = if (parsedUrl.getPort > 0) parsedUrl.getPort else 443
      val client = ExecuteOnRemoteServer(host, port)

      val command = ExecutionCommand(
        name = "llm_chat",
        params = Map(
          "systemPrompt" -> chatRequest.systemPrompt,
          "messengerModel" -> write(chatRequest.messengerModel)
        )
      )

      client.executeCommand(command).map { executionInfo =>
        executionInfo.result.fold(
          failure => responseVar.set(LlmResponseContent("", Some(failure.getMessage), finishedGeneration = true, System.currentTimeMillis())),
          success => {
            val generated = success.data.getOrElse("generatedText", "")
            responseVar.set(LlmResponseContent(generated, None, finishedGeneration = true, System.currentTimeMillis()))
          }
        )
      }.recover { case e =>
        println("[ERROR] AccessLLM: " + e.getMessage)
        e.printStackTrace()
        responseVar.set(LlmResponseContent("", Some(e.getMessage), finishedGeneration = true, System.currentTimeMillis()))
      }
    } catch {
      case e: Exception =>
        responseVar.set(LlmResponseContent("", Some(s"Invalid serverAccess '$serverAccess': ${e.getMessage}"), finishedGeneration = true, System.currentTimeMillis()))
    }
  }
}

object AccessLLM {
  case class ChatRequest(systemPrompt: String, messengerModel: MessengerModel)

  case class LlmResponseContent(generatedText: String, errorMsg: Option[String], finishedGeneration: Boolean, lastChangeTimestampMillis: Long)

  case class LlmResponse(chatRequest: ChatRequest, responseVar: Var[LlmResponseContent]) {
    private val completionPromise = Promise[MessengerModel]()

    private def resolveCompletion(content: LlmResponseContent): Unit = {
      if content.finishedGeneration then
        content.errorMsg match
          case Some(errorMessage) => completionPromise.tryFailure(new RuntimeException(errorMessage))
          case None => completionPromise.trySuccess(getCurrentMessageState())
    }

    responseVar.signal.addObserver(Observer[LlmResponseContent](resolveCompletion))(using unsafeWindowOwner)
    resolveCompletion(responseVar.now())

    def waitForFullGeneration(): Future[MessengerModel] = completionPromise.future

    def getCurrentMessageState(): MessengerModel = {
      val currentResponse = responseVar.now()
      val priorMessage = chatRequest.messengerModel

      if currentResponse.generatedText.trim.isEmpty then priorMessage
      else priorMessage.addMessage(
        Message(
          text = currentResponse.generatedText,
          timestampEpochMillis = currentResponse.lastChangeTimestampMillis.toString,
          author = teacherAuthor(priorMessage),
          senderRole = SenderRole.TEACHER
        )
      )
    }

  }

  private def teacherAuthor(priorMessage: MessengerModel): Person = {
    priorMessage.orderedMessages.reverse.collectFirst {
      case message if message.senderRole == SenderRole.TEACHER => message.author
    }.getOrElse(BasicPerson(LanguageMap.universalMap("AI")))
  }

  given ReadWriter[ChatRequest] = macroRW
}
