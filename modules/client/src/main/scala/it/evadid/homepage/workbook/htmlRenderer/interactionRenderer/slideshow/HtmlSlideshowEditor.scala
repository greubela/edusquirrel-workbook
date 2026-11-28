package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.slideshow

import it.evadid.workbook.control.InteractionSignals.*

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.{child, *}
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.*
import it.evadid.workbook.interactions.slideshow.*
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.workbook.sync.UpdateImportance.MAJOR

/** Navigation records visits; completion is evaluated by the shared slideshow grader. */
object HtmlSlideshowEditor extends LineBasedRenderingFactory[Slideshow] {

  /**
   * Creates the slideshow DOM with localized navigation buttons and a reactive current-panel view.
   * When navigation succeeds, the method updates the bound interaction state through the model-level transition recorder instead of constructing event data directly.
   */
  override protected def createRendering(fullInfo: FullInfo, workbookElement: Slideshow): AtomarLineRendering = {
    val stateVar = fullInfo.interactions.boundVar(workbookElement, MAJOR)
    val totalSlides = workbookElement.panelSize

    def safeIndex(state: SlideshowState): Int = {
      state.events.toList.sortBy(_.proceededAt).lastOption
        .flatMap(event => workbookElement.indexOf(event.newPanel)).filter(_ >= 0).getOrElse(0)
    }

    val currentIndex: Signal[Int] = stateVar.signal.map(safeIndex).distinct

    def navigateBy(offset: Int): Unit = {
      if (totalSlides > 0) {
        val oldIndex = safeIndex(stateVar.now())
        val newIndex = (oldIndex + offset).max(0).min(totalSlides - 1)
        if (newIndex != oldIndex) {
          stateVar.update(_.recordTransitionByIndex(oldIndex, newIndex))
        }
      }
    }

    def navigation(): Element =
      div(
        cls := "slide-deck-navigation",
        button(
          child.text <-- laminarHelper(fullInfo).plaintextStringSignal("PlantWorkshop/slideshowBack"),
          disabled <-- currentIndex.map(_ == 0),
          onClick.mapTo(-1) --> navigateBy
        ),
        span(
          cls := "slide-deck-counter",
          child.text <-- currentIndex.map(i => s"${i + 1}/$totalSlides")
        ),
        button(
          child.text <-- laminarHelper(fullInfo).plaintextStringSignal("PlantWorkshop/slideshowNext"),
          disabled <-- currentIndex.map(_ >= totalSlides - 1),
          onClick.mapTo(1) --> navigateBy
        )
      )

    val dom = div(
      cls := "slide-deck-container",
      onMountCallback { _ =>
        if totalSlides > 0 && stateVar.now().events.isEmpty then
          stateVar.update(_.recordTransitionByIndex(0, 0))
      },
      div(
        cls := "slide-deck-content slide-deck-image" ,
        child <-- currentIndex.map(curIndex => createSlideshowPanelDom(fullInfo, workbookElement, curIndex))
      ),
      navigation()
    )

    AtomarLineRendering.basicLine(fullInfo, workbookElement, dom, "slide-deck")

  }


  /**
   * Converts a core slideshow panel into the corresponding slide-deck DOM subtree.
   * Navigation is placed directly under the slide image. Unsupported panel implementations
   * receive an explicit fallback element so newly added panel types fail visibly instead of silently disappearing.
   */
  def createSlideshowPanelDom(fullInfo: FullInfo, workbookElement: Slideshow, index: Int): Element = {

    val panelOp: Option[AtomarLineRendering] = workbookElement.getPanel(index).map(HtmlRenderFactory.renderWorkbookElement(fullInfo, _)).map(_.rendering)
    val place = placeholder(fullInfo, workbookElement, "no content for slideshow available!")

    div(
      cls := "slide-deck-container",
      children <-- panelOp.getOrElse(place).elementsWithoutContainer.allElementsSignal
    )

  }

}
