package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ai

import scala.concurrent.Future

/**
 * Minimal LLM client interface.
 *
 * The command-backed implementation sends typed requests through the backend.
 */
trait LlmClient {
  def complete(prompt: String, systemPrompt: Option[String] = None): Future[String]

}
