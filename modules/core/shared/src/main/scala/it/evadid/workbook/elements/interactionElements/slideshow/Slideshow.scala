package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.abstractions.{WorkbookElement, WorkbookInteractionElement}

case class Slideshow(override val id: String, panels: List[SlideshowPanel]) extends WorkbookInteractionElement[SlideshowState] {

  override lazy val childrenOfThisElement: List[WorkbookElement] = panels

  override val defaultValue = SlideshowState(panels, Set.empty)

  override val serializer: Serializer[SlideshowState] = defaultValue.serializer()

}

/*

  def imageSlideTwoColumns(image: FileDescription,
                          sourceMapId: String,
                          leftLabel: String,
                          rightLabel: String,
                          leftBody: String,
                          rightBody: String,
                          fullInfo: FullInfo): SlidePanel = {

    val imageElement = HtmlImageElement(image, fullInfo)

    val panelElement = new HtmlAppElement {
      override def getDomElement(): Element = div(
        cls := "slide-deck-container",
        div(
          cls := "slide-deck-image",
          child <-- imageElement.getDomSignal
        ),
        if (sourceMapId.nonEmpty) {
          div(
            cls := "slide-deck-source",
            child.text <-- fullInfo.signals.stringFromLanguageMapId(sourceMapId)
          )
        } else emptyNode,
        div(
          cls := "slide-deck-text two-columns",
          div(
            cls := "slide-deck-column",
            div(
              cls := "slide-deck-column-title",
              HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, leftLabel).getDomElement()
            ),
            div(
              cls := "slide-deck-column-body",
              HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, leftBody).getDomElement()
            )
          ),
          div(
            cls := "slide-deck-column",
            div(
              cls := "slide-deck-column-title",
              HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, rightLabel).getDomElement()
            ),
            div(
              cls := "slide-deck-column-body",
              HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, rightBody).getDomElement()
            )
          )
        )
      )
    }
 */


/*
Yanneck


package interactionPlugins.slideshow

import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import datastructures.web.file.FileDescription
import workbook.htmlElements.basic.HtmlInstructionElement
import workbook.htmlElements.basic.HtmlImageElement
import workbook.model.info.FullInfo

case class SlidePanel(panelContent: HtmlAppElement)

object SlidePanel {

  def imageSlide(image: FileDescription, textMapId: String, descriptionMapId: String, fullInfo: FullInfo): SlidePanel =
    imageSlide(image, textMapId, "", descriptionMapId, fullInfo)

  def imageSlide(image: FileDescription, textMapId: String, sourceMapId: String, descriptionMapId: String, fullInfo: FullInfo): SlidePanel = {

    val imageElement = HtmlImageElement(image, fullInfo)

    val panelElement = new HtmlAppElement {
      override def getDomElement(): Element = div(
        cls := "slide-deck-container",
        div(
          cls := "slide-deck-image",
          child <-- imageElement.getDomSignal
        ),
        if (sourceMapId.nonEmpty) {
          div(
            cls := "slide-deck-source",
            child.text <-- fullInfo.signals.stringFromLanguageMapId(sourceMapId)
          )
        } else emptyNode,
        div(
          cls := "slide-deck-text",
          div(
            cls := "slide-deck-text-title",
            HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, descriptionMapId).getDomElement()
          ),
          div(
            cls := "slide-deck-text-body",
            HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, textMapId).getDomElement()
          )
        )
      )
    }

    SlidePanel(panelElement)
  }

  def imageSlideTwoColumns(image: FileDescription,
                          leftLabel: String,
                          rightLabel: String,
                          leftBody: String,
                          rightBody: String,
                          fullInfo: FullInfo): SlidePanel =
    imageSlideTwoColumns(image, "", leftLabel, rightLabel, leftBody, rightBody, fullInfo)

  def imageSlideTwoColumns(image: FileDescription,
                          sourceMapId: String,
                          leftLabel: String,
                          rightLabel: String,
                          leftBody: String,
                          rightBody: String,
                          fullInfo: FullInfo): SlidePanel = {

    val imageElement = HtmlImageElement(image, fullInfo)

    val panelElement = new HtmlAppElement {
      override def getDomElement(): Element = div(
        cls := "slide-deck-container",
        div(
          cls := "slide-deck-image",
          child <-- imageElement.getDomSignal
        ),
        if (sourceMapId.nonEmpty) {
          div(
            cls := "slide-deck-source",
            child.text <-- fullInfo.signals.stringFromLanguageMapId(sourceMapId)
          )
        } else emptyNode,
        div(
          cls := "slide-deck-text two-columns",
          div(
            cls := "slide-deck-column",
            div(
              cls := "slide-deck-column-title",
              HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, leftLabel).getDomElement()
            ),
            div(
              cls := "slide-deck-column-body",
              HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, leftBody).getDomElement()
            )
          ),
          div(
            cls := "slide-deck-column",
            div(
              cls := "slide-deck-column-title",
              HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, rightLabel).getDomElement()
            ),
            div(
              cls := "slide-deck-column-body",
              HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, rightBody).getDomElement()
            )
          )
        )
      )
    }

    SlidePanel(panelElement)
  }

  def apply(htmlContent: HtmlAppElement): SlidePanel = new SlidePanel(htmlContent)
}

 */


