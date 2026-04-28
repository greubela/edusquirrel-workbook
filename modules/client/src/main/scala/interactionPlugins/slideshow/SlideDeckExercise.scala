package interactionPlugins.slideshow

import com.raquo.laminar.api.L.*
import it.evadid.core.util.io.Serializer
import upickle.default.*
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.FullInfo
import workbook.model.interaction.InteractionVariable
import workbook.model.interaction.history.UpdateImportance
case class SlideView(slideIndex: Int, viewedAtEpochMillis: Long)

case class SlideDeckExercise(
                              fullInfo: FullInfo,
                              id: String,
                              slides: List[SlidePanel]
                            ) extends WorkbookInteraction[Set[SlideView]] {

  override val defaultValue: Set[SlideView] = Set.empty
  
  
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

  private val currentSlideSignal: Signal[SlidePanel] = currentIndex.signal.map(slides)

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
        interactionVariable.updateStateFromUserInteraction(updatedViewHistory, nowMillis, UpdateImportance.MAJOR)
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
