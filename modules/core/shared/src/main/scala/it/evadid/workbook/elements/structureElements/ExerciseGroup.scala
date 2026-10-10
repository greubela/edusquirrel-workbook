package it.evadid.workbook.elements.structureElements

import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement}
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

/** An untitled vertical group of workbook elements, suitable for a slideshow panel. */
case class ExerciseGroup(override val elementId: String, elements: List[WorkbookElement]) extends WorkbookDisplayElement derives upickle.default.ReadWriter {
  override lazy val childrenOfThisElement: List[WorkbookElement] = elements
  override val associatedFactory = ExerciseGroup.factory
}

object ExerciseGroup {
  val factory: WorkbookElementFactory[ExerciseGroup] = new WorkbookElementFactory[ExerciseGroup] {
    override lazy val elementMapAndOrderForConstructorLike = Map(
      0 -> List(VariableDisplayConfig("elementId", true)),
      1 -> List(VariableDisplayConfig("elements", false))
    )
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] =
      element.getElementsAs[WorkbookElementReference]("elements").map(_.referencedId).toSet
    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable] = Seq.empty
    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): ExerciseGroup =
      ExerciseGroup(element.elementId, element.getAndResolveWorkbookElements("elements", parsedElements))
    override def toSerializableElement(element: ExerciseGroup): WorkbookElementSerializable =
      toFactoryBase(element).withElementsAddedAs[WorkbookElementReference]("elements", element.elements.map(_.asRef))
  }
}
