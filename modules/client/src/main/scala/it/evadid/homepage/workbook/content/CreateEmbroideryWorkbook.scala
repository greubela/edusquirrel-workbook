package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.info.FullInfo
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.*
import it.evadid.workbook.model.interaction.plugins.TurtleStitch.{TurtleStitchExploreProjectElement, TurtleStitchRecreateShapeInteraction}
import it.evadid.workbook.model.interaction.plugins.gpt.GptInteractionElement
import todomove.datastructures.web.file.FileFactory

case class CreateEmbroideryWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {

  override val availableLanguages: List[HumanLanguage] = List(German, English, Spanish, French, Ukrainian, Danish, Turkish)

  override val workbookId: String = "EmbroideryWorkbook" // todo: Lang map should automatically prefix this

  override lazy val createWorkbook: Workbook = {
    workbook(
      "EmbroideryWorkbook/workbookTitle",
      List(
        introSection,
        firstSection,
        secondSection,
        thirdSection,
        fourthSection,
        fifthSection,
        //sixthSection,
        finalSection
      )
    )
  }

  private def createExploreExerciseDownloadInteraction(filename: String): WorkbookElement = {
    val fileDesc = FileFactory.relativeToResourceFolder("workbookresources/embroidery/existingProjects/" + filename + ".xml")
    //TurtleStitchExploreProjectExercise.createElementLine(fullInfo, fileDesc)
    TurtleStitchExploreProjectElement(fileDesc)
  }

  private def createRecreateShapeUploadInteraction(imageName: String): WorkbookElement = {
    val fileDesc = FileFactory.relativeToResourceFolder("workbookresources/embroidery/desiredShapes/" + imageName + ".png")
    //val imgElement = HtmlImageElement(fileDesc, fullInfo)
    //TurtleStitchRecreateShapeExercise.createInteractionElement(fullInfo, nextId("recreateShape"), imgElement)
    TurtleStitchRecreateShapeInteraction(nextId("recreateShape"), fileDesc)
  }


  private lazy val firstSection: WorkbookSection = {

    val textInputGpt1 = createTextInput()

    section(
      "Section1",
      "EmbroideryWorkbook/section1Title",
      List(
        container("EmbroideryWorkbook/Ex1Title", List(
          instructionHtml("EmbroideryWorkbook/Ex1Instr1"),
          createExploreExerciseDownloadInteraction("simple_forward"),
          instructionHtml("EmbroideryWorkbook/Ex1Instr2"),
          textInputGpt1,
          GptInteractionElement("gpt-ex1instr2", textInputGpt1, LanguageMapContentId("EmbroideryWorkbook/Ex1Instr1"), List(LanguageMapContentId("EmbroideryWorkbook/Ex1Instr1Scaff")), List()),
          instructionHtml("EmbroideryWorkbook/Ex1Instr3"),
          checklist("EmbroideryWorkbook/ConfirmSteps"),
          instructionHtml("EmbroideryWorkbook/Ex1Instr4"),
          createTextInput(),
          instructionHtml("EmbroideryWorkbook/Ex1Instr5"),
          createTextInput(),
        )),
        container("EmbroideryWorkbook/Ex2Title", List(
          instructionHtml("EmbroideryWorkbook/Ex2Instr1"),
          createExploreExerciseDownloadInteraction("reset_forward"),
          instructionHtml("EmbroideryWorkbook/Ex2Instr2"),
          createTextInput(),
          instructionHtml("EmbroideryWorkbook/Ex2Instr3"),
          createTextInput(),
          instructionHtml("EmbroideryWorkbook/Ex2Instr4"),
          createTextInput(),
        )),
        container("EmbroideryWorkbook/Ex3Title", List(
          instructionHtml("EmbroideryWorkbook/Ex3Instr1"),
          createExploreExerciseDownloadInteraction("updown_forward"),
          instructionHtml("EmbroideryWorkbook/Ex3Instr2"),
          createTextInput(),
          instructionHtml("EmbroideryWorkbook/Ex3Instr3"),
          createTextInput()
        )),
      )
    )
  }


  private lazy val secondSection: WorkbookSection =
    section(
      "Section2",
      "EmbroideryWorkbook/section2Title",
      List(
        container("EmbroideryWorkbook/S2E1Title", List(
          instructionHtml("EmbroideryWorkbook/RecreateShape"),
          createRecreateShapeUploadInteraction("square"),

          instructionHtml("EmbroideryWorkbook/AnalyzeProgram"),
          createExploreExerciseDownloadInteraction("simple_repeat"),

          instructionHtml("EmbroideryWorkbook/S2E1I1"),
          createTextInput(),
          instructionHtml("EmbroideryWorkbook/S2E1I2"),
          createTextInput(),
          instructionHtml("EmbroideryWorkbook/S2E1I3"),
          createTextInput(),
          instructionHtml("EmbroideryWorkbook/S2E1I4"),
          createTextInput(),
        )),
        container("EmbroideryWorkbook/S2E2Title", List(
          instructionHtml("EmbroideryWorkbook/AnalyzeProgram"),
          createExploreExerciseDownloadInteraction("complex_repeat"),

          instructionHtml("EmbroideryWorkbook/S2E2I1"),
          createTextInput(),
          instructionHtml("EmbroideryWorkbook/S2E2I2"),
          createTextInput(),
          instructionHtml("EmbroideryWorkbook/S2E2I3"),
          createTextInput(),
          instructionHtml("EmbroideryWorkbook/S2E2I4"),
          createTextInput(),
        )),
        container("EmbroideryWorkbook/S2E3Title", List(
          instructionHtml("EmbroideryWorkbook/RecreateShape"),
          createRecreateShapeUploadInteraction("two_squares"),
          instructionHtml("EmbroideryWorkbook/RecreateShape"),
          createRecreateShapeUploadInteraction("four_squares"),
          instructionHtml("EmbroideryWorkbook/RecreateShape"),
          createRecreateShapeUploadInteraction("five_triangles"),
          instructionHtml("EmbroideryWorkbook/RecreateShape"),
          createRecreateShapeUploadInteraction("pinwheel"),
        )),
        container("EmbroideryWorkbook/S2E4Title", List(
          instructionHtml("EmbroideryWorkbook/S2E4I1"),
          instructionHtml("EmbroideryWorkbook/RecreateShape"),
          createRecreateShapeUploadInteraction("squarewheel"),
          instructionHtml("EmbroideryWorkbook/RecreateShape"),
          createRecreateShapeUploadInteraction("blocky_eight"),
          instructionHtml("EmbroideryWorkbook/RecreateShape"),
          createRecreateShapeUploadInteraction("repeat_large"),
        )),
      )
    )

  private lazy val thirdSection: WorkbookSection = section(
    "Section3",
    "EmbroideryWorkbook/section3Title",
    List(
      container("EmbroideryWorkbook/S3E1Title", List(
        instructionHtml("EmbroideryWorkbook/S3E1I1"),
        checklist("EmbroideryWorkbook/ConfirmSteps"),

        instructionHtml("EmbroideryWorkbook/S3E1I2"),
        image("block_square"),
        checklist("EmbroideryWorkbook/ConfirmSteps"),

        instructionHtml("EmbroideryWorkbook/S3E1I3"),
        image("block_usesquare"),
        checklist("EmbroideryWorkbook/ConfirmSteps"),

        instructionHtml("EmbroideryWorkbook/S3E1I4"),
        createTextInput(),
      )),
      container("EmbroideryWorkbook/S3E2Title", List(

        instructionHtml("EmbroideryWorkbook/RecreateShapeWithBlocks"),
        createRecreateShapeUploadInteraction("five_triangles"),
        instructionHtml("EmbroideryWorkbook/RecreateShapeWithBlocks"),
        createRecreateShapeUploadInteraction("repeat_large"),
        instructionHtml("EmbroideryWorkbook/S3E2I1"),
        createTextInput()
      )),
      container("EmbroideryWorkbook/S3E3Title", List(

        instructionHtml("EmbroideryWorkbook/S3E3I1"),
        image("parameter_create"),

        instructionHtml("EmbroideryWorkbook/S3E3I2"),
        image("parameter_name"),

        instructionHtml("EmbroideryWorkbook/S3E3I3"),
        createTextInput(),

        instructionHtml("EmbroideryWorkbook/RecreateShapeWithBlocks"),
        createRecreateShapeUploadInteraction("houses_larger"),
      ))
    ))


  private lazy val fourthSection: WorkbookSection =
    section(
      "Section4",
      "EmbroideryWorkbook/section4Title",
      List(
        container("EmbroideryWorkbook/S4E1Title", List(

          instructionHtml("EmbroideryWorkbook/S4E1I1"),

          instructionHtml("EmbroideryWorkbook/AnalyzeProgram"),
          createExploreExerciseDownloadInteraction("parameter_error"),

          instructionHtml("EmbroideryWorkbook/S4E1I2"),
          createTextInput(),

          instructionHtml("EmbroideryWorkbook/S4E1I3"),
          createTextInput(),

          instructionHtml("EmbroideryWorkbook/S4E1I4"),
          checklist("EmbroideryWorkbook/ConfirmSteps"),

          instructionHtml("EmbroideryWorkbook/S4E1I5"),
          createTextInput(),

          instructionHtml("EmbroideryWorkbook/S4E1I6"),
          createTextInput(),
        )),
        container("EmbroideryWorkbook/S4E2Title", List(

          instructionHtml("EmbroideryWorkbook/RecreateShapeWithCounting"),
          createRecreateShapeUploadInteraction("houses_larger"),

          instructionHtml("EmbroideryWorkbook/RecreateShapeWithCounting"),
          createRecreateShapeUploadInteraction("circles_larger"),
        ))
      )
    )


  private lazy val introSection: WorkbookSection =
    section(
      "SectionIntro",
      "EmbroideryWorkbook/section0Title",
      List(
        container("EmbroideryWorkbook/S0E1Title", List(
          instructionHtml("EmbroideryWorkbook/S0E1I1"),
          instructionHtml("EmbroideryWorkbook/S0E1I2"),
          instructionHtml("EmbroideryWorkbook/S0E1I3"),
        ))
      ))

  private lazy val fifthSection: WorkbookSection =
    section(
      "Section5",
      "EmbroideryWorkbook/section5Title",
      List(
        container("EmbroideryWorkbook/S5E1Title", List(
          instructionHtml("EmbroideryWorkbook/S5E1I1"),
          //HtmlButtonElement.withTextLabel(fullInfo, "EmbroideryWorkbook/downloadButton", _ => DownloadHelper.downloadFromUrl("workbook.pdf", URL("https://evadid.it/edusquirrel/resources/workbookpdfs/20250402StickmaschineArbeitsheft.pdf")))
        ))
      ))

  private lazy val sixthSection: WorkbookSection =
    section(
      "Section6",
      "EmbroideryWorkbook/section6Title",
      List(
        container(
          "EmbroideryWorkbook/S6E1Title",
          List(instructionHtml("EmbroideryWorkbook/S6E1I1"),
          ))
      ))


  private lazy val finalSection: WorkbookSection =
    section(
      "Section7",
      "EmbroideryWorkbook/section7Title",
      List(
        container("EmbroideryWorkbook/S7E1Title",
          List(
            instructionHtml("EmbroideryWorkbook/S7E1I1"),
            instructionHtml("EmbroideryWorkbook/S7E1I2"),
            instructionHtml("EmbroideryWorkbook/S7E1I3"),
            instructionHtml("EmbroideryWorkbook/S7E1I4"),
            createRecreateShapeUploadInteraction("empty"),
          ))
      ))


}
