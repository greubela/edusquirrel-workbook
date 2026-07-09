package it.evadid.workbook.elements.structureElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.WorkbookStructuringType.EXERCISE_CONTAINER
import it.evadid.workbook.model.abstractions.{WorkbookElement, WorkbookStructureElement, WorkbookStructuringType}

case class ExerciseContainer(containerTitle: LanguageMapContentId, containerContent: List[WorkbookElement]) extends WorkbookStructureElement[WorkbookElement] {

  override val groupElements: List[WorkbookElement] = containerContent

  override lazy val structureType: WorkbookStructuringType = EXERCISE_CONTAINER

}
