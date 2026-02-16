package interactionPlugins.visualNovel

import com.raquo.laminar.api.L.*
import contentmanagement.model.image.ImageDescription.ServerImageDescription
import contentmanagement.model.image.{FullImage, ImageDescription}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.storage.ImageStorage
import workbook.model.exercise.ExerciseContent
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.basic.{HtmlExerciseTitleElement, HtmlPlaintextInstructionElement}

import scala.collection.mutable

case class VisualNovelExercise(
                                exerciseContent: ExerciseContent,
                                panels: List[VisualNovelPanel]
                                // ,imageMap: Map[ImageDescription, FullImage]
                              ) extends HtmlWorkbookElement {

  private val currentIndex = Var(0)
  val currentPanelSignal: Signal[VisualNovelPanel] = currentIndex.signal.map(panels)

  private def totalPanels: Int = panels.length

  override def getDomElement(): Element = domElement

  // exercise information
  private val htmlTitleElement = HtmlExerciseTitleElement(exerciseContent.titleMap)

  private lazy val domElement: Element = div(cls := "container-exercise style-vbox",
    htmlTitleElement.getDomElement(),
    interactionDomElement,
    navigationElement
  )

  private lazy val navigationElement = div(
    cls := "visual-novel-navigation",
    button(
      "←",
      disabled <-- currentIndex.signal.map(_ == 0),
      onClick --> { _ => currentIndex.update(i => if (i > 0) i - 1 else i) }
    ),
    span(
      cls := "visual-novel-counter",
      child.text <-- currentIndex.signal.map(i => s"${i + 1}/$totalPanels")
    ),
    button(
      "→",
      disabled <-- currentIndex.signal.map(_ >= totalPanels - 1),
      onClick --> { _ => currentIndex.update(i => if (i < totalPanels - 1) i + 1 else i) }
    )
  )

  private lazy val interactionDomElement = {

    div(
      cls := "visual-novel-container",

      // Hauptbild
      img(
        cls := "visual-novel-image",
        alt <-- currentPanelSignal.map(_.description),
        //src <-- currentPanelSignal.map(panel => imageMap(panel.image).imgSourceString)
        src <-- currentPanelSignal.map(panelSignal => {
          val desc = panelSignal.image
          desc match {
            case ServerImageDescription(url) => url
            case _ => {
              println("VisualNovelExercise: Cannot handle description: " + desc)
              "???"
            }
          }
        })
      ),

      // Quelle klein darunter
      div(
        cls := "visual-novel-source",
        child.text <-- currentPanelSignal.map(panel => s"Quelle: ${panel.source}")
      ),

      // Text zum Bild (z.B. was gesagt wird)
      div(
        cls := "visual-novel-text",
        child.text <-- currentPanelSignal.map(_.textContent)
      ),

    )

  }
}




