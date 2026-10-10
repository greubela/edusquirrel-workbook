package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElementWithGrader}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

case class TurtleRecreateShapeInteraction
(
  override val elementId: String,
  initProgram: ProgrammingState,
  desiredResult: TurtleGraphic,
  availablePalette: ProgrammingEditorPalette = ProgrammingEditorPalette.BeginnerTurtle,
  limitTurtleCommandUsage: Map[String, Integer],
  comparisonPolicy: TurtleDrawingPolicy = TurtleDrawingPolicy.Strokes
) extends WorkbookInteractionElementWithGrader[ProgrammingState, RecreateShapeGradingResult] derives ReadWriter {

  override val defaultValue: ProgrammingState = initProgram
  override val serializerInteractionContent: Serializer[ProgrammingState] = Serializer.fromUpickleJson(ProgrammingState.derived$ReadWriter)
  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
  override val associatedFactory: WorkbookElementFactory[TurtleRecreateShapeInteraction] = TurtleRecreateShapeInteraction.factory
}

object TurtleRecreateShapeInteraction {
  val factory: WorkbookElementFactory[TurtleRecreateShapeInteraction] = new SimpleWorkbookElementFactory[TurtleRecreateShapeInteraction]() {
    override protected val constructorFieldOrder = List("elementId", "initProgram", "desiredResult", "availablePalette", "limitTurtleCommandUsage", "comparisonPolicy")

    override def finishSerialization(baseElement: WorkbookElementSerializable, element: TurtleRecreateShapeInteraction): WorkbookElementSerializable = {
      val serialized = baseElement
        .withElementAddedAs("initProgram", element.initProgram)
        .withElementAddedAs("desiredResult", element.desiredResult)
        .withElementAddedAs("availablePalette", element.availablePalette)
        .withElementAddedAs("limitTurtleCommandUsage", element.limitTurtleCommandUsage)
      if element.comparisonPolicy == TurtleDrawingPolicy.Strokes then serialized
      else serialized.withElementAddedAs("comparisonPolicy", element.comparisonPolicy)
    }

    override def finishDeserialization(serialized: WorkbookElementSerializable): TurtleRecreateShapeInteraction =
      TurtleRecreateShapeInteraction(
        serialized.elementId,
        serialized.getElementAs[ProgrammingState]("initProgram"),
        serialized.getElementAs[TurtleGraphic]("desiredResult"),
        serialized.getElementAs[ProgrammingEditorPalette]("availablePalette"),
        serialized.getElementAs[Map[String, Integer]]("limitTurtleCommandUsage"),
        serialized.getOptionalElementAs[TurtleDrawingPolicy]("comparisonPolicy", TurtleDrawingPolicy.Strokes)
      )
  }
}
