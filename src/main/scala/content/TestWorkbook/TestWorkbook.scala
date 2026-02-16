package content.TestWorkbook

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import contentmanagement.model.language.AppLanguage
import contentmanagement.model.language.AppLanguage.English
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.blockEnvironment.exercise.{HtmlProgrammingExercise, ProgrammingExercise}
import interactionPlugins.gpt.{HtmlTextBasedGptExercise, TextBasedGptExercise}
import workbook.workbookHtmlElements.container.HtmlFullScreenElement

class TestWorkbook(fullscreenElement: HtmlFullScreenElement) extends HtmlAppElement {

  private val domElement: Element = {
    // Generic GPT Exercise
    val testEx = TextBasedGptExercise("id-007", Map(AppLanguage.English -> "this is title"), Map(AppLanguage.English -> "this is instruction"))
    val htmlEx = HtmlTextBasedGptExercise(testEx, fullscreenElement = fullscreenElement)

    // Programming Exercise
    val testProgEx = ProgrammingExercise(
      "id-003",
      Map(AppLanguage.English -> "Exercise 2"),
      Map(AppLanguage.English -> "Use Turtle Commands to program the Shape on the right :-)"),
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
      htmlProgEx.getDomElement(),
    )
  }

  override def getDomElement(): L.Element = domElement
}
