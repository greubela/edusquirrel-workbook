package it.evadid.workbook.model.elements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.{WorkbookElement, WorkbookElementGroup, WorkbookGroupType}

case class LabeledWorkbookContainer(containerLabel: LanguageMapContentId, containerContent: List[WorkbookElement]) extends WorkbookElementGroup[WorkbookElement] {


  override val groupElements: List[WorkbookElement] = containerContent

  override val groupType: Option[WorkbookGroupType] = Some(WorkbookGroupType.EXERCISE_CONTAINER)

}
