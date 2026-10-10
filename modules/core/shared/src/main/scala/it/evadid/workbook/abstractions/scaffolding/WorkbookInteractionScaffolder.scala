package it.evadid.workbook.abstractions.scaffolding

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap

import scala.concurrent.Future

trait WorkbookInteractionScaffolder[T, S <: ScaffoldingResult[T]] {


  def generateFeedback(currentState: T): Future[S]


}
