package interactionPlugins.visualNovel

import com.raquo.laminar.api.L.*
import contentmanagement.model.image.ImageDescription.ServerImageDescription

import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

/*
case class VisualNovelExercise(
                                exerciseContent: ExerciseWithTitleDescription,
                                panels: List[VisualNovelPanel]
                                // ,imageMap: Map[ImageDescription, FullImage]
                              ) {

  private val pageHistorySerializer = new util.Serializer[List[Int]] {
    override def serialize(obj: List[Int]): String = obj.mkString(",")

    override def deserialize(str: String): List[Int] =
      if (str.isBlank) List() else str.split(",").map(_.toInt).toList
  }
  private val initialPageHistory = if (panels.nonEmpty) List(1) else List()
  private val pageHistoryVariable = InteractionVariable(exerciseContent, initialPageHistory, pageHistorySerializer)

  val pageHistory: ExerciseInteractionHistory[List[Int]] = new ExerciseInteractionHistory[List[Int]] {
    override def underlyingExercise: ExerciseWithTitleDescription = exerciseContent

    override def editorStateVariable: InteractionVariable[List[Int]] = pageHistoryVariable
  }

  private val currentIndex = Var(0)
  val currentPanelSignal: Signal[VisualNovelPanel] = currentIndex.signal.map(panels)

  private def totalPanels: Int = panels.length

  def getDomElement(): Element = domElement

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
      onClick --> { _ => navigateBy(-1) }
    ),
    span(
      cls := "visual-novel-counter",
      child.text <-- currentIndex.signal.map(i => s"${i + 1}/$totalPanels")
    ),
    button(
      "→",
      disabled <-- currentIndex.signal.map(_ >= totalPanels - 1),
      onClick --> { _ => navigateBy(1) }
    )
  )

  private def navigateBy(offset: Int): Unit = {
    val oldIndex = currentIndex.now()
    val newIndex = (oldIndex + offset).max(0).min(totalPanels - 1)
    if (newIndex != oldIndex) {
      currentIndex.update(_ => newIndex)

      val currentHistory = pageHistory.editorStateVariable.asVar.now()
      val updatedHistory = currentHistory :+ (newIndex + 1)
      pageHistory.editorStateVariable.updateStateFromUserInteraction(updatedHistory, System.currentTimeMillis(), UpdateImportance.MAJOR)
    }
  }

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
*/

