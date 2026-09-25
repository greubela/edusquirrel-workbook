package it.evadid.workbook.elements.structureElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.WorkbookStructuringType.EXERCISE_CONTAINER
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookStructureElement, WorkbookStructuringType}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

object ExerciseContainer {
  val factory: WorkbookElementFactory[ExerciseContainer] = new WorkbookElementFactory[ExerciseContainer] {
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] =
      element.getElementsAsWorkbookReferences("content").map(_.referencedId).toSet
    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable] = Seq.empty
    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): ExerciseContainer =
      ExerciseContainer(element.elementId, element.getElementAsContentId("title"), element.getAndResolveWorkbookElements("content", parsedElements))
    override def toSerializableElement(element: ExerciseContainer): WorkbookElementSerializable =
      toFactoryBase(element).withContentIdAdded("title", element.containerTitle).withElementsAddedAs[WorkbookElementReference]("content", element.containerContent.map(_.asRef))
  }
}

case class ExerciseContainer(override val elementId: String, containerTitle: LanguageMapContentId, containerContent: List[WorkbookElement]) extends WorkbookStructureElement[WorkbookElement] {
  override val associatedFactory = ExerciseContainer.factory

  override val groupElements: List[WorkbookElement] = containerContent

  override lazy val structureType: WorkbookStructuringType = EXERCISE_CONTAINER


}
