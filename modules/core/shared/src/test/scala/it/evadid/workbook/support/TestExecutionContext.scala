package it.evadid.workbook.support

import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.binding.SyncConnection
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.meta.ExecutionContextInformation

import scala.concurrent.ExecutionContext

case class TestExecutionContext(
    availableExecutionClients: List[ExecutionClient] = Nil,
    languageMapIdResolver: Option[LanguageMapIdResolver] = None,
    language: () => HumanLanguage = () => English,
    futureExecutionContext: ExecutionContext = ExecutionContext.global,
    override val llmConfiguration: Option[it.evadid.llm.LlmConfiguration] = None
) extends ExecutionContextInformation {
  override def getCurrentLanguage: HumanLanguage = language()
}

extension [F <: InteractionFormat](variable: InteractionVariable[F])
  /** Connects the current binding to `context`, so following feedback requests and automatic grading use it. */
  def executingWith(context: ExecutionContextInformation): InteractionVariable[F] = {
    variable.binding.connect(SyncConnection.offline.copy(execution = context))
    variable
  }
