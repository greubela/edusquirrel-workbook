package content.TestWorkbook

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.blockEnvironment.exercise.{ProgrammingExerciseFactory, TurtleProgrammingInteraction}
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


  def createTestSection(workbookInfoVar: Var[WorkbookInfo]): WorkbookSection = {

    val defaultTitle: LanguageMap[HumanLanguage] =   LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
      AppLanguage.English -> "this is the title.",
      AppLanguage.German -> "Das ist der Titel."
    ))

    // Text
    val gptElements = GptExerciseFactory.createGptExercise(
      workbookInfoVar,
      "text-007",
      defaultTitle,
      List(
        LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
          AppLanguage.English -> "Write a text!",
          AppLanguage.German -> "Schreibe einen Text!"
        ))
        ,
        LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
          AppLanguage.English -> "Write another text!",
          AppLanguage.German -> "Schreibe noch einen Text!"
        ))
      )
    )
    val cont1 = HtmlExerciseContainer(workbookInfoVar, gptElements)

    // Prog
    val progElements = ProgrammingExerciseFactory.createTurtleProgrammingExercise(workbookInfoVar, "prog-007", defaultTitle, ProgrammingExerciseFactory.DefaultPentagonExpectedResult)
    val cont2 = HtmlExerciseContainer(workbookInfoVar, progElements)

    val secTitle = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
      AppLanguage.English -> "This is section 1.",
      AppLanguage.German -> "Das ist Abschnitt 1."
    ))
    WorkbookSection(workbookInfoVar, secTitle, List(cont1, cont2))
  }

  def createTestWorkbook(fullscreenElement: HtmlFullScreenElement): Workbook = {
    val defaultInfo = WorkbookInfo(fullscreenElement, WorkbookConfig(AppLanguage.English, User("TestUser", "dummy@test.de")), Map())
    val workbookInfoVar = Var(defaultInfo)

    val sec = createTestSection(workbookInfoVar)

    val title: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Test Workbook",
      AppLanguage.German -> "Beispielheft"
    ))

    Workbook(workbookInfoVar, title, List(sec))
  }


}
