package it.evadid.workbook.meta

import it.evadid.llm.LlmConfiguration
import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.workbook.interaction.sync.SyncControl

import scala.concurrent.ExecutionContext

trait ExecutionContextInformation {

  def syncControl: Option[SyncControl]

  def llmConfiguration: Option[LlmConfiguration] = None

  def availableExecutionClients: List[ExecutionClient]

  def getCurrentLanguage: HumanLanguage

  def languageMapIdResolver: Option[LanguageMapIdResolver]

  def futureExecutionContext: ExecutionContext

}

object ExecutionContextInformation {

  val default: ExecutionContextInformation = new ExecutionContextInformation() {


    override def availableExecutionClients: List[ExecutionClient] = List()

    override def getCurrentLanguage: HumanLanguage = English

    override def languageMapIdResolver: Option[LanguageMapIdResolver] = None

    override def futureExecutionContext: ExecutionContext = ExecutionContext.global

    override def syncControl: Option[SyncControl] = None
  }

}