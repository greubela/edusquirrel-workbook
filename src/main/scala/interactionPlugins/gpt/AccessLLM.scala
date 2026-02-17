package interactionPlugins.gpt

import contentmanagement.model.chat.MessengerModel
import org.scalajs.dom
import org.scalajs.dom.{Headers, HttpMethod, RequestInit}
import upickle.default.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.js.annotation.*
import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js.Thenable

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

object AccessLLM {

  val serverAccess: String = "https://ypcgzj23.trafficplex.cloud/chat" // http://127.0.0.1:8001/chat

  type MessangerHistory = MessengerModel

  case class ChunkUpdateEvent(prompt: String, allResponsesUntilNow: String, newestChunk: String, chunkFinished: Boolean, timestampMillis: Long)

  case class ErrorEvent(prompt: String, errorMsg: String, timestampMillis: Long)

  private case class ChatRequest(systemPrompt: String, messengerModel: MessengerModel)
  private given ReadWriter[ChatRequest] = macroRW

  def callStreamed(systemPrompt: String, curHistory: MessangerHistory, handleOnUpdate: ChunkUpdateEvent => Any, handleOnError: ErrorEvent => Any): Unit = {
    val requestBody = write(ChatRequest(systemPrompt, curHistory))
    callStreamedRaw(promptForEvents = systemPrompt, requestBody = requestBody, handleOnUpdate, handleOnError)
  }

  def callStreamed(prompt: String, handleOnUpdate: ChunkUpdateEvent => Any, handleOnError: ErrorEvent => Any): Unit = {
    val fallbackHistory = MessengerModel(List())
    callStreamed(prompt, fallbackHistory, handleOnUpdate, handleOnError)
  }

  private def callStreamedRaw(promptForEvents: String, requestBody: String, handleOnUpdate: ChunkUpdateEvent => Any, handleOnError: ErrorEvent => Any): Unit = {

    var latestChunkUpdateEvent = ChunkUpdateEvent(
      prompt = promptForEvents,
      allResponsesUntilNow = "",
      newestChunk = "",
      chunkFinished = false,
      timestampMillis = System.currentTimeMillis()
    )

    println("send json: " + requestBody + "\n\n\n")

    val myHeaders = new Headers()
    myHeaders.set("Content-Type", "application/json")

    val requestInit = new RequestInit {
      method = HttpMethod.POST
      this.headers = myHeaders
      body = requestBody
    }

    val request = new dom.Request(serverAccess, requestInit)

    dom.fetch(request).toFuture.flatMap { response =>
      val reader = response.body.getReader().asInstanceOf[ReadableStreamDefaultReader]
      val decoder = new TextDecoder("utf-8")

      def pump(): Unit = {
        reader.read().toFuture.map { chunk =>
          if (!chunk.done && chunk.value != null) {
            val curChunkText = decoder.decode(chunk.value)
            val updatedText = latestChunkUpdateEvent.allResponsesUntilNow + curChunkText

            println("[INFO] AccessLLM, received text: >>>" + curChunkText + "<<<")

            latestChunkUpdateEvent = latestChunkUpdateEvent.copy(
              allResponsesUntilNow = updatedText,
              newestChunk = curChunkText,
              chunkFinished = false,
              timestampMillis = System.currentTimeMillis()
            )

            handleOnUpdate(latestChunkUpdateEvent)
            pump()
          } else {
            handleOnUpdate(latestChunkUpdateEvent.copy(chunkFinished = true))
          }
        }
      }

      pump()
      js.Promise.resolve(())
    }.recover { case e =>
      println("[ERROR] AccessLLM: " + e.getMessage)
      e.printStackTrace()
      handleOnError(ErrorEvent(promptForEvents, e.getMessage, System.currentTimeMillis()))
    }
  }

}
