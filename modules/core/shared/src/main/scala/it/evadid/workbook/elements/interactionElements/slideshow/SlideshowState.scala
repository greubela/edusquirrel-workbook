package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.elements.interactionElements.slideshow.SlideshowState.*
import it.evadid.workbook.jsonFactory.WorkbookElementReference
import upickle.default.*

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import java.time.LocalDateTime

/**
 * Stores the interaction history for a slideshow without owning any UI rendering details.
 * Transition recording lives here so renderers only decide when navigation happened while the model decides how that navigation is represented.
 */
case class SlideshowState(
                           allPanels: List[WorkbookElementReference],
                           events: Set[SlideshowProceededEvent]
                         ) {

  lazy val seenPanels: Set[WorkbookElementReference] = events.flatMap(state => List(state.newPanel, state.oldPanel)).toSet

  lazy val unseenPanels: Set[WorkbookElementReference] = allPanels.toSet -- seenPanels

  /**
   * Records that the user moved from one panel index to another and returns the updated slideshow state.
   * Both indices are resolved against `allPanels`, keeping event creation tied to the model's panel list rather than to a specific renderer.
   */
  def recordTransitionByIndex(oldPanelIndex: Int, newPanelIndex: Int, proceededAt: LocalDateTime = LocalDateTime.now()): SlideshowState = {
    val event = SlideshowProceededEvent(allPanels(oldPanelIndex), allPanels(newPanelIndex), proceededAt)
    copy(events = events + event)
  }

  def timestampsWhereUserSwitchedFromOrToPanel(panel: WorkbookDisplayElement): Set[LocalDateTime] =
    events.collect {
      case event if event.oldPanel.referencedId == panel.elementId || event.newPanel.referencedId == panel.elementId =>
        event.proceededAt
    }

  def serializer(): Serializer[SlideshowState] = Serializer.fromImplicitRW

}

object SlideshowState {
  case class SlideshowProceededEvent(oldPanel: WorkbookElementReference, newPanel: WorkbookElementReference, proceededAt: LocalDateTime) derives ReadWriter

  given ReadWriter[SlideshowState] = macroRW
}
