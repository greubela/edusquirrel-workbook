package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.grading.GradingImportance.REQUIRED_TEST
import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import it.evadid.workbook.abstractions.scaffolding.WorkbookInteractionScaffolder
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.elements.interactionElements.programming.AdditionalProgrammingTestInfo
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
) extends WorkbookInteractionElementFull[ProgrammingState, AdditionalProgrammingTestInfo, Unit] derives ReadWriter {

  override val defaultValue: ProgrammingState = initProgram
  override val serializerInteractionContent: Serializer[ProgrammingState] = ProgrammingState.serializer
  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
  override val associatedFactory: WorkbookElementFactory[TurtleRecreateShapeInteraction] = TurtleRecreateShapeInteraction.factory


  override val gradingElements: List[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]] =
    List(RecreateShapeGrader(desiredResult, REQUIRED_TEST, elementId + "-grader", comparisonPolicy))
  //val scaffoldingElement: Option[WorkbookInteractionScaffolder[ProgrammingState, ?]] = None
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
