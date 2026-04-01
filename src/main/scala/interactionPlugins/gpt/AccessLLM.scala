package interactionPlugins.gpt

import com.raquo.airstream.core.Observer
import com.raquo.laminar.api.L.{Var, unsafeWindowOwner}
import datastructures.core.chat.MessengerModel.{BasicPerson, Message, Person, SenderRole}
import datastructures.core.chat.MessengerModel
import datastructures.core.language.LanguageMap
import org.scalajs.dom
import org.scalajs.dom.{Headers, HttpMethod, RequestInit}
import upickle.default.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.Thenable
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.js.annotation.*
import scala.scalajs.js.typedarray.Uint8Array

@js.native
@JSGlobal("TextDecoder")
class TextDecoder(encoding: String = "utf-8") extends js.Object {
  def decode(input: Uint8Array, options: js.UndefOr[js.Dictionary[Boolean]] = js.undefined): String = js.native
}

@js.native
trait ReadableStreamReaderChunk extends js.Object {
  val done: Boolean
  val value: Uint8Array
}

@js.native
trait ReadableStreamDefaultReader extends js.Object {
  def read(): Thenable[ReadableStreamReaderChunk] = js.native
}

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

    sendRequestStreamed(chatRequest, responseVar)
    response
  }

  private def sendRequestStreamed(chatRequest: ChatRequest, responseVar: Var[LlmResponseContent]): Unit = {
    val requestBody = write(chatRequest)

    val myHeaders = new Headers()
    myHeaders.set("Content-Type", "application/json")

    val requestInit = new RequestInit {
      method = HttpMethod.POST
      this.headers = myHeaders
      body = requestBody
    }

    val request = new dom.Request(serverAccess, requestInit)

    dom.fetch(request).toFuture.flatMap { response =>
      if !response.ok then
        throw new RuntimeException(s"HTTP ${response.status.toInt} while calling $serverAccess")

      val body = response.body
      if body == null then
        throw new RuntimeException(s"No response body while calling $serverAccess")

      val reader = body.getReader().asInstanceOf[ReadableStreamDefaultReader]
      val decoder = new TextDecoder("utf-8")

      def updateResponse(updateFn: LlmResponseContent => LlmResponseContent): Unit = {
        responseVar.update { current =>
          updateFn(current).copy(lastChangeTimestampMillis = System.currentTimeMillis())
        }
      }

      def pump(): Unit = {
        reader.read().toFuture.map { chunk =>
          if !chunk.done && chunk.value != null then
            val curChunkText = decoder.decode(chunk.value)
            updateResponse(cur => cur.copy(generatedText = cur.generatedText + curChunkText, finishedGeneration = false))
            pump()
          else
            updateResponse(cur => cur.copy(finishedGeneration = true))
        }
      }

      pump()
      js.Promise.resolve(())
    }.recover { case e =>
      println("[ERROR] AccessLLM: " + e.getMessage)
      e.printStackTrace()
      responseVar.update(_.copy(errorMsg = Some(e.getMessage), finishedGeneration = true, lastChangeTimestampMillis = System.currentTimeMillis()))
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

    responseVar.signal.addObserver(Observer[LlmResponseContent](resolveCompletion))(unsafeWindowOwner)
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
