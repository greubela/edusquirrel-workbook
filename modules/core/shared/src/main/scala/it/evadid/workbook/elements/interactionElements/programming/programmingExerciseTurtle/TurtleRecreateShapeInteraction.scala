package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python, SnapLanguage}
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.grading.GradingImportance.REQUIRED_TEST
import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.elements.interactionElements.programming.AdditionalProgrammingTestInfo
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.RecreateTurtleDidacticConfig.defaultDidacticConfig
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction.*
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
  comparisonPolicy: TurtleDrawingPolicy = TurtleDrawingPolicy.Strokes,
  customExerciseConfig: Option[RecreateTurtleDidacticConfig] = Some(defaultDidacticConfig)
) extends WorkbookInteractionElementFull[ProgrammingState, AdditionalProgrammingTestInfo, Unit] derives ReadWriter {

  override val defaultValue: ProgrammingState = initProgram
  override val serializerInteractionContent: Serializer[ProgrammingState] = ProgrammingState.serializer
  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
  override val associatedFactory: WorkbookElementFactory[TurtleRecreateShapeInteraction] = TurtleRecreateShapeInteraction.factory

  def exerciseConfig: RecreateTurtleDidacticConfig = customExerciseConfig.getOrElse(RecreateTurtleDidacticConfig.defaultDidacticConfig)

  override val gradingElements: List[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]] =
    List(RecreateShapeGrader(desiredResult, REQUIRED_TEST, elementId + "-grader", comparisonPolicy)) ++
      TurtleCommandUsageGrader.forLimits(elementId, exerciseConfig.maxCommandUsage)
  //val scaffoldingElement: Option[WorkbookInteractionScaffolder[ProgrammingState, ?]] = None
}

object TurtleRecreateShapeInteraction {


  val factory: WorkbookElementFactory[TurtleRecreateShapeInteraction] = new SimpleWorkbookElementFactory[TurtleRecreateShapeInteraction]() {
    override protected val constructorFieldOrder = List("elementId", "initProgram", "desiredResult", "comparisonPolicy", "customExerciseConfig")

    override def finishSerialization(baseElement: WorkbookElementSerializable, element: TurtleRecreateShapeInteraction): WorkbookElementSerializable = {
      val serialized = baseElement
        .withElementAddedAs("initProgram", element.initProgram)
        .withElementAddedAs("desiredResult", element.desiredResult)
        .withElementAddedAs("customExerciseConfig", element.customExerciseConfig)
      if element.comparisonPolicy == TurtleDrawingPolicy.Strokes then serialized
      else serialized.withElementAddedAs("comparisonPolicy", element.comparisonPolicy)
    }

    override def finishDeserialization(serialized: WorkbookElementSerializable): TurtleRecreateShapeInteraction =
      TurtleRecreateShapeInteraction(
        serialized.elementId,
        serialized.getElementAs[ProgrammingState]("initProgram"),
        serialized.getElementAs[TurtleGraphic]("desiredResult"),
        serialized.getOptionalElementAs[TurtleDrawingPolicy]("comparisonPolicy", TurtleDrawingPolicy.Strokes),
        serialized.getOptionalElementAs[Option[RecreateTurtleDidacticConfig]]("customExerciseConfig", None),
      )
  }
}