/* Andre
import com.raquo.laminar.api.L.*
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.model.interaction.variable.InteractionVariable
import upickle.default.*
import workbookHomepage.model.info.FullInfo

case class SlideView(slideIndex: Int, viewedAtEpochMillis: Long)

case class SlideDeckExercise(
                              fullInfo: FullInfo,
                              id: String,
                              slides: List[HtmlSlidePanel]
                            ) extends WorkbookInteraction[SlideShowState] {

  override val defaultValue: SlideShowState = Set.empty

  require(slides.nonEmpty, "SlideDeckExercise requires at least one slide")

  private given ReadWriter[SlideView] = macroRW

  private val slideViewSerializer = new Serializer[Set[SlideView]] {
    override def serialize(obj: Set[SlideView]): String = write(obj.toList.sortBy(_.slideIndex))

    override def deserialize(str: String): Set[SlideView] =
      if (str.isBlank) Set.empty else read[List[SlideView]](str).toSet
  }

  private val currentIndex = Var(0)

  private def nowMillis: Long = System.currentTimeMillis()

  override val interactionVariable: InteractionVariable[Set[SlideView]] =
    InteractionVariable(
      this,
      slideViewSerializer
    )


  private def totalSlides: Int = slides.length

  private val currentSlideSignal: Signal[HtmlSlidePanel] = currentIndex.signal.map(slides)

  private def navigateBy(offset: Int): Unit = {
    val oldIndex = currentIndex.now()
    val newIndex = (oldIndex + offset).max(0).min(totalSlides - 1)
    if (newIndex != oldIndex) {
      currentIndex.update(_ => newIndex)

      val currentHistory = interactionVariable.currentValue
      val alreadySeen = currentHistory.exists(_.slideIndex == newIndex)
      if (!alreadySeen) {
        val updatedViewHistory = currentHistory + SlideView(
          slideIndex = newIndex,
          viewedAtEpochMillis = nowMillis
        )
        interactionVariable.setStateFromUserInteraction(updatedViewHistory, UpdateImportance.MAJOR)
      }
    }
  }

  private val navigationElement: Element = div(
    cls := "slide-deck-navigation",
    button(
      child.text <-- fullInfo.signals.stringFromLanguageMapId("PlantWorkshop/slideshowBack"),
      disabled <-- currentIndex.signal.map(_ == 0),
      onClick.mapTo(-1) --> navigateBy
    ),
    span(
      cls := "slide-deck-counter",
      child.text <-- currentIndex.signal.map(i => s"${i + 1}/$totalSlides")
    ),
    button(
      child.text <-- fullInfo.signals.stringFromLanguageMapId("PlantWorkshop/slideshowNext"),
      disabled <-- currentIndex.signal.map(_ >= totalSlides - 1),
      onClick.mapTo(1) --> navigateBy
    )
  )

  override def getDomElement(): Element = div(
    cls := "workbook-interaction",
    navigationElement,
    child <-- currentSlideSignal.map(_.panelContent.getDomElement())
  )
}
*/