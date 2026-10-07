package it.evadid.workbook.elements.interactionElements.Turtle

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingEditorPalette, ProgrammingState, TurtleGraphic}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

case class TurtleRecreateShapeInteraction
(
  override val elementId: String,
  initProgram: ProgrammingState,
  desiredResult: TurtleGraphic,
  availablePalette: ProgrammingEditorPalette = ProgrammingEditorPalette.BeginnerTurtle,
  limitTurtleCommandUsage: Map[String, Integer]
) extends WorkbookInteractionElement[ProgrammingState] derives ReadWriter {

  override val defaultValue: ProgrammingState = initProgram
  override val serializerInteractionContent: Serializer[ProgrammingState] = Serializer.fromUpickleJson(ProgrammingState.derived$ReadWriter)
  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
  override val associatedFactory: WorkbookElementFactory[TurtleRecreateShapeInteraction] = new SimpleWorkbookElementFactory[TurtleRecreateShapeInteraction]() {
    override protected val constructorFieldOrder = List("elementId", "initProgram", "desiredResult", "availablePalette", "limitTurtleCommandUsage")

    override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: TurtleRecreateShapeInteraction): WorkbookElementSerializable = {
      baseElement
    }

    override def finishDeserialization(serialized: WorkbookElementSerializable): TurtleRecreateShapeInteraction = {
      TurtleRecreateShapeInteraction(
        serialized.elementId,
        serialized.getElementAs[ProgrammingState]("initProgram"),
        serialized.getElementAs[TurtleGraphic]("desiredResult"),
        serialized.getElementAs[ProgrammingEditorPalette]("availablePalette"),
        serialized.getElementAs[Map[String, Integer]]("limitTurtleCommandUsage")
      )
    }
  }


  new WorkbookElementFactory[TurtleRecreateShapeInteraction] {
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = {
      Set()
    }

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): TurtleRecreateShapeInteraction = {
      TurtleRecreateShapeInteraction(
        element.elementId,
        initProgram = parsedElements(element.getElement("initProgram")).asInstanceOf[ProgrammingState],
        desiredResult = parsedElements(element.getElement("desiredResult")).asInstanceOf[TurtleGraphic],
        availablePalette = parsedElements(element.getElement("availablePalette")).asInstanceOf[ProgrammingEditorPalette],
        limitTurtleCommandUsage = parsedElements(element.getElement("limitTurtleCommandUsage")).asInstanceOf[Map[String, Integer]],
      )
    }
  }
}

