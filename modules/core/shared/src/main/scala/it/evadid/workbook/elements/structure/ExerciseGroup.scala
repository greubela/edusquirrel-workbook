package it.evadid.workbook.elements.structure

import it.evadid.workbook.serialization.WorkbookElementFactory.constructorLayout

import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement}
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

/** An untitled vertical group of workbook elements, suitable for a slideshow panel. */
case class ExerciseGroup(override val elementId: String, elements: Seq[WorkbookElement]) extends WorkbookDisplayElement {
  override lazy val childrenOfThisElement: Seq[WorkbookElement] = elements
  override val associatedFactory = ExerciseGroup.factory
}

object ExerciseGroup {
  val factory: WorkbookElementFactory[ExerciseGroup] = new WorkbookElementFactory[ExerciseGroup] {
    override lazy val elementMapAndOrderForConstructorLike = constructorLayout("elementId", "elements")
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] =
      element.getElementsAs[WorkbookElementReference]("elements").map(_.referencedId).toSet
    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): ExerciseGroup =
      ExerciseGroup(element.elementId, element.getAndResolveWorkbookElements("elements", parsedElements))
    override def toSerializableElement(element: ExerciseGroup): WorkbookElementSerializable =
      toFactoryBase(element).withElementsAddedAs[WorkbookElementReference]("elements", element.elements.map(_.asRef))
  }
}
