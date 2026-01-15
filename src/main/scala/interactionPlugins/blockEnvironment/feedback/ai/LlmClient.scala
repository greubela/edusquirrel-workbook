package interactionPlugins.blockEnvironment.feedback.ai

import scala.concurrent.Future

/**
 * Minimal LLM client interface.
 *
 * Implementations can be browser-based (Scala.js fetch) or server-side.
 */
trait LlmClient {
  def complete(prompt: String, systemPrompt: Option[String] = None): Future[String]
}
