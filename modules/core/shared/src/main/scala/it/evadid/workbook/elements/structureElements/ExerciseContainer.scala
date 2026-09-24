package it.evadid.workbook.elements.structureElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.WorkbookStructuringType.EXERCISE_CONTAINER
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookStructureElement, WorkbookStructuringType}
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

object ExerciseContainer {
  }

case class ExerciseContainer(override val elementId: String, containerTitle: LanguageMapContentId, containerContent: List[WorkbookElement]) extends WorkbookStructureElement[WorkbookElement] {
  override val associatedFactory = it.evadid.workbook.jsonFactory.WorkbookElementFactory.unsupported[this.type](this.getClass.getSimpleName)

  override val groupElements: List[WorkbookElement] = containerContent

  override lazy val structureType: WorkbookStructuringType = EXERCISE_CONTAINER


}
