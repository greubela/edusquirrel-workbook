package it.evadid.workbook.abstractions.scaffolding

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap

trait WorkbookInteractionScaffolder[T] {


  def generateFeedback(currentState: T): LanguageMap[HumanLanguage]


}
