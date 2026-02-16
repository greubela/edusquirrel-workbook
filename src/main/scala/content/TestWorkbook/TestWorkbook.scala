package content.TestWorkbook

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import contentmanagement.model.language.{AppLanguage, LanguageMap}
import contentmanagement.model.language.AppLanguage.English
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.blockEnvironment.exercise.{HtmlProgrammingExercise, ProgrammingExercise}
import interactionPlugins.gpt.{HtmlTextBasedGptExercise, TextBasedGptExercise}
import interactionPlugins.visualNovel.VisualNovelContent
import workbook.workbookHtmlElements.container.HtmlFullScreenElement

class TestWorkbook(fullscreenElement: HtmlFullScreenElement) extends HtmlAppElement {

  private val domElement: Element = {
    // Generic GPT Exercise
    val testEx = TextBasedGptExercise(
      "id-007",
      LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "this is title")),
      LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "this is instruction"))
      )
    val htmlEx = HtmlTextBasedGptExercise(testEx, fullscreenElement = fullscreenElement)

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
  }

  override def getDomElement(): L.Element = domElement
}
