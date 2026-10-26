package it.evadid.workbook.abstractions.scaffolding

import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.distribution.clients.ExecutionClient

import scala.concurrent.Future

trait WorkbookInteractionScaffolder[T, A] {

  def generateFeedback(currentState: T, availableExecutionClients: List[ExecutionClient], resolver: LanguageMapIdResolver): Future[ScaffoldingResult[T, A]]


}
