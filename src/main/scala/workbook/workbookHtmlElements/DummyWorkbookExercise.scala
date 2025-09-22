package workbook.workbookHtmlElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.AppLanguage
import workbook.model.exercise.ExerciseContent
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.container.HtmlFullInteractionContainerVboxMini
import workbook.workbookHtmlElements.interactions.TextBasedGptInteraction

case class DummyWorkbookExercise() {

  def getExerciseElements(): List[HtmlWorkbookElement] = {


    val sampleExercise = ExerciseContent("id-007", "this is instruction")

    val sampleInteraction = TextBasedGptInteraction("init text goes here I assume???")


    val res = List(

      HtmlExerciseTitleElement(Map(AppLanguage.German -> "Übung 1", AppLanguage.English -> "Exercise 1")),
      HtmlPlaintextInstructionElement(Map(AppLanguage.German -> "Das ist die Aufgabe: Mach das jetzt sofort", AppLanguage.English -> "This is Exercise Text")),
      // FullTextBasedExerciseElement("dummy-exercise", null, TextBasedDummyScaffolder, TextBasedDummyGrader)
      HtmlFullInteractionContainerVboxMini(sampleExercise, sampleInteraction)

    )

    res

  }

  def createDomElement(): Element = div(cls := "container-exercise style-vbox",
    getExerciseElements().map(_.getDomElement())
  )


}
