package it.evadid.workbook.elements.interactionElements.Turtle

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingState, TurtleGraphic}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

case class TurtleRecreateShapeInteraction
(
  override val elementId: String,
  initProgram: ProgrammingState,
  desiredResult: TurtleGraphic
) extends WorkbookInteractionElement[ProgrammingState] derives ReadWriter {

  override val defaultValue: ProgrammingState = initProgram
  override val serializerInteractionContent: Serializer[ProgrammingState] = Serializer.fromUpickleJson(ProgrammingState.derived$ReadWriter)
  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
  override val associatedFactory: WorkbookElementFactory[_ <: WorkbookElement] = new WorkbookElementFactory[TurtleRecreateShapeInteraction] {
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = {
      Set()
    }

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): TurtleRecreateShapeInteraction = {
      TurtleRecreateShapeInteraction(
        element.elementId,
        initProgram = parsedElements(element.getElement("initProgram")).asInstanceOf[ProgrammingState],
        desiredResult = parsedElements(element.getElement("desiredResult")).asInstanceOf[TurtleGraphic])
    }
  }
}

