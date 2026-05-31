package it.evadid.workbook.model.elements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.*
import it.evadid.workbook.model.abstractions.WorkbookGroupType.SECTION

case class WorkbookSection(
                            sectionId: String,
                            sectionTitle: LanguageMapContentId,
                            sectionContent: List[WorkbookElement],
                            sectionsRequiredBefore: List[WorkbookSection] = List(),
                            sectionsRecommendedBefore: List[WorkbookSection] = List()
                          ) extends WorkbookElementGroup[WorkbookElement] {

  override val groupElements: List[WorkbookElement] = sectionContent
  
  override val groupType: Option[WorkbookGroupType] = Some(SECTION)

}
