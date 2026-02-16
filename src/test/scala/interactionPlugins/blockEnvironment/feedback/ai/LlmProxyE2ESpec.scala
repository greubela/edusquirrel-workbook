package interactionPlugins.blockEnvironment.feedback.ai

import munit.FunSuite

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

/**
 * Opt-in E2E test for the dev proxy.
 *
 * What it proves:
 * - The Scala.js client can reach the proxy endpoint.
 * - The proxy returns a non-empty completion (i.e., it likely reached OpenAI).
 *
 * By default this test is skipped to avoid network usage / costs.
 *
 * How to run locally:
 * 1) Start the proxy: (from tools/openai-proxy) uvicorn app:app --reload --port 8000
 * 2) RUN_E2E_LLM=1 LLM_PROXY_URL=http://localhost:8000/api/llm/complete sbt -no-colors \
 *      'testOnly interactionPlugins.blockEnvironment.feedback.ai.LlmProxyE2ESpec'
 */
final class LlmProxyE2ESpec extends FunSuite:

  private def proxyUrl: String =
    sys.env
      .get("LLM_PROXY_URL")
      .orElse(sys.env.get("E2E_LLM_PROXY_URL"))
      .getOrElse("http://localhost:8000/api/llm/complete")

  private def hasGlobalFetch: Boolean =
    try js.typeOf(js.Dynamic.global.fetch) == "function"
    catch case _: Throwable => false

  test("E2E: proxy returns marker text (opt-in)") {
    assume(sys.env.get("RUN_E2E_LLM").contains("1"), "Set RUN_E2E_LLM=1 to enable this test")
    assume(hasGlobalFetch, "No global fetch() available in this Scala.js test runtime")

    given ExecutionContext = global

    val llm = new FetchProxyLlmClient(endpointUrl = proxyUrl, requestTimeoutMs = 20_000)

    // Keep the prompt extremely short and deterministic.
    // We only assert that the proxy returns something non-empty containing a marker.
    val marker = "E2E_OK"
    val prompt = s"Reply with exactly this token and nothing else: $marker"

    llm.complete(prompt).map { text =>
      val out = Option(text).getOrElse("").trim
      assert(out.nonEmpty, s"Proxy returned empty text. Is it running at ${proxyUrl} and does it have OPENAI_API_KEY?")
      assert(out.contains(marker), s"Expected marker '$marker' in response, got: '$out'")
    }
  }
