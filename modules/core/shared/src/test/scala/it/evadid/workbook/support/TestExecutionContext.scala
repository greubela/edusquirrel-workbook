package it.evadid.workbook.support

import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.workbook.interaction.sync.SyncControl
import it.evadid.workbook.meta.ExecutionContextInformation

import scala.concurrent.ExecutionContext

case class TestExecutionContext(
    availableExecutionClients: List[ExecutionClient] = Nil,
    languageMapIdResolver: Option[LanguageMapIdResolver] = None,
    syncControl: Option[SyncControl] = None,
    language: () => HumanLanguage = () => English,
    futureExecutionContext: ExecutionContext = ExecutionContext.global
) extends ExecutionContextInformation {
  override def getCurrentLanguage: HumanLanguage = language()
}
