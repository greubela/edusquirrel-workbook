package interactionPlugins.gpt

import org.scalajs.dom
import org.scalajs.dom.{Headers, HttpMethod, RequestInit}
import upickle.default.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.js.annotation.*
import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js.{JSON, Thenable}

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

  case class ChunkUpdateEvent(prompt: String, allResponsesUntilNow: String, newestChunk: String, chunkFinished: Boolean, timestampMillis: Long)

  case class ErrorEvent(prompt: String, errorMsg: String, timestampMillis: Long)


  def callStreamed(prompt: String, handleOnUpdate: ChunkUpdateEvent => Any, handleOnError: ErrorEvent => Any): Unit = {


    var latestChunkUpdateEvent = ChunkUpdateEvent(
      prompt = prompt,
      allResponsesUntilNow = "",
      newestChunk = "",
      chunkFinished = false,
      timestampMillis = System.currentTimeMillis()
    )

    val fullJsonString = write(Map("llmPrompt" -> prompt))

    println("send json: " + fullJsonString + "\n\n\n")

    val myHeaders = new Headers()
    myHeaders.set("Content-Type", "application/json")

    val requestInit = new RequestInit {
      method = HttpMethod.POST
      this.headers = myHeaders
      body = fullJsonString
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

            println("[INFO] AccessLLM, received text: >>>" + curChunkText +"<<<")

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
      handleOnError(ErrorEvent(prompt, e.getMessage, System.currentTimeMillis()))
    }
  }

}
