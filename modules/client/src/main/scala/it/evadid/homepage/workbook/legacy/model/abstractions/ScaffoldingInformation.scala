package it.evadid.homepage.workbook.legacy.model.abstractions

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.workbook.model.interaction.WorkbookInteraction


case class ScaffoldingInformation[T](
                                      underlyingInteraction: WorkbookInteraction[T],
                                      exerciseText: LanguageMap[HumanLanguage],
                                      additionalScaffolds: LanguageMap[HumanLanguage]
                                    ) {

}
