package interactionPlugins.visualNovel

import com.raquo.laminar.api.L.*
import upickle.default.*
import workbook.htmlElements.basic.HtmlContainerTitle
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.WorkbookInfo
import workbook.model.interaction.history.UpdateImportance
import workbook.model.interaction.InteractionVariable
import util.Serializer
import contentmanagement.model.language.{HumanLanguage, LanguageMap}

case class VisualNovelPanelView(panelIndex: Int, viewedAtEpochMillis: Long)

case class VisualNovelExercise(workbookInfoVar: Var[WorkbookInfo],
                               id: String,
                               titleMap: LanguageMap[HumanLanguage],
                               panels: List[VisualNovelPanel]) extends WorkbookInteraction[Set[VisualNovelPanelView]] {

  require(panels.nonEmpty, "VisualNovelExercise requires at least one panel")

  private given ReadWriter[VisualNovelPanelView] = macroRW

  private val panelViewSerializer = new Serializer[Set[VisualNovelPanelView]] {
    override def serialize(obj: Set[VisualNovelPanelView]): String = write(obj.toList.sortBy(_.panelIndex))

    override def deserialize(str: String): Set[VisualNovelPanelView] = {
      if (str.isBlank) Set.empty else read[List[VisualNovelPanelView]](str).toSet
    }
  }

  private val currentIndex = Var(0)

  private def nowMillis: Long = System.currentTimeMillis()

  override val interactionVariable: InteractionVariable[Set[VisualNovelPanelView]] =
    InteractionVariable(
      this,
      Set(VisualNovelPanelView(panelIndex = 0, viewedAtEpochMillis = nowMillis)),
      panelViewSerializer
    )

  private val htmlTitleElement = HtmlContainerTitle(workbookInfoVar, titleMap)

  private def totalPanels: Int = panels.length

  private val currentPanelSignal: Signal[VisualNovelPanel] = currentIndex.signal.map(panels)

  private def navigateBy(offset: Int): Unit = {
    val oldIndex = currentIndex.now()
    val newIndex = (oldIndex + offset).max(0).min(totalPanels - 1)
    if (newIndex != oldIndex) {
      currentIndex.update(_ => newIndex)

      val currentHistory = interactionVariable.currentValue
      val alreadySeen = currentHistory.exists(_.panelIndex == newIndex)
      if (!alreadySeen) {
        val updatedViewHistory = currentHistory + VisualNovelPanelView(
          panelIndex = newIndex,
          viewedAtEpochMillis = nowMillis
        )
        interactionVariable.updateStateFromUserInteraction(updatedViewHistory, nowMillis, UpdateImportance.MAJOR)
      }
    }
  }

  private val navigationElement: Element = div(
    cls := "visual-novel-navigation",
    styleAttr := "position: sticky; top: 0; z-index: 10; background: var(--background-color, #fff);",
    button(
      "←",
      disabled <-- currentIndex.signal.map(_ == 0),
      onClick.mapTo(-1) --> navigateBy
    ),
    span(
      cls := "visual-novel-counter",
      child.text <-- currentIndex.signal.map(i => s"${i + 1}/$totalPanels")
    ),
    button(
      "→",
      disabled <-- currentIndex.signal.map(_ >= totalPanels - 1),
      onClick.mapTo(1) --> navigateBy
    )
  )

  private val domElement: Element = div(
    cls := "container-exercise style-vbox",
    htmlTitleElement.getDomElement(),
    navigationElement,
    child <-- currentPanelSignal.map(_.panelContent.getDomElement())
  )

  override def getDomElement(): Element = domElement
}
