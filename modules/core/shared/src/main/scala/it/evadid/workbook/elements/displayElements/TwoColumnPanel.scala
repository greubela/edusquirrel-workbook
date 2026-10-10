package it.evadid.workbook.elements.displayElements

import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement}
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

/** Two independently renderable workbook elements displayed side by side. */
case class TwoColumnPanel(override val elementId: String, left: WorkbookElement, right: WorkbookElement) extends WorkbookDisplayElement {
  override lazy val childrenOfThisElement: List[WorkbookElement] = List(left, right)
  override val associatedFactory = TwoColumnPanel.factory
}

object TwoColumnPanel {
  val factory: WorkbookElementFactory[TwoColumnPanel] = new WorkbookElementFactory[TwoColumnPanel] {
    override lazy val elementMapAndOrderForConstructorLike = Map(
      0 -> List(VariableDisplayConfig("elementId", true)),
      1 -> List(VariableDisplayConfig("left", false)),
      2 -> List(VariableDisplayConfig("right", false))
    )
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] =
      Set(element.getElementAs[WorkbookElementReference]("left").referencedId, element.getElementAs[WorkbookElementReference]("right").referencedId)
    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable] = Seq.empty
    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): TwoColumnPanel =
      TwoColumnPanel(element.elementId, element.getAndResolveWorkbookElement[WorkbookElement]("left", parsedElements), element.getAndResolveWorkbookElement[WorkbookElement]("right", parsedElements))
    override def toSerializableElement(element: TwoColumnPanel): WorkbookElementSerializable =
      toFactoryBase(element).withElementAddedAs[WorkbookElementReference]("left", element.left.asRef).withElementAddedAs[WorkbookElementReference]("right", element.right.asRef)
  }
}
