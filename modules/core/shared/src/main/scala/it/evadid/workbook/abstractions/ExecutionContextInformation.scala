package it.evadid.workbook.abstractions

import it.evadid.llm.LlmConfiguration
import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.distribution.clients.ExecutionClient

import scala.concurrent.ExecutionContext

trait ExecutionContextInformation {

  def llmConfiguration: Option[LlmConfiguration] = None

  def availableExecutionClients: Seq[ExecutionClient]

  def getCurrentLanguage: HumanLanguage

  def languageMapIdResolver: Option[LanguageMapIdResolver]

  def futureExecutionContext: ExecutionContext

}

object ExecutionContextInformation {

  val default: ExecutionContextInformation = new ExecutionContextInformation() {


    override def availableExecutionClients: Seq[ExecutionClient] = List()

    override def getCurrentLanguage: HumanLanguage = English

    override def languageMapIdResolver: Option[LanguageMapIdResolver] = None

    override def futureExecutionContext: ExecutionContext = ExecutionContext.global
  }

}