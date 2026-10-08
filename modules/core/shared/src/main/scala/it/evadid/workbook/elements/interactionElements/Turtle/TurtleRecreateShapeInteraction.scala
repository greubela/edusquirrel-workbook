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
  override val associatedFactory: WorkbookElementFactory[TurtleRecreateShapeInteraction] = TurtleRecreateShapeInteraction.factory
}

object TurtleRecreateShapeInteraction {
  val factory: WorkbookElementFactory[TurtleRecreateShapeInteraction] = new SimpleWorkbookElementFactory[TurtleRecreateShapeInteraction]() {
    override protected val constructorFieldOrder = List("elementId", "initProgram", "desiredResult", "availablePalette", "limitTurtleCommandUsage")

    override def finishSerialization(baseElement: WorkbookElementSerializable, element: TurtleRecreateShapeInteraction): WorkbookElementSerializable =
      baseElement
        .withElementAddedAs("initProgram", element.initProgram)
        .withElementAddedAs("desiredResult", element.desiredResult)
        .withElementAddedAs("availablePalette", element.availablePalette)
        .withElementAddedAs("limitTurtleCommandUsage", element.limitTurtleCommandUsage)

    override def finishDeserialization(serialized: WorkbookElementSerializable): TurtleRecreateShapeInteraction =
      TurtleRecreateShapeInteraction(
        serialized.elementId,
        serialized.getElementAs[ProgrammingState]("initProgram"),
        serialized.getElementAs[TurtleGraphic]("desiredResult"),
        serialized.getElementAs[ProgrammingEditorPalette]("availablePalette"),
        serialized.getElementAs[Map[String, Integer]]("limitTurtleCommandUsage")
      )
  }
}
