package content.TestWorkbook

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import contentmanagement.model.image.ImageDescription.ServerImageDescription
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.blockEnvironment.exercise.{ProgrammingExerciseFactory, TurtleProgrammingInteraction}
import interactionPlugins.fileSubmission.TurtleStitchFileUploadFactory
import interactionPlugins.gpt.GptExerciseFactory
import workbook.model.*
import workbook.model.info.{WorkbookConfig, WorkbookInfo}
import workbook.user.User
import workbook.workbookHtmlElements.abstractions.WorkbookInteraction
import workbook.workbookHtmlElements.container.*

class TestWorkbook(fullscreenElement: HtmlFullScreenElement) extends HtmlAppElement {


  private val domElement: Element = div()
  /*{


    // VisualNovel:


    // Programming Exercise
    val testProgEx = ProgrammingExercise(
      "id-003",
      LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "Exercise 2")),
      LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "Use Turtle Commands to program the Shape on the right :-)")),
      ProgrammingExercise.DefaultPentagonExpectedResult
    )
    val htmlProgEx = HtmlProgrammingExercise(testProgEx, fullscreenElement)



    val combinedElement = div(
      // jsxGraphPreview,
      fullscreenElement.getDomElement(),
      /* div(
         h2("Workbook Overview"),
         div(
           cls := "workbook-overview-sample",
          // overviewElement
         )
       ),*/
      htmlEx.getDomElement(),
      htmlProgEx.getDomElement(),
    )

    div(
      htmlEx.getDomElement(),
      VisualNovelContent.monkContent.getDomElement(),
      htmlProgEx.getDomElement(),
    )
  }*/

  override def getDomElement(): L.Element = domElement
}

object TestWorkbook {

  private val defaultTitle: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
    AppLanguage.English -> "this is the title.",
    AppLanguage.German -> "Das ist der Titel."
  ))

  private val textInstruction1: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
    AppLanguage.English -> "Write a text!",
    AppLanguage.German -> "Schreibe einen Text!"
  ))

  private val textInstruction2: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
    AppLanguage.English -> "Write another text!",
    AppLanguage.German -> "Schreibe noch einen Text!"
  ))

  private def gptCont(workbookInfoVar: Var[WorkbookInfo]): HtmlExerciseContainer = {
    val gptElements = GptExerciseFactory.createGptExercise(
      workbookInfoVar,
      "text-007",
      defaultTitle,
      List(textInstruction1, textInstruction2)
    )
    HtmlExerciseContainer(workbookInfoVar, gptElements)
  }

  private def blockProgCont(workbookInfoVar: Var[WorkbookInfo]): HtmlExerciseContainer = {
    val progElements = ProgrammingExerciseFactory.createTurtleProgrammingExercise(workbookInfoVar, "prog-007", defaultTitle, ProgrammingExerciseFactory.DefaultPentagonExpectedResult)
    HtmlExerciseContainer(workbookInfoVar, progElements)
  }

  private def turtleProgCont(workbookInfoVar: Var[WorkbookInfo]): HtmlExerciseContainer = {

    val elements = TurtleStitchFileUploadFactory.createReprogramShapeExercise(
      workbookInfoVar,
      "ts-upload-007",
      TurtleStitchFileUploadFactory.languageMapDefaultExerciseTitle,
      ServerImageDescription("/resources/workbookresources/monks/Image01.jpg")
    )
    HtmlExerciseContainer(workbookInfoVar, elements)
  }


  def createTestSection(workbookInfoVar: Var[WorkbookInfo]): WorkbookSection = {


    val contList = List(
      gptCont(workbookInfoVar),
      turtleProgCont(workbookInfoVar),
      blockProgCont(workbookInfoVar)
    )


    val secTitle = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
      AppLanguage.English -> "This is section 1.",
      AppLanguage.German -> "Das ist Abschnitt 1."
    ))
    WorkbookSection(workbookInfoVar, secTitle, contList)
  }

  def createTestWorkbook(fullscreenElement: HtmlFullScreenElement): Workbook = {
    val defaultInfo = WorkbookInfo(fullscreenElement, WorkbookConfig(AppLanguage.German, User("TestUser", "dummy@test.de")), Map())
    val workbookInfoVar = Var(defaultInfo)

    val sec = createTestSection(workbookInfoVar)

    val title: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Test Workbook",
      AppLanguage.German -> "Beispielheft"
    ))

    Workbook(workbookInfoVar, title, List(sec))
  }


}
