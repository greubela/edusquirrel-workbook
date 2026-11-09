package it.evadid.llm

import upickle.default.*

case class LlmConfiguration(endpoint: String, model: String, apiKey: Option[String], maxTokens: Option[Int] = None) derives ReadWriter {
  override def toString: String = s"LlmConfiguration($endpoint,$model,apiKey=redacted,maxTokens=$maxTokens)"
}

object LlmConfiguration {
  val defaultEndpoint: String = "https://api.openai.com/v1/chat/completions"
  val defaultModel: String = "gpt-4o-mini"
}
