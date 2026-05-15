package content

import datastructures.web.file.FileDescription
import interactionPlugins.gpt.GptInteractionLine
import interactionPlugins.turtleStitchPlugin.{TurtleStitchExploreProjectExercise, TurtleStitchRecreateShapeExercise}
import it.evadid.core.datastructures.language.AppLanguage.*
import workbook.htmlElements.basic.*
import workbook.model.*
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo

case class CreateEmbroideryWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {


  override val availableLanguages: List[HumanLanguage] = List(German, English, Spanish, French, Ukrainian, Danish, Turkish)

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


  private def createExploreExerciseDownloadInteraction(filename: String): HtmlWorkbookElement = {
    val fileDesc = FileDescription.relativeToResourceFolder("workbookresources/embroidery/existingProjects/" + filename + ".xml")
    TurtleStitchExploreProjectExercise.createElementLine(fullInfo, fileDesc)
  }

  private def createRecreateShapeUploadInteraction(imageName: String): HtmlWorkbookElement = {
    val fileDesc = FileDescription.relativeToResourceFolder("workbookresources/embroidery/desiredShapes/" + imageName + ".png")
    val imgElement = HtmlImageElement(fileDesc, fullInfo)
    TurtleStitchRecreateShapeExercise.createInteractionElement(fullInfo, nextId("recreateShape"), imgElement)
  }

  private def image(imageName: String, imgType: String = "png"): HtmlWorkbookElement = {
    image(FileDescription.relativeToResourceFolder("workbookresources/embroidery/images/" + imageName + "." + imgType))
  }

  private lazy val firstSection: WorkbookSection = {

    val textInputGpt1 = createTextInput()

    section(
      "EmbroideryWorkbook/section1Title",
      List(
        container(List(
          HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/Ex1Title"),
          instructionHtml("EmbroideryWorkbook/Ex1Instr1"),
          createExploreExerciseDownloadInteraction("simple_forward"),
          instructionHtml("EmbroideryWorkbook/Ex1Instr2"),
          textInputGpt1,
          GptInteractionLine(fullInfo, textInputGpt1, "EmbroideryWorkbook/Ex1Instr1", Some("EmbroideryWorkbook/Ex1Instr1Scaff"), None),
          instructionHtml("EmbroideryWorkbook/Ex1Instr3"),
          checklist("EmbroideryWorkbook/ConfirmSteps"),
          instructionHtml("EmbroideryWorkbook/Ex1Instr4"),
          createTextInput(),
          instructionHtml("EmbroideryWorkbook/Ex1Instr5"),
          createTextInput(),
        )),
        container(List(
          HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/Ex2Title"),
          instructionHtml("EmbroideryWorkbook/Ex2Instr1"),
          createExploreExerciseDownloadInteraction("reset_forward"),
          instructionHtml("EmbroideryWorkbook/Ex2Instr2"),
          createTextInput(),
          instructionHtml("EmbroideryWorkbook/Ex2Instr3"),
          createTextInput(),
          instructionHtml("EmbroideryWorkbook/Ex2Instr4"),
          createTextInput(),
        )),
        container(List(
          HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/Ex3Title"),
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
      "EmbroideryWorkbook/section2Title",
      List(
        container(List(
          HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/S2E1Title"),

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
        container(List(
          HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/S2E2Title"),
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
        container(List(
          HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/S2E3Title"),
          instructionHtml("EmbroideryWorkbook/RecreateShape"),
          createRecreateShapeUploadInteraction("two_squares"),
          instructionHtml("EmbroideryWorkbook/RecreateShape"),
          createRecreateShapeUploadInteraction("four_squares"),
          instructionHtml("EmbroideryWorkbook/RecreateShape"),
          createRecreateShapeUploadInteraction("five_triangles"),
          instructionHtml("EmbroideryWorkbook/RecreateShape"),
          createRecreateShapeUploadInteraction("pinwheel"),
        )),
        container(List(
          HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/S2E4Title"),
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
    "EmbroideryWorkbook/section3Title",
    List(
      container(List(
        HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/S3E1Title"),
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
      container(List(
        HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/S3E2Title"),

        instructionHtml("EmbroideryWorkbook/RecreateShapeWithBlocks"),
        createRecreateShapeUploadInteraction("five_triangles"),
        instructionHtml("EmbroideryWorkbook/RecreateShapeWithBlocks"),
        createRecreateShapeUploadInteraction("repeat_large"),
        instructionHtml("EmbroideryWorkbook/S3E2I1"),
        createTextInput()
      )),
      container(List(
        HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/S3E3Title"),

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
      "EmbroideryWorkbook/section4Title",
      List(
        container(List(
          HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/S4E1Title"),

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
        container(List(
          HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/S4E2Title"),

          instructionHtml("EmbroideryWorkbook/RecreateShapeWithCounting"),
          createRecreateShapeUploadInteraction("houses_larger"),

          instructionHtml("EmbroideryWorkbook/RecreateShapeWithCounting"),
          createRecreateShapeUploadInteraction("circles_larger"),
        ))
      )
    )


  private lazy val introSection: WorkbookSection =
    section(
      "EmbroideryWorkbook/section0Title",
      List(
        container(List(
          HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/S0E1Title"),
          instructionHtml("EmbroideryWorkbook/S0E1I1"),
          instructionHtml("EmbroideryWorkbook/S0E1I2"),
          instructionHtml("EmbroideryWorkbook/S0E1I3"),
        ))
      ))

  private lazy val fifthSection: WorkbookSection =
    section(
      "EmbroideryWorkbook/section5Title",
      List(
        container(List(
          HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/S5E1Title"),

          instructionHtml("EmbroideryWorkbook/S5E1I1"),
          //HtmlButtonElement.withTextLabel(fullInfo, "EmbroideryWorkbook/downloadButton", _ => DownloadHelper.downloadFromUrl("workbook.pdf", URL("https://evadid.it/edusquirrel/resources/workbookpdfs/20250402StickmaschineArbeitsheft.pdf")))

        ))
      ))

  private lazy val sixthSection: WorkbookSection =
    section(
      "EmbroideryWorkbook/section6Title",
      List(
        container(List(
          HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/S6E1Title"),

          instructionHtml("EmbroideryWorkbook/S6E1I1"),

        ))
      ))


  private lazy val finalSection: WorkbookSection =
    section(
      "EmbroideryWorkbook/section7Title",
      List(
        container(List(
          HtmlContainerTitle(fullInfo, "EmbroideryWorkbook/S7E1Title"),
          instructionHtml("EmbroideryWorkbook/S7E1I1"),
          instructionHtml("EmbroideryWorkbook/S7E1I2"),
          instructionHtml("EmbroideryWorkbook/S7E1I3"),
          instructionHtml("EmbroideryWorkbook/S7E1I4"),
          createRecreateShapeUploadInteraction("empty"),

        ))
      ))


}
