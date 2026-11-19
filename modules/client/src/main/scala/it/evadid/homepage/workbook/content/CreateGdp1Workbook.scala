package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading.ContentForLlmInteraction
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonString
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{PaletteLayout, ProgrammingEditorPalette}
import it.evadid.workbook.elements.structureElements.Workbook

/** Progressive practice: explicit commands, loops, functions, then parameters. */
case class CreateGdp1Workbook(override val fullInfo: FullInfo) extends WorkbookFactory {
  override val workbookId = "2026GdP1"
  override val availableLanguages: Seq[HumanLanguage] = Seq(German, English)
  private def key(name: String) = s"2026gdp1/$name"
  private def content(name: String) = LanguageMapContentId(key(name))
  private val movement = Seq("forward", "turn")
  private val beginner = Seq("forward", "turn", "up", "down", "doRepeat", "doIfElse", "reportVariadicSum", "reportDifference", "reportVariadicProduct", "reportVariadicEquals")

  private def palette(selectors: Seq[String], functions: Boolean = false, variables: Boolean = false) =
    ProgrammingEditorPalette.Custom(PaletteLayout.Mixed(selectors, functions, variables))

  private def task(number: Int, target: TurtleGraphic, available: ProgrammingEditorPalette,
      requirements: Seq[(ProgramStructureRequirement, String)]): WorkbookElement = {
    val id = s"$workbookId-task-$number"
    val tests = requirements.zipWithIndex.map { case ((requirement, description), index) =>
      ProgramStructureGrader(requirement, s"$id-structure-$index", content(description))
    }
    container(key(s"task${number}Title"), Seq(
      instructionMarkdown(key(s"task${number}Instruction")),
      TurtleRecreateShapeInteraction(id, ProgrammingStatePythonString(""), target,
        customExerciseConfig = Some(RecreateTurtleDidacticConfig(Seq(SnapLanguage, Python), available, MaxCommandUsage.noBounds)),
        structureTests = tests, instructionText = Some(content(s"task${number}Instruction")))))
  }

  import ProgramStructureRequirement.*
  private val repetition = UsesLoop -> "testLoop"
  private val noFunctions = NoFunctions -> "testNoFunctions"
  private val squareFunction = DefinesFunction("quadrat") -> "testSquareFunction"

  override lazy val createWorkbook: Workbook = workbook(key("title"), Seq(
    section(s"$workbookId-overview", key("section0Title"), Seq(instructionMarkdown(key("overview")))),
    section(s"$workbookId-introduction", key("section1Title"), Seq(
      instructionMarkdown(key("turtleIntroduction")),
      task(1, CreateGdp1Workbook.square, palette(movement), Seq(NoLoops -> "testNoLoops", noFunctions, OnlyCommands(Seq("forward", "right")) -> "testOnlyCommands")),
      instructionMarkdown(key("loopIntroduction")),
      task(2, CreateGdp1Workbook.square, palette(movement :+ "doRepeat"), Seq(repetition, noFunctions)),
      instructionMarkdown(key("nestedLoopsIntroduction")),
      task(3, CreateGdp1Workbook.row(Seq.fill(5)(100)), palette(beginner), Seq(repetition, noFunctions)),
      instructionMarkdown(key("functionIntroduction")),
      task(4, CreateGdp1Workbook.row(Seq.fill(5)(100)), palette(beginner, functions = true), Seq(repetition, squareFunction)),
      container(key("reflectionTitle"), Seq(TextInputWithScaffoldingAndGrading(s"$workbookId-reflection", content("reflectionInstruction"),
        contentScaffolding = Some(ContentForLlmInteraction(content("reflectionScaffolding"), Map.empty)),
        contentGrading = Seq(ContentForLlmInteraction(content("reflectionGrading"), Map.empty)))))
    )),
    section(s"$workbookId-parameters", key("section2Title"), Seq(
      instructionMarkdown(key("parameterIntroduction")),
      task(5, CreateGdp1Workbook.row(Seq(100, 200, 300, 400, 500)), palette(beginner ++ Seq("doSetVar", "doChangeVar", "doFor"), functions = true, variables = true),
        Seq(repetition, DefinesFunction("quadrat", Seq("distanz")) -> "testDistanceParameter")),
      instructionMarkdown(key("alternationIntroduction")),
      task(6, CreateGdp1Workbook.row(Seq(100, 200, 100, 200, 100)), palette(beginner ++ Seq("doSetVar", "doChangeVar", "doFor"), functions = true, variables = true),
        Seq(repetition, DefinesFunction("quadrat", Seq("distanz")) -> "testDistanceParameter")),
      instructionMarkdown(key("orientationIntroduction")),
      task(7, CreateGdp1Workbook.row(Seq(100, 200, 100, 200, 100), Set(2)), palette(beginner ++ Seq("doSetVar", "doChangeVar", "doFor"), functions = true, variables = true),
        Seq(repetition, DefinesFunction("quadrat", Seq("distanz")) -> "testDistanceParameter"))
    ))), User.AndreGreubel)
}

object CreateGdp1Workbook {
  import TurtleCommand.*
  private def squareCommands(side: Int, down: Boolean = false): Seq[TurtleCommand[Double]] = {
    val direction = if down then -1.0 else 1.0
    (0 until 4).flatMap(_ => Seq(Forward(side * direction), Right(90 * direction)))
  }
  private[content] val square: TurtleGraphic = TurtleGraphic(squareCommands(100))

  /** Gaps are measured between neighboring square edges; pen-up travel is not graded as a stroke. */
  private[content] def row(sides: Seq[Int], down: Set[Int] = Set.empty): TurtleGraphic = TurtleGraphic(
    sides.zipWithIndex.flatMap { (side, index) =>
      squareCommands(side, down(index)) ++ Option.when(index < sides.size - 1)(Seq(
        PenUp(), Right(90.0), Forward(side + 100.0), Right(270.0), PenDown())).toSeq.flatten
    })
}
