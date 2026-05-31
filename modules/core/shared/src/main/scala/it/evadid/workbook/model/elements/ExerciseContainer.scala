package it.evadid.workbook.model.elements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.WorkbookGroupType.EXERCISE_CONTAINER
import it.evadid.workbook.model.abstractions.{WorkbookElement, WorkbookElementGroup, WorkbookGroupType}

case class ExerciseContainer(containerTitle: LanguageMapContentId, containerContent: List[WorkbookElement]) extends WorkbookElementGroup[WorkbookElement] {

  override val groupElements: List[WorkbookElement] = containerContent

  override val groupType: Option[WorkbookGroupType] = Some(EXERCISE_CONTAINER)

}
