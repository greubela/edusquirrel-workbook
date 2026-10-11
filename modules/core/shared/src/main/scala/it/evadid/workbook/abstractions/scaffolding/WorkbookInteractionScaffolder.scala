package it.evadid.workbook.abstractions.scaffolding

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap

import scala.concurrent.Future

trait WorkbookInteractionScaffolder[T, A] {

  def generateFeedback(currentState: T): Future[ScaffoldingResult[T, A]]


}
