package interactionPlugins.blockEnvironment.feedback.ai

import org.scalajs.dom
import org.scalajs.dom.HttpMethod
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/**
 * Scala.js LLM client that calls a local HTTP proxy using fetch().
 *
 * The proxy should expose POST /api/llm/complete returning streaming text/plain.
 */
final class FetchProxyLlmClient(
  endpointUrl: String,
  requestTimeoutMs: Int = 20_000
) extends LlmClient {

  override def complete(prompt: String, systemPrompt: Option[String] = None): Future[String] = {
    val payload = js.Dynamic.literal(
      "prompt" -> prompt,
      "systemPrompt" -> systemPrompt.getOrElse("")
    )

    val init = new dom.RequestInit {
      method = HttpMethod.POST
      headers = js.Dictionary("Content-Type" -> "application/json")
      body = JSON.stringify(payload)
    }

    val fetchF: Future[dom.Response] = jsPromiseToFuture(dom.fetch(endpointUrl, init))

    withTimeout(fetchF.flatMap { resp =>
      if (!resp.ok) {
        jsPromiseToFuture(resp.text()).flatMap { txt =>
          Future.failed(new Exception(s"LLM proxy HTTP ${resp.status}: ${Option(txt).getOrElse("")}"))
        }
      } else {
        readText(resp)
      }
    }, requestTimeoutMs)
  }

  private def readText(resp: dom.Response): Future[String] =
    // Proxy response is text/plain (often streamed). Response.text() is the most
    // compatible option across Scala.js environments.
    jsPromiseToFuture(resp.text())

  private def jsPromiseToFuture[A](promise: js.Promise[A]): Future[A] = {
    val p = Promise[A]()
    promise.`then`[Unit](
      onFulfilled = (value: A) => {
        p.success(value)
        ()
      },
      onRejected = (err: Any) => {
        p.failure(new Exception(err.toString))
        ()
      }
    )
    p.future
  }

  private def withTimeout[A](future: Future[A], timeoutMs: Int): Future[A] = {
    if (timeoutMs <= 0) future
    else {
      val p = Promise[A]()
      val handle: SetTimeoutHandle = setTimeout(timeoutMs.toDouble) {
        p.tryFailure(new Exception(s"LLM request timed out after ${timeoutMs}ms"))
      }
      future.onComplete { res =>
        clearTimeout(handle)
        p.tryComplete(res)
      }(scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)
      p.future
    }
  }
}

object FetchProxyLlmClient {

  /** Default endpoint for local dev. */
  private val DefaultUrl = "http://localhost:8000/api/llm/complete"

  /**
   * Best-effort config discovery for browser runs.
   *
   * - If `globalThis.LLM_PROXY_URL` is set, use it.
   * - Else use the default localhost URL.
   */
  def default(): FetchProxyLlmClient = {
    def readGlobalLlmProxyUrl: Option[String] =
      try {
        // Scala.js restriction: global scope selections must use a static name.
        val v = js.Dynamic.global.selectDynamic("LLM_PROXY_URL")
        if (js.isUndefined(v) || v == null) None else Some(v.toString)
      } catch {
        case _: Throwable => None
      }

    def readEnv(name: String): Option[String] =
      try {
        val process = js.Dynamic.global.selectDynamic("process")
        if (js.isUndefined(process) || process == null) None
        else {
          val env = process.selectDynamic("env")
          if (js.isUndefined(env) || env == null) None
          else {
            val v = env.selectDynamic(name)
            if (js.isUndefined(v) || v == null) None else Some(v.toString)
          }
        }
      } catch {
        case _: Throwable => None
      }

    val url =
      readGlobalLlmProxyUrl
        .orElse(readEnv("LLM_PROXY_URL"))
        .getOrElse(DefaultUrl)

    new FetchProxyLlmClient(endpointUrl = url)
  }
}
