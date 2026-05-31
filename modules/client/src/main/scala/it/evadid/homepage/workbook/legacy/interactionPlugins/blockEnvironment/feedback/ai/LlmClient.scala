package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ai

import scala.concurrent.Future

/**
 * Minimal LLM client interface.
 *
 * Implementations can be browser-based (Scala.js fetch) or server-side.
 */
trait LlmClient {
  def complete(prompt: String, systemPrompt: Option[String] = None): Future[String]

  def completeWithMeta(
    prompt: String,
    systemPrompt: Option[String] = None,
    logTag: Option[String] = None,
    studentCode: Option[String] = None,
    debugMeta: Map[String, String] = Map.empty
  ): Future[String] =
    complete(prompt, systemPrompt)
}
