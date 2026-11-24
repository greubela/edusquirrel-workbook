package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python, SnapLanguage}
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.grading.GradingImportance.REQUIRED_TEST
import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookElement, *}
import it.evadid.workbook.elements.interactionElements.programming.AdditionalProgrammingTestInfo
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingHintScaffolder
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.RecreateTurtleDidacticConfig.defaultDidacticConfig
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.core.util.io.serializer.*
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import upickle.default.*

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
case class TurtleRecreateShapeInteraction
(
  override val elementId: String,
  initProgram: ProgrammingState,
  desiredResult: TurtleGraphic,
  comparisonPolicy: TurtleDrawingPolicy = TurtleDrawingPolicy.Strokes,
  customExerciseConfig: Option[RecreateTurtleDidacticConfig] = Some(defaultDidacticConfig),
  structureTests: Seq[ProgramStructureGrader] = Seq.empty,
  instructionText: Option[LanguageMapContentId] = None
) extends WorkbookInteractionElement[TurtleRecreateShapeInteractionFormat.type] {

  override val defaultValue: ProgrammingState = initProgram

  override lazy val childrenOfThisElement: Seq[WorkbookElement] = List()
  override val associatedFactory: WorkbookElementFactory[TurtleRecreateShapeInteraction] = TurtleRecreateShapeInteraction.factory

  def exerciseConfig: RecreateTurtleDidacticConfig = customExerciseConfig.getOrElse(RecreateTurtleDidacticConfig.defaultDidacticConfig)

  override val gradingElements: Seq[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]] =
    List(RecreateShapeGrader(desiredResult, REQUIRED_TEST, elementId + "-grader", comparisonPolicy)) ++
      TurtleCommandUsageGrader.forLimits(elementId, exerciseConfig.maxCommandUsage) ++ structureTests
  /** The hint button asks about the current program against the public task text. */
  override val scaffoldingElement: Option[ProgrammingHintScaffolder] = instructionText.map(task => new ProgrammingHintScaffolder(
    ProgrammingHintScaffolder.Exercise(() => interactionVariable.currentHistory.historyStates.lastStateOption, defaultValue,
      gradingElements, state => interactionVariable.currentHistory.historyGrading.gradingsMap.get(state).filter(_.state == state)
        .toList.flatMap(_.graderResultMap.values)), task))

}

object TurtleRecreateShapeInteraction  {

  val factory: WorkbookElementFactory[TurtleRecreateShapeInteraction] = new SimpleWorkbookElementFactory[TurtleRecreateShapeInteraction]() {
    override protected val constructorFieldOrder = List("elementId", "initProgram", "desiredResult", "comparisonPolicy", "customExerciseConfig", "structureTests", "instructionText")

    override def finishSerialization(baseElement: WorkbookElementSerializable, element: TurtleRecreateShapeInteraction): WorkbookElementSerializable = {
      val serialized = baseElement
        .withElementAddedAs("initProgram", element.initProgram)
        .withElementAddedAs("desiredResult", element.desiredResult)
        .withElementAddedAs("customExerciseConfig", element.customExerciseConfig)
        .withElementAddedAs("structureTests", element.structureTests)
        .withElementAddedAs("instructionText", element.instructionText)
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
        serialized.getElementAs[Seq[ProgramStructureGrader]]("structureTests"),
        serialized.getElementAs[Option[LanguageMapContentId]]("instructionText"),
      )
  }
}
