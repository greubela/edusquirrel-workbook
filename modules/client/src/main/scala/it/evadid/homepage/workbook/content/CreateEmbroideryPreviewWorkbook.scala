package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.*
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.interactionElements.Turtle.{TurtleRecreateShapeInteraction, TurtleStitchExploreProjectElement}
import it.evadid.workbook.elements.interactionElements.gpt.GptInteractionElement
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStatePythonString
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}

/** Separate digital edition: preserves the original workbook and its saved responses. */
case class CreateEmbroideryPreviewWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {

  override val availableLanguages: List[HumanLanguage] = List(German, English)

  override val workbookId: String = "EmbroideryPreviewWorkbook"

  override lazy val createWorkbook: Workbook = {
    workbook(
      "embroiderypreviewworkbook/workbookTitle",
      List(
        introSection,
        firstSection,
        secondSection,
        thirdSection,
        fourthSection,
        fifthSection,
        sixthSection,
        finalSection
      ),
      User.AndreGreubel
    )
  }

  private def createExploreExerciseDownloadInteraction(filename: String): WorkbookElement = {
    val fileRelToResources = "workbookresources/embroidery/existingProjects/" + filename + ".xml"
    TurtleStitchExploreProjectElement(nextId("exploreProject"), fileRelToResources)
  }

  private def recreate(id: String, target: TurtleGraphic,
                       initialCode: String = ""): TurtleRecreateShapeInteraction =
    TurtleRecreateShapeInteraction(id, ProgrammingStatePythonString(initialCode), target,
      ProgrammingEditorPalette.Embroidery, Map.empty[String, Integer])

  private def createRecreateShapeInteraction(shape: String): WorkbookElement =
    recreate(nextId("embroidery-preview-" + shape), EmbroideryPreviewTargets.shape(shape))


  private lazy val firstSection: WorkbookSection = {

    val textInputGpt1 = createTextInput()

    section(
      "Section1",
      "embroiderypreviewworkbook/section1Title",
      List(
        container("embroiderypreviewworkbook/Ex1Title", List(
          instructionHtml("embroiderypreviewworkbook/Ex1Instr1"),
          createExploreExerciseDownloadInteraction("simple_forward"),
          instructionHtml("embroiderypreviewworkbook/Ex1Instr2"),
          textInputGpt1,
          GptInteractionElement("gpt-ex1instr2", textInputGpt1, LanguageMapContentId("embroiderypreviewworkbook/Ex1Instr1"), List(LanguageMapContentId("embroiderypreviewworkbook/Ex1Instr1Scaff")), List()),
          instructionHtml("embroiderypreviewworkbook/Ex1Instr3"),
          checklist("embroiderypreviewworkbook/ConfirmSteps"),
          instructionHtml("embroiderypreviewworkbook/Ex1Instr4"),
          createTextInput(),
          instructionHtml("embroiderypreviewworkbook/Ex1Instr5"),
          createTextInput(),
        )),
        container("embroiderypreviewworkbook/Ex2Title", List(
          instructionHtml("embroiderypreviewworkbook/Ex2Instr1"),
          createExploreExerciseDownloadInteraction("reset_forward"),
          instructionHtml("embroiderypreviewworkbook/Ex2Instr2"),
          createTextInput(),
          instructionHtml("embroiderypreviewworkbook/Ex2Instr3"),
          createTextInput(),
          instructionHtml("embroiderypreviewworkbook/Ex2Instr4"),
          createTextInput(),
        )),
        container("embroiderypreviewworkbook/Ex3Title", List(
          instructionHtml("embroiderypreviewworkbook/Ex3Instr1"),
          createExploreExerciseDownloadInteraction("updown_forward"),
          instructionHtml("embroiderypreviewworkbook/Ex3Instr2"),
          createTextInput(),
          instructionHtml("embroiderypreviewworkbook/Ex3Instr3"),
          createTextInput()
        )),
      )
    )
  }


  private lazy val secondSection: WorkbookSection = {

    val gptText1 = createTextInput("gpt-ex-1")

    section(
      "Section2",
      "embroiderypreviewworkbook/section2Title",
      List(
        container("embroiderypreviewworkbook/S2E1Title", List(
          instructionHtml("embroiderypreviewworkbook/RecreateShape"),
          createRecreateShapeInteraction("square"),

          instructionHtml("embroiderypreviewworkbook/S2E1I0"),
          gptText1,
          GptInteractionElement("gpt-s2i1", gptText1, LanguageMapContentId("embroiderypreviewworkbook/S2E1I0"), List(LanguageMapContentId("embroiderypreviewworkbook/S2E1I0Scaff")), List()),

          instructionHtml("embroiderypreviewworkbook/AnalyzeProgram"),
          createExploreExerciseDownloadInteraction("simple_repeat"),

          instructionHtml("embroiderypreviewworkbook/S2E1I1"),
          createTextInput(),
          instructionHtml("embroiderypreviewworkbook/S2E1I2"),
          createTextInput(),
          instructionHtml("embroiderypreviewworkbook/S2E1I3"),
          createTextInput(),
          instructionHtml("embroiderypreviewworkbook/S2E1I4"),
          createTextInput(),
        )),
        container("embroiderypreviewworkbook/S2E2Title", List(
          instructionHtml("embroiderypreviewworkbook/AnalyzeProgram"),
          createExploreExerciseDownloadInteraction("complex_repeat"),

          instructionHtml("embroiderypreviewworkbook/S2E2I1"),
          createTextInput(),
          instructionHtml("embroiderypreviewworkbook/S2E2I2"),
          createTextInput(),
          instructionHtml("embroiderypreviewworkbook/S2E2I3"),
          createTextInput(),
          instructionHtml("embroiderypreviewworkbook/S2E2I4"),
          createTextInput(),
        )),
        container("embroiderypreviewworkbook/S2E3Title", List(
          instructionHtml("embroiderypreviewworkbook/RecreateShape"),
          createRecreateShapeInteraction("two_squares"),
          instructionHtml("embroiderypreviewworkbook/RecreateShape"),
          createRecreateShapeInteraction("four_squares"),
          instructionHtml("embroiderypreviewworkbook/RecreateShape"),
          createRecreateShapeInteraction("five_triangles"),
          instructionHtml("embroiderypreviewworkbook/RecreateShape"),
          createRecreateShapeInteraction("pinwheel"),
        )),
        container("embroiderypreviewworkbook/S2E4Title", List(
          instructionHtml("embroiderypreviewworkbook/S2E4I1"),
          instructionHtml("embroiderypreviewworkbook/RecreateShape"),
          createRecreateShapeInteraction("squarewheel"),
          instructionHtml("embroiderypreviewworkbook/RecreateShape"),
          createRecreateShapeInteraction("blocky_eight"),
          instructionHtml("embroiderypreviewworkbook/RecreateShape"),
          createRecreateShapeInteraction("repeat_large"),
        )),
      )
    )
  }

  private lazy val thirdSection: WorkbookSection = section(
    "Section3",
    "embroiderypreviewworkbook/section3Title",
    List(
      container("embroiderypreviewworkbook/S3E1Title", List(
        instructionHtml("embroiderypreviewworkbook/S3E1I1"),
        checklist("embroiderypreviewworkbook/ConfirmSteps"),

        instructionHtml("embroiderypreviewworkbook/S3E1I2"),
        imageResources(LanguageMapContentId("embroiderypreviewworkbook/fileBlockSquare")),
        checklist("embroiderypreviewworkbook/ConfirmSteps"),

        instructionHtml("embroiderypreviewworkbook/S3E1I3"),
        imageResources(LanguageMapContentId("embroiderypreviewworkbook/fileBlockUseSquare")),
        checklist("embroiderypreviewworkbook/ConfirmSteps"),

        instructionHtml("embroiderypreviewworkbook/S3E1I4"),
        createTextInput(),
      )),
      container("embroiderypreviewworkbook/S3E2Title", List(

        instructionHtml("embroiderypreviewworkbook/RecreateShapeWithBlocks"),
        createRecreateShapeInteraction("five_triangles"),
        instructionHtml("embroiderypreviewworkbook/RecreateShapeWithBlocks"),
        createRecreateShapeInteraction("repeat_large"),
        instructionHtml("embroiderypreviewworkbook/S3E2I1"),
        createTextInput()
      )),
      container("embroiderypreviewworkbook/S3E3Title", List(

        instructionHtml("embroiderypreviewworkbook/S3E3I1"),
        imageResources(LanguageMapContentId("embroiderypreviewworkbook/fileParameterCreate")),

        instructionHtml("embroiderypreviewworkbook/S3E3I2"),
        imageResources(LanguageMapContentId("embroiderypreviewworkbook/fileParameterName")),

        instructionHtml("embroiderypreviewworkbook/S3E3I3"),
        createTextInput(),

        instructionHtml("embroiderypreviewworkbook/RecreateShapeWithBlocks"),
        createRecreateShapeInteraction("houses_larger"),
      ))
    ))


  private lazy val fourthSection: WorkbookSection =
    section(
      "Section4",
      "embroiderypreviewworkbook/section4Title",
      List(
        container("embroiderypreviewworkbook/S4E1Title", List(

          instructionHtml("embroiderypreviewworkbook/S4E1I1"),

          instructionHtml("embroiderypreviewworkbook/AnalyzeProgram"),
          createExploreExerciseDownloadInteraction("parameter_error"),

          instructionHtml("embroiderypreviewworkbook/S4E1I2"),
          createTextInput(),

          instructionHtml("embroiderypreviewworkbook/S4E1I3"),
          createTextInput(),

          instructionHtml("embroiderypreviewworkbook/S4E1I4"),
          checklist("embroiderypreviewworkbook/ConfirmSteps"),

          instructionHtml("embroiderypreviewworkbook/S4E1I5"),
          createTextInput(),

          instructionHtml("embroiderypreviewworkbook/S4E1I6"),
          createTextInput(),
        )),
        container("embroiderypreviewworkbook/S4E2Title", List(

          instructionHtml("embroiderypreviewworkbook/RecreateShapeWithCounting"),
          createRecreateShapeInteraction("houses_larger"),

          instructionHtml("embroiderypreviewworkbook/RecreateShapeWithCounting"),
          createRecreateShapeInteraction("circles_larger"),
        ))
      )
    )


  private lazy val introSection: WorkbookSection =
    section(
      "SectionIntro",
      "embroiderypreviewworkbook/section0Title",
      List(
        container("embroiderypreviewworkbook/S0E1Title", List(
          instructionHtml("embroiderypreviewworkbook/S0E1I1"),
          instructionHtml("embroiderypreviewworkbook/S0E1I2"),
          instructionHtml("embroiderypreviewworkbook/S0E1I3"),
        ))
      ))

  private def prompt(name: String): WorkbookElement = instructionMarkdown("embroiderypreviewworkbook/" + name)
  private def answer(id: String): WorkbookElement = createTextInput("embroidery-preview-" + id)

  private lazy val fifthSection: WorkbookSection = section("Section5", "embroiderypreviewworkbook/section5Title", List(
    container("embroiderypreviewworkbook/S5E1Title", List(prompt("S5Modulo")) ++
      (1 to 7).toList.map(i => numberInput("embroiderypreviewworkbook/modulo" + i,
        it.evadid.workbook.elements.interactionElements.basic.LabeledNumberInteraction.NumberType.IntegerLike,
        defaultValue = "", elementId = "embroidery-preview-modulo-" + i))),
    container("embroiderypreviewworkbook/S5E2Title", List(
      prompt("S5Predict"), answer("condition-prediction"),
      prompt("S5Example"), recreate("embroidery-preview-condition-example", EmbroideryPreviewTargets.alternatingShapes,
        EmbroideryPreviewTargets.conditionExample),
      prompt("S5Explain"), answer("condition-explanation"))),
    container("embroiderypreviewworkbook/S5NestedTitle", List(
      prompt("S5NestedPredict"), answer("nested-condition-prediction"),
      prompt("S5NestedRun"), recreate("embroidery-preview-nested-condition", EmbroideryPreviewTargets.nestedShapes,
        EmbroideryPreviewTargets.nestedExample),
      prompt("S5NestedExplain"), answer("nested-condition-explanation"))),
    container("embroiderypreviewworkbook/S5E3Title", List(
      prompt("S5Alternate"), recreate("embroidery-preview-alternate", EmbroideryPreviewTargets.housePattern(2)),
      prompt("S5EveryFourth"), recreate("embroidery-preview-every-fourth", EmbroideryPreviewTargets.housePattern(4)),
      prompt("S5Reflect"), answer("condition-reflection")))
  ))

  private lazy val sixthSection: WorkbookSection = section("Section6", "embroiderypreviewworkbook/section6Title", List(
    container("embroiderypreviewworkbook/S6E1Title", List(
      prompt("S6Introduction"), prompt("S6Run"),
      recreate("embroidery-preview-koch-example", EmbroideryPreviewTargets.koch(0, 50), EmbroideryPreviewTargets.kochExample),
      prompt("S6Predict"), answer("koch-prediction"))),
    container("embroiderypreviewworkbook/S6E2Title", List(prompt("S6Levels")) ++ (0 to 3).toList.flatMap { depth =>
      List(prompt("level" + depth), recreate("embroidery-preview-koch-growing-" + depth,
        EmbroideryPreviewTargets.koch(depth, 50 * math.pow(3, depth))))
    }),
    container("embroiderypreviewworkbook/S6E3Title", List(prompt("S6Length")) ++ (1 to 3).toList.flatMap { depth =>
      List(prompt("level" + depth), recreate("embroidery-preview-koch-fixed-" + depth, EmbroideryPreviewTargets.koch(depth, 270)))
    } ++ List(prompt("S6LengthExplain"), answer("koch-length-explanation"))),
    container("embroiderypreviewworkbook/S6E4Title", List(prompt("S6Snowflake")) ++ (0 to 3).toList.flatMap { depth =>
      List(prompt("level" + depth), recreate("embroidery-preview-snowflake-" + depth, EmbroideryPreviewTargets.snowflake(depth)))
    }),
    container("embroiderypreviewworkbook/S6E5Title", List(
      prompt("S6Branches"), recreate("embroidery-preview-branches", EmbroideryPreviewTargets.branches),
      prompt("S6Termination"), answer("recursion-termination")))
  ))

  private lazy val finalSection: WorkbookSection =
    section(
      "Section7",
      "embroiderypreviewworkbook/section7Title",
      List(
        container("embroiderypreviewworkbook/S7E1Title",
          List(
            instructionHtml("embroiderypreviewworkbook/S7E1I1"),
            instructionHtml("embroiderypreviewworkbook/S7E1I2"),
            instructionHtml("embroiderypreviewworkbook/S7E1I3"),
            instructionHtml("embroiderypreviewworkbook/S7E1I4"),
            ProgrammingExercise("embroidery-preview-masterpiece", editorPalette = ProgrammingEditorPalette.Embroidery),
            prompt("S7DesignReflection"), answer("masterpiece-reflection"),
            checklist("embroiderypreviewworkbook/S7Ready", "embroidery-preview-ready-to-stitch"),
          ))
      ))


}
